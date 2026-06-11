package com.securetrade.menu;

import com.securetrade.SecureTradeSounds;
import com.securetrade.TradeItemValidator;
import com.securetrade.TradeMessages;
import com.securetrade.platform.Services;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;

public class TradeMenu extends ScreenHandler {
    public final TradeSession session;
    public final boolean isPlayer1;
    public boolean myLock = false;
    public boolean otherLock = false;
    public int countdownSeconds = -1;
    public long myXP = 0;
    public long otherXP = 0;
    public long otherTotalXP = 0;
    public String partnerName = "";

    private final Inventory myContainer;
    private final Inventory otherContainer;
    private long blacklistWarningUntilMillis = 0L;
    private long lastBlacklistNotificationMillis = 0L;
    private long lastLocalItemAddSoundMillis = 0L;
    private long lastLocalBlacklistSoundMillis = 0L;

    public static final int TRADE_SLOTS_COUNT = 27;
    public static final int MY_SLOTS_START = 0;
    public static final int OTHER_SLOTS_START = MY_SLOTS_START + TRADE_SLOTS_COUNT;
    public static final int INV_SLOTS_START = OTHER_SLOTS_START + TRADE_SLOTS_COUNT;
    public static final int HOTBAR_SLOTS_START = INV_SLOTS_START + 27;

    public TradeMenu(int containerId, PlayerInventory playerInventory) {
        super(TradeMenuType.get(), containerId);
        this.session = null;
        this.isPlayer1 = true;
        this.myContainer = new SimpleInventory(TRADE_SLOTS_COUNT);
        this.otherContainer = new SimpleInventory(TRADE_SLOTS_COUNT);
        setupSlots(playerInventory);
    }

    public TradeMenu(int containerId, PlayerInventory playerInventory, TradeSession session, boolean isPlayer1) {
        super(TradeMenuType.get(), containerId);
        this.session = session;
        this.isPlayer1 = isPlayer1;
        this.myContainer = isPlayer1 ? session.inventory1 : session.inventory2;
        this.otherContainer = isPlayer1 ? session.inventory2 : session.inventory1;
        setupSlots(playerInventory);
    }

    private void setupSlots(PlayerInventory playerInventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(myContainer, col + row * 9, 8 + col * 18, 17 + row * 18) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return !stack.isEmpty() && !isBlacklisted(stack);
                    }

