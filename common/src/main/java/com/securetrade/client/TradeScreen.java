package com.securetrade.client;

import com.securetrade.XPMath;
import com.securetrade.menu.TradeMenu;
import com.securetrade.platform.Services;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

public class TradeScreen extends AbstractContainerScreen<TradeMenu> {
    public static final Identifier TRADE_TEXTURE = Identifier.fromNamespaceAndPath("securetrade", "textures/gui/trade.png");

    private static final int LEFT_READY_X = 30;
    private static final int LEFT_READY_Y = 98;
    private static final int READY_W = 117;
    private static final int READY_H = 20;

    private static final int RIGHT_READY_X = 210;
    private static final int RIGHT_READY_Y = 98;

    private static final int LEFT_MINUS_X = 12;
    private static final int LEFT_MINUS_Y = 82;
    private static final int LEFT_PLUS_X = 151;
    private static final int LEFT_PLUS_Y = 82;
    private static final int BUTTON_SIZE = 13;

    private static final int LEFT_XP_BAR_X = 29;
    private static final int LEFT_XP_BAR_Y = 85;
    private static final int XP_BAR_W = 118;
    private static final int XP_BAR_H = 7;

    private static final int LEFT_XP_FILL_X = 30;
    private static final int RIGHT_XP_FILL_X = 210;
    private static final int XP_FILL_Y = 86;
    private static final int XP_FILL_W = 116;

    private static final int ARROW_X = 162;
    private static final int ARROW_Y = 98;
    private static final float ARROW_ANIMATION_SECONDS = 0.17f;
    private static final float SLOT_HIGHLIGHT_ANIMATION_SECONDS = 0.16f;
    private static final float READY_HOVER_ANIMATION_SECONDS = 0.10f;

    private static final int LEFT_XP_TEXT_X = 71;
    private static final int RIGHT_XP_TEXT_X = 251;
    private static final int XP_TEXT_Y = 73;

    private static final int SPRITE_LEFT_ARROW_U = 356;
    private static final int SPRITE_LEFT_ARROW_V = 0;
    private static final int SPRITE_LEFT_ARROW_W = 31;
    private static final int SPRITE_LEFT_ARROW_H = 22;

    private static final int SPRITE_RIGHT_ARROW_U = 387;
    private static final int SPRITE_RIGHT_ARROW_V = 0;
    private static final int SPRITE_RIGHT_ARROW_W = 31;
    private static final int SPRITE_RIGHT_ARROW_H = 22;

    private static final int SPRITE_PLUS_U = 356;
    private static final int SPRITE_MINUS_U = 369;
    private static final int SPRITE_HOVER_PLUS_U = 382;
    private static final int SPRITE_HOVER_MINUS_U = 395;
    private static final int SPRITE_DISABLED_PLUS_U = 408;
    private static final int SPRITE_DISABLED_MINUS_U = 421;
    private static final int SPRITE_CONTROL_V = 27;

    private static final int SPRITE_GREEN_BAR_U = 356;
    private static final int SPRITE_GREEN_BAR_V = 22;
    private static final int SPRITE_GREEN_BAR_H = 5;

    private static final int SPRITE_KNOB_U = 356;
    private static final int SPRITE_KNOB_V = 100;
    private static final int SPRITE_KNOB_W = 5;
    private static final int SPRITE_KNOB_H = 13;

    private static final int SPRITE_READY_BG_U = 356;
    private static final int SPRITE_READY_NORMAL_V = 40;
    private static final int SPRITE_READY_ACTIVE_V = 60;
    private static final int SPRITE_READY_DISABLED_V = 80;

    private float sliderValue = 0.0f;
    private boolean isDraggingSlider = false;
    private float myArrowProgress = 0.0f;
    private float otherArrowProgress = 0.0f;
    private float mySlotHighlightProgress = 0.0f;
    private float otherSlotHighlightProgress = 0.0f;
    private float readyHoverProgress = 0.0f;
    private long lastArrowFrameNanos = System.nanoTime();

