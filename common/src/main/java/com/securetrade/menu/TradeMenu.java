package com.securetrade.menu;

import com.securetrade.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;


public class TradeMenu extends AbstractContainerMenu {
    public final TradeSession session;
    public final boolean isPlayer1;
    public boolean myLock = false;
    public boolean otherLock = false;
    public int countdownSeconds = -1;
    
    private final Container myContainer;
    private final Container otherContainer;

    // Client-side constructor
    public TradeMenu(int containerId, Inventory playerInventory) {
        super(TradeMenuType.TRADE_MENU, containerId);
        this.session = null;
        this.isPlayer1 = true;
        this.myContainer = new SimpleContainer(12);
        this.otherContainer = new SimpleContainer(12);
        setupSlots(playerInventory);
    }

    // Server-side constructor
    public TradeMenu(int containerId, Inventory playerInventory, TradeSession session, boolean isPlayer1) {
        super(TradeMenuType.TRADE_MENU, containerId);
        this.session = session;
        this.isPlayer1 = isPlayer1;
        this.myContainer = isPlayer1 ? session.inventory1 : session.inventory2;
        this.otherContainer = isPlayer1 ? session.inventory2 : session.inventory1;
        setupSlots(playerInventory);
    }

    private void setupSlots(Inventory playerInventory) {
        // My slots (left side, 3x4)
        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new Slot(myContainer, col + row * 3, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public void setChanged() {
                        super.setChanged();
                        if (session != null) session.onStateChanged();
                    }
                });
            }
        }

        // Other player's slots (right side, 3x4)
        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new Slot(otherContainer, col + row * 3, 116 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false; // Cannot place items in other player's slots
                    }
                    @Override
                    public boolean mayPickup(Player playerIn) {
                        return false; // Cannot pick up other player's items
                    }
                });
            }
        }

        // Player inventory
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 161));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            
            // From Trade slots to Player Inventory
            if (index < 24) {
                if (!this.moveItemStackTo(itemstack1, 24, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } 
            // From Player Inventory to My Trade slots (0-11)
            else if (!this.moveItemStackTo(itemstack1, 0, 12, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (session != null && !player.level().isClientSide()) {
            session.cancelTrade();
        }
    }

    public void setLocked(Player player, boolean locked) {
        if (session != null && player instanceof ServerPlayer serverPlayer) {
            session.setLocked(serverPlayer, locked);
        }
    }

    public void updateClientState(boolean myLock, boolean otherLock, int countdownSeconds) {
        this.myLock = myLock;
        this.otherLock = otherLock;
        this.countdownSeconds = countdownSeconds;
        // Broadcast packet to client if server
        if (session != null) {
            ServerPlayer myPlayer = isPlayer1 ? session.player1 : session.player2;
            Services.PLATFORM.sendStateSync(myPlayer, myLock, otherLock, countdownSeconds);
        }
    }

    public static void openTrade(ServerPlayer player1, ServerPlayer player2) {
        TradeSession session = new TradeSession(player1, player2);
        
        player1.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Trade with " + player2.getScoreboardName());
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
                return new TradeMenu(id, inv, session, true);
            }
        });
        
        player2.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Trade with " + player1.getScoreboardName());
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
                return new TradeMenu(id, inv, session, false);
            }
        });
    }
}
