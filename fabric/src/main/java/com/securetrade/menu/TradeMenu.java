package com.securetrade.menu;

import com.securetrade.TradeItemValidator;
import com.securetrade.TradeMessages;
import com.securetrade.platform.Services;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;


public class TradeMenu extends ScreenHandler {
    public final TradeSession session;
    public final boolean isPlayer1;
    public boolean myLock = false;
    public boolean otherLock = false;
    public int countdownSeconds = -1;
    public int myXP = 0;
    public int otherXP = 0;
    
    private final Inventory myContainer;
    private final Inventory otherContainer;

    // Client-side constructor
    public TradeMenu(int containerId, PlayerInventory playerInventory) {
        super(TradeMenuType.get(), containerId);
        this.session = null;
        this.isPlayer1 = true;
        this.myContainer = new SimpleInventory(12);
        this.otherContainer = new SimpleInventory(12);
        setupSlots(playerInventory);
    }

    // Server-side constructor
    public TradeMenu(int containerId, PlayerInventory playerInventory, TradeSession session, boolean isPlayer1) {
        super(TradeMenuType.get(), containerId);
        this.session = session;
        this.isPlayer1 = isPlayer1;
        this.myContainer = isPlayer1 ? session.inventory1 : session.inventory2;
        this.otherContainer = isPlayer1 ? session.inventory2 : session.inventory1;
        setupSlots(playerInventory);
    }

    private void setupSlots(PlayerInventory playerInventory) {
        // My slots (left side, 3x4)
        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new Slot(myContainer, col + row * 3, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return !stack.isEmpty() && !isBlacklisted(stack);
                    }
                    @Override
                    public void markDirty() {
                        super.markDirty();
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
                    public boolean canInsert(ItemStack stack) {
                        return false; // Cannot place items in other player's slots
                    }
                    @Override
                    public boolean canTakeItems(PlayerEntity playerIn) {
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
    public ItemStack transferSlot(PlayerEntity player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        // FIX #13: Explicitly block Shift-click from partner's slots (12-23)
        if (index >= 12 && index < 24) {
            return ItemStack.EMPTY;
        }

        if (slot != null && slot.hasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();
            
            // From My Trade slots (0-11) to Player Inventory
            if (index < 12) {
                if (!this.insertItem(itemstack1, 24, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } 
            // From Player Inventory to My Trade slots (0-11)
            else {
                if (isBlacklisted(itemstack1)) {
                    return ItemStack.EMPTY;
                }
                if (!this.insertItem(itemstack1, 0, 12, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return itemstack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }


    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        if (session != null && !player.world.isClient) {
            session.cancelTrade();
        }
    }

    public void setLocked(PlayerEntity player, boolean locked) {
        if (session != null && player instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            session.setLocked(serverPlayer, locked);
        }
    }

    public void setOfferedXP(PlayerEntity player, int xp) {
        if (session != null && player instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
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
            ServerPlayerEntity myPlayer = isPlayer1 ? session.player1 : session.player2;
            Services.PLATFORM.sendStateSync(myPlayer, myLock, otherLock, countdownSeconds, myXP, otherXP);
        }
    }

    public static void openTrade(ServerPlayerEntity player1, ServerPlayerEntity player2) {
        TradeSession session = new TradeSession(player1, player2);
        
        player1.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return TradeMessages.text("Trade with " + player2.getEntityName());
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity player) {
                return new TradeMenu(id, inv, session, true);
            }
        });
        
        player2.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return TradeMessages.text("Trade with " + player1.getEntityName());
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity player) {
                return new TradeMenu(id, inv, session, false);
            }
        });
    }
}
