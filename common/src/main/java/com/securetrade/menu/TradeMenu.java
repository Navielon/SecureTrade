package com.securetrade.menu;

import com.securetrade.TradeItemValidator;
import com.securetrade.SecureTradeSounds;
import com.securetrade.TradeMessages;
import com.securetrade.platform.Services;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.ITextComponent;


public class TradeMenu extends Container {
    public final TradeSession session;
    public final boolean isPlayer1;
    public boolean myLock = false;
    public boolean otherLock = false;
    public int countdownSeconds = -1;
    public long myXP = 0;
    public long otherXP = 0;
    public long otherTotalXP = 0;
    public String partnerName = "";
    private long blacklistWarningUntilMillis = 0L;

    private final IInventory myContainer;
    private final IInventory otherContainer;
    private long lastBlacklistNotificationMillis = 0L;
    private long lastLocalItemAddSoundMillis = 0L;
    private long lastLocalBlacklistSoundMillis = 0L;

    public static final int TRADE_SLOTS_COUNT = 27; // 9x3

    public static final int MY_SLOTS_START = 0;
    public static final int OTHER_SLOTS_START = MY_SLOTS_START + TRADE_SLOTS_COUNT; // 27
    public static final int INV_SLOTS_START = OTHER_SLOTS_START + TRADE_SLOTS_COUNT; // 54
    public static final int HOTBAR_SLOTS_START = INV_SLOTS_START + 27; // 81

    public TradeMenu(int containerId, PlayerInventory playerInventory) {
        super(TradeMenuType.get(), containerId);
        this.session = null;
        this.isPlayer1 = true;
        this.myContainer = new Inventory(TRADE_SLOTS_COUNT);
        this.otherContainer = new Inventory(TRADE_SLOTS_COUNT);
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
                    public boolean mayPlace(ItemStack stack) {
                        return !stack.isEmpty() && !isBlacklisted(stack);
                    }
                    @Override
                    public void setChanged() {
                        super.setChanged();
                        if (session != null) session.onItemsChanged();
                    }
                });
            }
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(otherContainer, col + row * 9, 188 + col * 18, 17 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                    @Override
                    public boolean mayPickup(PlayerEntity playerIn) {
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
    public ItemStack clicked(int slotId, int button, ClickType clickType, PlayerEntity player) {
        if (slotId >= MY_SLOTS_START && slotId < OTHER_SLOTS_START) {
            ItemStack attemptedStack = getAttemptedPlacementStack(button, clickType, player);
            if (!attemptedStack.isEmpty() && isBlacklisted(attemptedStack)) {
                notifyBlacklisted(player);
                return ItemStack.EMPTY;
            }
            if (shouldPlayLocalItemAddSound(slotId, attemptedStack, clickType, player)) {
                playLocalItemAddSound(player);
            }
            return super.clicked(slotId, button, clickType, player);
        }
        return super.clicked(slotId, button, clickType, player);
    }

    private ItemStack getAttemptedPlacementStack(int button, ClickType clickType, PlayerEntity player) {
        if (clickType == ClickType.SWAP && button >= 0 && button < player.inventory.getContainerSize()) {
            return player.inventory.getItem(button);
        }
        return player.inventory.getCarried();
    }

    private void notifyBlacklisted(PlayerEntity player) {
        if (player.level.isClientSide()) {
            showBlacklistWarning();
            playLocalBlacklistSound(player);
            return;
        }

        if (!(player instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

        long now = System.currentTimeMillis();
        if (now - lastBlacklistNotificationMillis < 250L) {
            return;
        }
        lastBlacklistNotificationMillis = now;
        serverPlayer.playNotifySound(SecureTradeSounds.TRADE_ITEM_BLOCKED, SoundCategory.MASTER, 0.9f, 1.0f);
        Services.PLATFORM.sendBlacklistWarning(serverPlayer);
    }

    @Override
    public ItemStack quickMoveStack(PlayerEntity player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (index >= OTHER_SLOTS_START && index < INV_SLOTS_START) {
            return ItemStack.EMPTY;
        }

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            
            if (index < OTHER_SLOTS_START) {
                if (!this.moveItemStackTo(itemstack1, INV_SLOTS_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } 
            else {
                if (isBlacklisted(itemstack1)) {
                    notifyBlacklisted(player);
                    return ItemStack.EMPTY;
                }
                if (!this.moveItemStackTo(itemstack1, MY_SLOTS_START, OTHER_SLOTS_START, false)) {
                    return ItemStack.EMPTY;
                }
                playLocalItemAddSound(player);
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
    public boolean stillValid(PlayerEntity player) {
        return true;
    }


    @Override
    public void removed(PlayerEntity player) {
        super.removed(player);
        if (session != null && !player.level.isClientSide()) {
            session.cancelTrade();
        }
    }

    public void setLocked(PlayerEntity player, boolean locked) {
        if (session != null && player instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            session.setLocked(serverPlayer, locked);
        }
    }

    public void setOfferedXP(PlayerEntity player, long xp) {
        if (session != null && player instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            session.setOfferedXP(serverPlayer, xp);
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

    private boolean shouldPlayLocalItemAddSound(int slotId, ItemStack attemptedStack, ClickType clickType, PlayerEntity player) {
        if (!player.level.isClientSide() || attemptedStack.isEmpty()) {
            return false;
        }
        if (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_CRAFT && clickType != ClickType.SWAP) {
            return false;
        }
        Slot slot = this.slots.get(slotId);
        if (!slot.mayPlace(attemptedStack)) {
            return false;
        }

        ItemStack current = slot.getItem();
        return current.isEmpty()
                || !ItemStack.isSame(current, attemptedStack)
                || !ItemStack.tagMatches(current, attemptedStack)
                || current.getCount() < Math.min(current.getMaxStackSize(), slot.getMaxStackSize());
    }

    private void playLocalItemAddSound(PlayerEntity player) {
        if (!player.level.isClientSide()) {
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
        
        player1.openMenu(new INamedContainerProvider() {
            @Override
            public ITextComponent getDisplayName() {
                return TradeMessages.trans("securetrade.gui.trade_with", player2.getScoreboardName());
            }

            @Override
            public Container createMenu(int id, PlayerInventory inv, PlayerEntity player) {
                return new TradeMenu(id, inv, session, true);
            }
        });
        
        player2.openMenu(new INamedContainerProvider() {
            @Override
            public ITextComponent getDisplayName() {
                return TradeMessages.trans("securetrade.gui.trade_with", player1.getScoreboardName());
            }

            @Override
            public Container createMenu(int id, PlayerInventory inv, PlayerEntity player) {
                return new TradeMenu(id, inv, session, false);
            }
        });

        session.syncState();
    }
}