    public TradeScreen(TradeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 356, 205);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateReadyAnimations(mouseX, mouseY);
        extractTradeBackground(graphics);
        super.extractContents(graphics, mouseX, mouseY, partialTick);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        drawSlotHighlights(graphics, x, y);
        drawActiveOverlays(graphics, x, y, mouseX, mouseY);
    }

    private Component lockButtonText() {
        return Component.translatable(
                this.menu.countdownSeconds > 0 || this.menu.myLock
                        ? "securetrade.gui.cancel"
                        : "securetrade.gui.lock"
        );
    }

    private void drawSlotHighlights(GuiGraphicsExtractor graphics, int x, int y) {
        drawSlotHighlightGrid(graphics, x, y, 8, 17, this.mySlotHighlightProgress);
        drawSlotHighlightGrid(graphics, x, y, 188, 17, this.otherSlotHighlightProgress);
    }

    private void drawSlotHighlightGrid(GuiGraphicsExtractor graphics, int x, int y, int slotX, int slotY, float progress) {
        int color = slotHighlightColor(progress);
        if (color == 0) {
            return;
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                graphics.fill(x + slotX + col * 18, y + slotY + row * 18, x + slotX + 16 + col * 18, y + slotY + 16 + row * 18, color);
            }
        }
    }

    private int slotHighlightColor(float progress) {
        int alpha = Math.round(0x35 * progress);
        if (alpha <= 0) {
            return 0;
        }
        return (alpha << 24) | 0x00FF00;
    }

    private static int lerpColor(int from, int to, float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;
        int r = Math.round(fromR + (toR - fromR) * clamped);
        int g = Math.round(fromG + (toG - fromG) * clamped);
        int b = Math.round(fromB + (toB - fromB) * clamped);
        return (r << 16) | (g << 8) | b;
    }

    private boolean isMouseOver(int btnX, int btnY, int btnSize, double mouseX, double mouseY) {
        return isMouseOver(btnX, btnY, btnSize, btnSize, mouseX, mouseY);
    }

    private boolean isMouseOver(int btnX, int btnY, int btnW, int btnH, double mouseX, double mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        return mouseX >= x + btnX && mouseX <= x + btnX + btnW && mouseY >= y + btnY && mouseY <= y + btnY + btnH;
    }

    private void drawActiveOverlays(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        drawReadyButton(graphics, x, y);
        drawArrowFill(graphics, x, y, this.myArrowProgress, true);
        drawArrowFill(graphics, x, y, this.otherArrowProgress, false);
        drawXPControlButtons(graphics, x, y, mouseX, mouseY);

        long maxXP = XPMath.getPlayerXP(this.minecraft.player);
        if (!this.isDraggingSlider) {
            this.sliderValue = maxXP > 0 ? (float) this.menu.myXP / maxXP : 0.0f;
        }

        int leftFilledW = Math.round(XP_FILL_W * this.sliderValue);
        if (leftFilledW > 0) {
            blit(graphics, x + LEFT_XP_FILL_X, y + XP_FILL_Y, SPRITE_GREEN_BAR_U, SPRITE_GREEN_BAR_V, leftFilledW, SPRITE_GREEN_BAR_H);
        }

        int knobX = x + LEFT_XP_FILL_X + leftFilledW - SPRITE_KNOB_W / 2;
        int knobY = y + LEFT_XP_BAR_Y + (XP_BAR_H - SPRITE_KNOB_H) / 2;
        blit(graphics, knobX, knobY, SPRITE_KNOB_U, SPRITE_KNOB_V, SPRITE_KNOB_W, SPRITE_KNOB_H);

        float otherSliderValue = this.menu.otherTotalXP > 0
                ? Math.min(1.0f, (float) this.menu.otherXP / this.menu.otherTotalXP)
                : 0.0f;
        int rightFilledW = Math.round(XP_FILL_W * otherSliderValue);
        if (rightFilledW > 0) {
            blit(graphics, x + RIGHT_XP_FILL_X, y + XP_FILL_Y, SPRITE_GREEN_BAR_U, SPRITE_GREEN_BAR_V, rightFilledW, SPRITE_GREEN_BAR_H);
        }
        int rightKnobX = x + RIGHT_XP_FILL_X + rightFilledW - SPRITE_KNOB_W / 2;
        blit(graphics, rightKnobX, knobY, SPRITE_KNOB_U, SPRITE_KNOB_V, SPRITE_KNOB_W, SPRITE_KNOB_H);

        drawPartnerReadyButton(graphics, x, y);

        int myLevel = XPMath.getLevelForXp(this.menu.myXP);
        int otherLevel = XPMath.getLevelForXp(this.menu.otherXP);
        graphics.text(this.font, xpLabel(this.menu.myXP, false), x + LEFT_XP_TEXT_X, y + XP_TEXT_Y, this.menu.myXP > 0 ? 0xFFD05050 : 0xFF686868, false);
        graphics.text(this.font, xpLabel(this.menu.otherXP, true), x + RIGHT_XP_TEXT_X, y + XP_TEXT_Y, this.menu.otherXP > 0 ? 0xFF168A22 : 0xFF686868, false);

        if (isOverXPInfo(x, y, 0, mouseX, mouseY)) {
            Component tooltip = this.menu.myXP > 0
                    ? Component.translatable("securetrade.gui.give_xp", this.menu.myXP, myLevel)
                    : Component.translatable("securetrade.gui.choose_xp");
            graphics.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
        }
        if (isOverXPInfo(x, y, 180, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable("securetrade.gui.receive_xp", this.menu.otherXP, otherLevel), mouseX, mouseY);
        }

        if (this.menu.countdownSeconds > 0) {
            int cx = x + 178;
            int cy = y + 60;

            graphics.fill(cx - 10, cy - 12, cx + 10, cy + 12, 0x70000000);
            graphics.fill(cx - 12, cy - 10, cx + 12, cy + 10, 0x70000000);

            drawScaledCenteredText(graphics, String.valueOf(this.menu.countdownSeconds), cx, cy - 8, 2.0f, 0xFF55FF55, false);
        }

        drawBlacklistWarning(graphics, x, y);
    }

    private void drawReadyButton(GuiGraphicsExtractor graphics, int x, int y) {
        boolean activeReady = this.menu.myLock || this.menu.countdownSeconds > 0;
        int spriteV = activeReady ? SPRITE_READY_ACTIVE_V : SPRITE_READY_NORMAL_V;
        blit(graphics, x + LEFT_READY_X, y + LEFT_READY_Y, SPRITE_READY_BG_U, spriteV, READY_W, READY_H);
        drawReadyHoverFrame(graphics, x + LEFT_READY_X, y + LEFT_READY_Y, this.readyHoverProgress, activeReady);

        int color = activeReady ? 0xFFF2F2F2 : 0xFFFFFFFF;
        drawCenteredButtonText(graphics, lockButtonText(), x + LEFT_READY_X + READY_W / 2, y + LEFT_READY_Y + (READY_H - 8) / 2, color, activeReady);
    }

    private void drawPartnerReadyButton(GuiGraphicsExtractor graphics, int x, int y) {
        int otherReadySpriteV = this.menu.otherLock || this.menu.countdownSeconds > 0
                ? SPRITE_READY_ACTIVE_V
                : SPRITE_READY_DISABLED_V;
        blit(graphics, x + RIGHT_READY_X, y + RIGHT_READY_Y, SPRITE_READY_BG_U, otherReadySpriteV, READY_W, READY_H);

        Component otherMsg;
        if (this.menu.countdownSeconds > 0) {
            otherMsg = Component.translatable("securetrade.gui.confirmed");
        } else if (this.menu.otherLock) {
            otherMsg = Component.translatable("securetrade.gui.lock");
        } else {
            otherMsg = Component.translatable("securetrade.gui.waiting");
        }
        int otherTextColor = this.menu.otherLock || this.menu.countdownSeconds > 0 ? 0xFFF2F2F2 : 0xFFD0D0D0;
        drawCenteredButtonText(graphics, otherMsg, x + RIGHT_READY_X + READY_W / 2, y + RIGHT_READY_Y + (READY_H - 8) / 2, otherTextColor, false);
    }

    private void drawBlacklistWarning(GuiGraphicsExtractor graphics, int x, int y) {
        long remainingMillis = this.menu.getBlacklistWarningRemainingMillis();
        if (remainingMillis <= 0) {
            return;
        }

        float fade = Math.min(1.0f, remainingMillis / 350.0f);
        int alpha = Math.round(0xE8 * fade);
        int centerX = x + this.imageWidth / 2;
        List<FormattedCharSequence> lines = this.font.split(
                Component.translatable("securetrade.error_blacklisted_item"),
                214
        );
        int textWidth = lines.stream().mapToInt(this.font::width).max().orElse(0);
        int panelWidth = Math.min(238, textWidth + 16);
        int panelHeight = lines.size() * 9 + 10;
        int left = centerX - panelWidth / 2;
        int right = centerX + panelWidth / 2;
        int top = y + 42;
        int bottom = top + panelHeight;

        graphics.fill(left, top, right, bottom, (alpha << 24) | 0x181818);
        graphics.fill(left + 1, top, right - 1, top + 1, (alpha << 24) | 0xD0A020);
        graphics.fill(left + 1, bottom - 1, right - 1, bottom, (alpha << 24) | 0xD0A020);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.text(this.font, line, centerX - this.font.width(line) / 2, top + 5 + i * 9, 0xFFF0B040, false);
        }
    }

    private void drawXPControlButtons(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        boolean canChange = this.menu.countdownSeconds <= 0;
        boolean canSub = canChange && this.menu.myXP > 0;
        boolean canAdd = canChange && this.menu.myXP < XPMath.getPlayerXP(this.minecraft.player);

        int leftMinusU = !canSub
                ? SPRITE_DISABLED_MINUS_U
                : isMouseOver(LEFT_MINUS_X, LEFT_MINUS_Y, BUTTON_SIZE, mouseX, mouseY) ? SPRITE_HOVER_MINUS_U : SPRITE_MINUS_U;
        int leftPlusU = !canAdd
                ? SPRITE_DISABLED_PLUS_U
                : isMouseOver(LEFT_PLUS_X, LEFT_PLUS_Y, BUTTON_SIZE, mouseX, mouseY) ? SPRITE_HOVER_PLUS_U : SPRITE_PLUS_U;

        blit(graphics, x + LEFT_MINUS_X, y + LEFT_MINUS_Y, leftMinusU, SPRITE_CONTROL_V, BUTTON_SIZE, BUTTON_SIZE);
        blit(graphics, x + LEFT_PLUS_X, y + LEFT_PLUS_Y, leftPlusU, SPRITE_CONTROL_V, BUTTON_SIZE, BUTTON_SIZE);
        blit(graphics, x + LEFT_MINUS_X + 180, y + LEFT_MINUS_Y, SPRITE_DISABLED_MINUS_U, SPRITE_CONTROL_V, BUTTON_SIZE, BUTTON_SIZE);
        blit(graphics, x + LEFT_PLUS_X + 180, y + LEFT_PLUS_Y, SPRITE_DISABLED_PLUS_U, SPRITE_CONTROL_V, BUTTON_SIZE, BUTTON_SIZE);
    }

    private void drawReadyHoverFrame(GuiGraphicsExtractor graphics, int x, int y, float progress, boolean activeReady) {
        int alpha = Math.round(0xCC * progress);
        if (alpha <= 0) {
            return;
        }

        int color = (alpha << 24) | 0xFFFFFF;
        if (activeReady) {
            graphics.fill(x + 2, y + READY_H - 2, x + READY_W - 2, y + READY_H - 1, color);
            return;
        }

        graphics.fill(x + 2, y + 1, x + READY_W - 2, y + 2, color);
        graphics.fill(x + 2, y + READY_H - 2, x + READY_W - 2, y + READY_H - 1, color);
        graphics.fill(x + 1, y + 2, x + 2, y + READY_H - 2, color);
        graphics.fill(x + READY_W - 2, y + 2, x + READY_W - 1, y + READY_H - 2, color);
    }

    private void updateReadyAnimations(int mouseX, int mouseY) {
        long now = System.nanoTime();
        float elapsedSeconds = Math.min(0.1f, (now - this.lastArrowFrameNanos) / 1_000_000_000.0f);
        this.lastArrowFrameNanos = now;
        float arrowStep = elapsedSeconds / ARROW_ANIMATION_SECONDS;
        float slotStep = elapsedSeconds / SLOT_HIGHLIGHT_ANIMATION_SECONDS;
        float hoverStep = elapsedSeconds / READY_HOVER_ANIMATION_SECONDS;
        this.myArrowProgress = approach(this.myArrowProgress, this.menu.myLock ? 1.0f : 0.0f, arrowStep);
        this.otherArrowProgress = approach(this.otherArrowProgress, this.menu.otherLock ? 1.0f : 0.0f, arrowStep);
        this.mySlotHighlightProgress = approach(this.mySlotHighlightProgress, this.menu.myLock ? 1.0f : 0.0f, slotStep);
        this.otherSlotHighlightProgress = approach(this.otherSlotHighlightProgress, this.menu.otherLock ? 1.0f : 0.0f, slotStep);
        boolean readyHovered = isMouseOver(LEFT_READY_X, LEFT_READY_Y, READY_W, READY_H, mouseX, mouseY);
        this.readyHoverProgress = approach(this.readyHoverProgress, readyHovered ? 1.0f : 0.0f, hoverStep);
    }

    private static float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    private void drawArrowFill(GuiGraphicsExtractor graphics, int x, int y, float progress, boolean leftArrow) {
        int width = Math.round(SPRITE_LEFT_ARROW_W * progress);
        if (width <= 0) {
            return;
        }

        if (leftArrow) {
            blit(graphics, x + ARROW_X, y + ARROW_Y, SPRITE_LEFT_ARROW_U, SPRITE_LEFT_ARROW_V, width, SPRITE_LEFT_ARROW_H);
        } else {
            int crop = SPRITE_RIGHT_ARROW_W - width;
            blit(graphics, x + ARROW_X + crop, y + ARROW_Y, SPRITE_RIGHT_ARROW_U + crop, SPRITE_RIGHT_ARROW_V, width, SPRITE_RIGHT_ARROW_H);
        }
    }

    private Component xpLabel(long xp, boolean received) {
        if (xp <= 0) {
            return Component.translatable("securetrade.gui.xp_level_zero");
        }
        return Component.translatable(
                received ? "securetrade.gui.xp_level_plus" : "securetrade.gui.xp_level_minus",
                XPMath.getLevelForXp(xp)
        );
    }

    private boolean isOverXPInfo(int x, int y, int panelOffset, double mouseX, double mouseY) {
        return mouseX >= x + LEFT_XP_BAR_X + panelOffset
                && mouseX <= x + LEFT_XP_BAR_X + panelOffset + XP_BAR_W
                && mouseY >= y + LEFT_XP_BAR_Y - 14
                && mouseY <= y + LEFT_XP_BAR_Y + XP_BAR_H + 4;
    }

    private void drawCenteredButtonText(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int color, boolean shadow) {
        int textWidth = this.font.width(text);
        int textX = centerX - textWidth / 2;
        if (shadow) {
            graphics.text(this.font, text, textX + 1, y + 1, 0xFF303030, false);
        }
        graphics.text(this.font, text, textX, y, color, false);
    }

    private void drawScaledCenteredText(GuiGraphicsExtractor graphics, String text, int centerX, int y, float scale, int color, boolean shadow) {
        int textWidth = this.font.width(text);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        graphics.text(this.font, text, (int) ((centerX - textWidth * scale / 2.0f) / scale), (int) (y / scale), color, shadow);
        graphics.pose().popMatrix();
    }

    private void drawCenteredPanelText(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int maxWidth, int color) {
        int textWidth = this.font.width(text);
        if (textWidth <= maxWidth) {
            graphics.text(this.font, text, centerX - textWidth / 2, y, color | 0xFF000000, false);
            return;
        }

        float scale = Math.max(0.7f, (float) maxWidth / textWidth);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        graphics.text(this.font, text, (int) (centerX / scale - textWidth / 2.0f), (int) (y / scale), color | 0xFF000000, false);
        graphics.pose().popMatrix();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int myColor = lerpColor(0x404040, 0x00AA00, this.mySlotHighlightProgress);
        int otherColor = lerpColor(0x404040, 0x00AA00, this.otherSlotHighlightProgress);

        drawCenteredPanelText(graphics, Component.translatable("securetrade.gui.you_offer"), 88, 6, 162, myColor);

        if (!this.menu.partnerName.isEmpty()) {
            Component partnerText = Component.translatable("securetrade.gui.partner_offers", this.menu.partnerName);
            drawCenteredPanelText(graphics, partnerText, 268, 6, 162, otherColor);
        }
    }

    private void extractTradeBackground(GuiGraphicsExtractor graphics) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        blit(graphics, x, y, 0, 0, 356, 205);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == 0 && this.menu.countdownSeconds <= 0) {
            int x = (this.width - this.imageWidth) / 2;
            int y = (this.height - this.imageHeight) / 2;

            if (isMouseOver(LEFT_READY_X, LEFT_READY_Y, READY_W, READY_H, mouseX, mouseY)) {
                playButtonClickSound();
                Services.PLATFORM.sendLockPacket(!this.menu.myLock);
                return true;
            }

            if (mouseX >= x + LEFT_MINUS_X && mouseX <= x + LEFT_MINUS_X + BUTTON_SIZE &&
                mouseY >= y + LEFT_MINUS_Y && mouseY <= y + LEFT_MINUS_Y + BUTTON_SIZE) {
                if (this.menu.myXP > 0) {
                    playButtonClickSound();
                    adjustXPByLevel(false);
                    return true;
                }
            }

            if (mouseX >= x + LEFT_PLUS_X && mouseX <= x + LEFT_PLUS_X + BUTTON_SIZE &&
                mouseY >= y + LEFT_PLUS_Y && mouseY <= y + LEFT_PLUS_Y + BUTTON_SIZE) {
                if (this.menu.myXP < XPMath.getPlayerXP(this.minecraft.player)) {
                    playButtonClickSound();
                    adjustXPByLevel(true);
                    return true;
                }
            }

            if (isOverMySlider(mouseX, mouseY)) {
                this.isDraggingSlider = true;
                updateXPFromSlider(mouseX);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void playButtonClickSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void adjustXPByLevel(boolean increase) {
        long currentXP = this.menu.myXP;
        int currentLevel = XPMath.getLevelForXp(currentXP);
        long maxXP = XPMath.getPlayerXP(this.minecraft.player);
        long targetXP;

        if (increase) {
            targetXP = XPMath.getXpForLevels(currentLevel + 1);
            if (targetXP > maxXP) {
                targetXP = maxXP;
            }
        } else {
            long currentLevelXP = XPMath.getXpForLevels(currentLevel);
            if (currentXP > currentLevelXP) {
                targetXP = currentLevelXP;
            } else {
                targetXP = XPMath.getXpForLevels(Math.max(0, currentLevel - 1));
            }
        }

        setOfferedXP(targetXP);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            this.isDraggingSlider = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.isDraggingSlider && event.button() == 0 && this.menu.countdownSeconds <= 0) {
            updateXPFromSlider(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private boolean isOverMySlider(double mouseX, double mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int sliderX = x + LEFT_XP_BAR_X;
        int sliderY = y + LEFT_XP_BAR_Y;
        return mouseX >= sliderX - 2 && mouseX <= sliderX + XP_BAR_W + 2 && mouseY >= sliderY - 4 && mouseY <= sliderY + XP_BAR_H + 4;
    }

    private void updateXPFromSlider(double mouseX) {
        long totalXP = XPMath.getPlayerXP(this.minecraft.player);
        if (totalXP <= 0) {
            this.sliderValue = 0.0f;
            setOfferedXP(0);
            return;
        }

        int x = (this.width - this.imageWidth) / 2;
        int sliderX = x + LEFT_XP_BAR_X;
        double pct = (mouseX - sliderX) / (double) XP_BAR_W;
        pct = Math.max(0.0, Math.min(1.0, pct));

        long newXP = (long) (pct * totalXP);
        this.sliderValue = (float) newXP / totalXP;
        setOfferedXP(newXP);
    }

    private void setOfferedXP(long xp) {
        if (this.menu.myXP != xp) {
            this.menu.myXP = xp;
            Services.PLATFORM.sendXPChangePacket(xp);
        }
    }

    private static void blit(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int u, int v, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, 512, 512);
    }

    private static void blit(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int width, int height) {
        blit(graphics, TRADE_TEXTURE, x, y, u, v, width, height);
    }
}