                    @Override
                    public void markDirty() {
                        super.markDirty();
                        if (session != null) session.onItemsChanged();
                    }
                });
            }
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(otherContainer, col + row * 9, 188 + col * 18, 17 + row * 18) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean canTakeItems(PlayerEntity playerIn) {
                        return false;
                    }
                });
            }
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 98 + col * 18, 126 + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 98 + col * 18, 184));
        }
    }

    private boolean isBlacklisted(ItemStack stack) {
        return TradeItemValidator.containsBlacklistedItem(stack);
    }

    @Override
    public ItemStack onSlotClick(int slotId, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotId >= MY_SLOTS_START && slotId < OTHER_SLOTS_START) {
            ItemStack attemptedStack = getAttemptedPlacementStack(button, actionType, player);
            if (!attemptedStack.isEmpty() && isBlacklisted(attemptedStack)) {
                notifyBlacklisted(player);
                return ItemStack.EMPTY;
            }
            if (shouldPlayLocalItemAddSound(slotId, attemptedStack, actionType, player)) {
                playLocalItemAddSound(player);
            }
            return super.onSlotClick(slotId, button, actionType, player);
        }
        return super.onSlotClick(slotId, button, actionType, player);
    }

    private ItemStack getAttemptedPlacementStack(int button, SlotActionType actionType, PlayerEntity player) {
        if (actionType == SlotActionType.SWAP && button >= 0 && button < player.inventory.size()) {
            return player.inventory.getStack(button);
        }
        return player.inventory.getCursorStack().copy();
    }

    private void notifyBlacklisted(PlayerEntity player) {
        if (player.world.isClient) {
            showBlacklistWarning();
            playLocalBlacklistSound(player);
            return;
        }

        if (!(player instanceof ServerPlayerEntity)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBlacklistNotificationMillis < 250L) {
            return;
        }
        lastBlacklistNotificationMillis = now;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        serverPlayer.playSound(SecureTradeSounds.TRADE_ITEM_BLOCKED, SoundCategory.MASTER, 0.9f, 1.0f);
        Services.PLATFORM.sendBlacklistWarning(serverPlayer);
    }

    @Override
    public ItemStack transferSlot(PlayerEntity player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (index >= OTHER_SLOTS_START && index < INV_SLOTS_START) {
            return ItemStack.EMPTY;
        }

        if (slot != null && slot.hasStack()) {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();

            if (index < OTHER_SLOTS_START) {
                if (!this.insertItem(itemstack1, INV_SLOTS_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (isBlacklisted(itemstack1)) {
                    notifyBlacklisted(player);
                    return ItemStack.EMPTY;
                }
                if (!this.insertItem(itemstack1, MY_SLOTS_START, OTHER_SLOTS_START, false)) {
                    return ItemStack.EMPTY;
                }
                playLocalItemAddSound(player);
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
            session.setLocked((ServerPlayerEntity) player, locked);
        }
    }

    public void setOfferedXP(PlayerEntity player, long xp) {
        if (session != null && player instanceof ServerPlayerEntity) {
            session.setOfferedXP((ServerPlayerEntity) player, xp);
        }
    }

    public void updateFields(boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName) {
        this.myLock = myLock;
        this.otherLock = otherLock;
        this.countdownSeconds = countdownSeconds;
        this.myXP = myXP;
        this.otherXP = otherXP;
        this.otherTotalXP = otherTotalXP;
        this.partnerName = partnerName;
    }

    public void syncToClient(boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName) {
        this.myLock = myLock;
        this.otherLock = otherLock;
        this.countdownSeconds = countdownSeconds;
        this.myXP = myXP;
        this.otherXP = otherXP;
        this.otherTotalXP = otherTotalXP;
        this.partnerName = partnerName;
        if (session != null) {
            ServerPlayerEntity myPlayer = isPlayer1 ? session.player1 : session.player2;
            Services.PLATFORM.sendStateSync(myPlayer, myLock, otherLock, countdownSeconds, myXP, otherXP, otherTotalXP, partnerName);
        }
    }

    public void showBlacklistWarning() {
        this.blacklistWarningUntilMillis = System.currentTimeMillis() + 2200L;
    }

    private boolean shouldPlayLocalItemAddSound(int slotId, ItemStack attemptedStack, SlotActionType actionType, PlayerEntity player) {
        if (!player.world.isClient || attemptedStack.isEmpty()) {
            return false;
        }
        if (actionType != SlotActionType.PICKUP && actionType != SlotActionType.QUICK_CRAFT && actionType != SlotActionType.SWAP) {
            return false;
        }
        Slot slot = this.slots.get(slotId);
        if (!slot.canInsert(attemptedStack)) {
            return false;
        }

        ItemStack current = slot.getStack();
        return current.isEmpty()
                || !ItemStack.areItemsEqual(current, attemptedStack)
                || !ItemStack.areTagsEqual(current, attemptedStack)
                || current.getCount() < Math.min(current.getMaxCount(), slot.getMaxItemCount());
    }

    private void playLocalItemAddSound(PlayerEntity player) {
        if (!player.world.isClient) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLocalItemAddSoundMillis < 45L) {
            return;
        }
        lastLocalItemAddSoundMillis = now;
        player.playSound(SecureTradeSounds.TRADE_ITEM_ADD, 0.45f, 1.0f);
    }

    private void playLocalBlacklistSound(PlayerEntity player) {
        long now = System.currentTimeMillis();
        if (now - lastLocalBlacklistSoundMillis < 120L) {
            return;
        }
        lastLocalBlacklistSoundMillis = now;
        player.playSound(SecureTradeSounds.TRADE_ITEM_BLOCKED, 0.85f, 1.0f);
    }

    public long getBlacklistWarningRemainingMillis() {
        return Math.max(0L, this.blacklistWarningUntilMillis - System.currentTimeMillis());
    }

    public static void openTrade(ServerPlayerEntity player1, ServerPlayerEntity player2) {
        TradeSession session = new TradeSession(player1, player2);

        player1.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return TradeMessages.trans("securetrade.gui.trade_with", player2.getEntityName());
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity player) {
                return new TradeMenu(id, inv, session, true);
            }
        });

        player2.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return TradeMessages.trans("securetrade.gui.trade_with", player1.getEntityName());
            }

            @Override
            public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity player) {
                return new TradeMenu(id, inv, session, false);
            }
        });

        session.syncState();
    }
}
