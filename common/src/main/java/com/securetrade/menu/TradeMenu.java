package com.securetrade.menu;

import com.securetrade.TradeItemValidator;
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
    public int myXP = 0;
    public int otherXP = 0;
    
    private final Container myContainer;
    private final Container otherContainer;

    // Client-side constructor
    public TradeMenu(int containerId, Inventory playerInventory) {
        super(TradeMenuType.get(), containerId);
        this.session = null;
        this.isPlayer1 = true;
        this.myContainer = new SimpleContainer(12);
        this.otherContainer = new SimpleContainer(12);
        setupSlots(playerInventory);
    }

    // Server-side constructor
    public TradeMenu(int containerId, Inventory playerInventory, TradeSession session, boolean isPlayer1) {
        super(TradeMenuType.get(), containerId);
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
                    public boolean mayPlace(ItemStack stack) {
                        return !stack.isEmpty() && !isBlacklisted(stack);
                    }
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

    private boolean isBlacklisted(ItemStack stack) {
        return TradeItemValidator.containsBlacklistedItem(stack);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        // FIX #13: Explicitly block Shift-click from partner's slots (12-23)
        if (index >= 12 && index < 24) {
            return ItemStack.EMPTY;
        }

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            
            // From My Trade slots (0-11) to Player Inventory
            if (index < 12) {
                if (!this.moveItemStackTo(itemstack1, 24, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } 
            // From Player Inventory to My Trade slots (0-11)
            else {
                if (isBlacklisted(itemstack1)) {
                    return ItemStack.EMPTY;
                }
                if (!this.moveItemStackTo(itemstack1, 0, 12, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
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

    public void setOfferedXP(Player player, int xp) {
        if (session != null && player instanceof ServerPlayer serverPlayer) {
            session.setOfferedXP(serverPlayer, xp);
        }
    }

    // FIX #5: Client-only method to update local fields from server sync packet
    public void updateFields(boolean myLock, boolean otherLock, int countdownSeconds, int myXP, int otherXP) {
        this.myLock = myLock;
        this.otherLock = otherLock;
        this.countdownSeconds = countdownSeconds;
        this.myXP = myXP;
        this.otherXP = otherXP;
    }

    // FIX #5: Server-only method to sync state to client
    public void syncToClient(boolean myLock, boolean otherLock, int countdownSeconds, int myXP, int otherXP) {
        this.myLock = myLock;
        this.otherLock = otherLock;
        this.countdownSeconds = countdownSeconds;
        this.myXP = myXP;
        this.otherXP = otherXP;
        if (session != null) {
            ServerPlayer myPlayer = isPlayer1 ? session.player1 : session.player2;
            Services.PLATFORM.sendStateSync(myPlayer, myLock, otherLock, countdownSeconds, myXP, otherXP);
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
