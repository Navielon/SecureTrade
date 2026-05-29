package com.securetrade.client;

import com.securetrade.XPMath;
import com.securetrade.TradeMessages;
import com.securetrade.menu.TradeMenu;
import com.securetrade.platform.Services;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.entity.player.PlayerInventory;

public class TradeScreen extends HandledScreen<TradeMenu> {
    private static final int CENTER_X = 61;
    private static final int CENTER_WIDTH = 54;
    private static final int XP_BAR_X = 64;
    private static final int XP_BAR_Y = 48;
    private static final int XP_BAR_WIDTH = 48;
    private static final int XP_BAR_HEIGHT = 5;
    private static final int STATUS_LEFT_OFFSET = 64;
    private static final int STATUS_TOP_OFFSET = 36;
    private static final int STATUS_RIGHT_OFFSET = 112;
    private static final int STATUS_BOTTOM_OFFSET = 62;

    private ButtonWidget lockButtonWidget;
    private ButtonWidget itemsTabButtonWidget;
    private ButtonWidget xpTabButtonWidget;
    private float sliderValue = 0.0f;
    private boolean isDraggingSlider = false;
    private boolean xpTabSelected = false;
    private int lastOtherXP = 0;

    public TradeScreen(TradeMenu menu, PlayerInventory playerInventory, Text title) {
        super(menu, playerInventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 185;
    }

    @Override
    protected void init() {
        super.init();

        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        this.itemsTabButtonWidget = new ButtonWidget(x + 61, y + 18, 34, 14, TradeMessages.trans("securetrade.gui.tab.items"), button -> {
            this.xpTabSelected = false;
            updateWidgetPositions();
        });

        this.xpTabButtonWidget = new ButtonWidget(x + 95, y + 18, 20, 14, TradeMessages.trans("securetrade.gui.tab.xp"), button -> {
            this.xpTabSelected = true;
            updateWidgetPositions();
        });

        this.lockButtonWidget = new ButtonWidget(x + 63, y + 67, 50, 18, TradeMessages.trans("securetrade.gui.lock"), button -> {
            boolean newState = !this.handler.myLock;
            Services.PLATFORM.sendLockPacket(newState);
        });

        this.addButton(this.itemsTabButtonWidget);
        this.addButton(this.xpTabButtonWidget);
        this.addButton(this.lockButtonWidget);

        if (this.handler.myXP > 0 || this.handler.otherXP > 0) {
            this.xpTabSelected = true;
        }
        this.lastOtherXP = this.handler.otherXP;

        updateWidgetPositions();
    }

    private void updateWidgetPositions() {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        this.itemsTabButtonWidget.x = x + 61;
        this.itemsTabButtonWidget.y = y + 18;
        this.xpTabButtonWidget.x = x + 95;
        this.xpTabButtonWidget.y = y + 18;
        this.lockButtonWidget.x = x + 63;
        this.lockButtonWidget.y = y + 67;
    }

    @Override
    public void render(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
        updateDynamicWidgetText();

        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);

        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        drawSlotHighlights(poseStack, x, y);
        if (this.xpTabSelected) {
            drawXPTab(poseStack, x, y, mouseX, mouseY);
        } else {
            drawItemsTab(poseStack, x, y, mouseX, mouseY);
        }

        this.drawMouseoverTooltip(poseStack, mouseX, mouseY);
    }

    private void updateDynamicWidgetText() {
        this.lockButtonWidget.setMessage(TradeMessages.trans(
                this.handler.countdownSeconds > 0 || this.handler.myLock
                        ? "securetrade.gui.cancel"
                        : "securetrade.gui.lock"
        ));
    }

    private void drawSlotHighlights(MatrixStack poseStack, int x, int y) {
        if (this.handler.myLock) {
            for (int row = 0; row < 4; ++row) {
                for (int col = 0; col < 3; ++col) {
                    fill(poseStack, x + 8 + col * 18, y + 18 + row * 18, x + 24 + col * 18, y + 34 + row * 18, 0x3500FF00);
                }
            }
        }

        if (this.handler.otherLock) {
            for (int row = 0; row < 4; ++row) {
                for (int col = 0; col < 3; ++col) {
                    fill(poseStack, x + 116 + col * 18, y + 18 + row * 18, x + 132 + col * 18, y + 34 + row * 18, 0x3500FF00);
                }
            }
        }
    }

    private void drawItemsTab(MatrixStack poseStack, int x, int y, int mouseX, int mouseY) {
        drawStatusWell(poseStack, x, y);

        if (this.handler.countdownSeconds > 0) {
            drawScaledCenteredText(poseStack, String.valueOf(this.handler.countdownSeconds), x + 88, y + 40, 2.0f, 0x55FF55, false);
        }

        if (this.handler.countdownSeconds <= 0 && !this.handler.myLock && (isOverItemsHint(mouseX, mouseY) || this.lockButtonWidget.isMouseOver(mouseX, mouseY))) {
            this.renderTooltip(poseStack, TradeMessages.trans("securetrade.gui.items_help"), mouseX, mouseY);
        }
    }

    private void drawStatusWell(MatrixStack poseStack, int x, int y) {
        int left = x + STATUS_LEFT_OFFSET;
        int top = y + STATUS_TOP_OFFSET;
        int right = x + STATUS_RIGHT_OFFSET;
        int bottom = y + STATUS_BOTTOM_OFFSET;

        fill(poseStack, left, top, right, bottom, 0x55303030);
        fill(poseStack, left, top, right, top + 1, 0x66464646);
        fill(poseStack, left, bottom - 1, right, bottom, 0x66FFFFFF);
        fill(poseStack, left, top, left + 1, bottom, 0x66464646);
        fill(poseStack, right - 1, top, right, bottom, 0x66FFFFFF);

        if (this.handler.countdownSeconds > 0) {
            return;
        }

        if (!this.handler.myLock && !this.handler.otherLock) {
            drawItemsHintIcon(poseStack, left, top);
            return;
        }

        if (this.handler.myLock || this.handler.otherLock) {
            int readyLeft = left + 4;
            int readyRight = right - 4;
            int readyY = bottom - 5;
            if (this.handler.myLock) {
                fill(poseStack, readyLeft, readyY, readyLeft + 18, readyY + 2, 0xFF55FF55);
            }
            if (this.handler.otherLock) {
                fill(poseStack, readyRight - 18, readyY, readyRight, readyY + 2, 0xFF55FF55);
            }
        }
    }

    private void drawItemsHintIcon(MatrixStack poseStack, int left, int top) {
        int slotX = left + 7;
        int slotY = top + 8;
        fill(poseStack, slotX, slotY, slotX + 10, slotY + 10, 0x55303030);
        fill(poseStack, slotX, slotY, slotX + 10, slotY + 1, 0x88707070);
        fill(poseStack, slotX, slotY, slotX + 1, slotY + 10, 0x88707070);
        fill(poseStack, slotX + 9, slotY, slotX + 10, slotY + 10, 0x88D0D0D0);
        fill(poseStack, slotX, slotY + 9, slotX + 10, slotY + 10, 0x88D0D0D0);

        int arrowX = left + 22;
        int arrowY = top + 13;
        fill(poseStack, arrowX, arrowY, arrowX + 10, arrowY + 2, 0x88707070);
        fill(poseStack, arrowX + 8, arrowY - 2, arrowX + 10, arrowY + 4, 0x88707070);
        fill(poseStack, arrowX + 10, arrowY - 1, arrowX + 12, arrowY + 3, 0x88707070);

        int checkX = left + 37;
        int checkY = top + 10;
        fill(poseStack, checkX, checkY + 6, checkX + 2, checkY + 8, 0xAA55FF55);
        fill(poseStack, checkX + 2, checkY + 8, checkX + 4, checkY + 10, 0xAA55FF55);
        fill(poseStack, checkX + 4, checkY + 4, checkX + 6, checkY + 8, 0xAA55FF55);
        fill(poseStack, checkX + 6, checkY + 2, checkX + 8, checkY + 6, 0xAA55FF55);
    }

    private void drawXPTab(MatrixStack poseStack, int x, int y, int mouseX, int mouseY) {
        int totalXP = XPMath.getPlayerXP(this.client.player);
        if (!this.isDraggingSlider) {
            this.sliderValue = totalXP > 0 ? (float) this.handler.myXP / totalXP : 0.0f;
        }

        if (this.handler.countdownSeconds > 0) {
            drawStatusWell(poseStack, x, y);
            drawScaledCenteredText(poseStack, String.valueOf(this.handler.countdownSeconds), x + 88, y + 40, 2.0f, 0x55FF55, false);
            return;
        }

        int myLevel = XPMath.getLevelForXp(this.handler.myXP);
        int otherLevel = XPMath.getLevelForXp(this.handler.otherXP);
        String myText = this.handler.myXP > 0 ? "-" + this.handler.myXP + " XP" : "0 XP";
        drawCenteredText(poseStack, myText, x + 88, y + 36, this.handler.myXP > 0 ? 0xC84E4E : 0x686868, false);

        drawExperienceBar(poseStack, x + XP_BAR_X, y + XP_BAR_Y, XP_BAR_WIDTH, this.sliderValue, true);
        if (this.handler.otherXP > 0) {
            String otherText = "+" + this.handler.otherXP + " XP";
            drawScaledCenteredText(poseStack, otherText, x + 88, y + 56, 0.85f, 0x1FB81F, false);
        }

        if (mouseX >= x + XP_BAR_X && mouseX <= x + XP_BAR_X + XP_BAR_WIDTH && mouseY >= y + 39 && mouseY <= y + 56) {
            Text tooltip = this.handler.myXP > 0
                    ? TradeMessages.trans("securetrade.gui.give_xp", this.handler.myXP, myLevel)
                    : TradeMessages.trans("securetrade.gui.choose_xp");
            this.renderTooltip(poseStack, tooltip, mouseX, mouseY);
        }
        if (mouseX >= x + 64 && mouseX <= x + 112 && mouseY >= y + 56 && mouseY <= y + 70) {
            this.renderTooltip(poseStack, TradeMessages.trans("securetrade.gui.receive_xp", this.handler.otherXP, otherLevel), mouseX, mouseY);
        }
    }

    private void drawCenteredText(MatrixStack poseStack, String text, int centerX, int y, int color) {
        drawCenteredText(poseStack, text, centerX, y, color, true);
    }

    private void drawCenteredText(MatrixStack poseStack, String text, int centerX, int y, int color, boolean shadow) {
        int textWidth = this.textRenderer.getWidth(text);
        if (shadow) {
            this.textRenderer.drawWithShadow(poseStack, text, centerX - textWidth / 2.0f, y, color);
        } else {
            this.textRenderer.draw(poseStack, text, centerX - textWidth / 2.0f, y, color);
        }
    }

    private void drawScaledCenteredText(MatrixStack poseStack, String text, int centerX, int y, float scale, int color) {
        drawScaledCenteredText(poseStack, text, centerX, y, scale, color, true);
    }

    private void drawScaledCenteredText(MatrixStack poseStack, String text, int centerX, int y, float scale, int color, boolean shadow) {
        int textWidth = this.textRenderer.getWidth(text);
        poseStack.push();
        poseStack.scale(scale, scale, 1.0f);
        float textX = (centerX - textWidth * scale / 2.0f) / scale;
        float textY = y / scale;
        if (shadow) {
            this.textRenderer.drawWithShadow(poseStack, text, textX, textY, color);
        } else {
            this.textRenderer.draw(poseStack, text, textX, textY, color);
        }
        poseStack.pop();
    }

    private boolean isOverItemsHint(double mouseX, double mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        return !this.xpTabSelected
                && mouseX >= x + STATUS_LEFT_OFFSET
                && mouseX <= x + STATUS_RIGHT_OFFSET
                && mouseY >= y + STATUS_TOP_OFFSET
                && mouseY <= y + STATUS_BOTTOM_OFFSET;
    }

    private void drawExperienceBar(MatrixStack poseStack, int x, int y, int width, float value, boolean interactive) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        int fillWidth = (int) ((width - 2) * clamped);

        fill(poseStack, x, y, x + width, y + XP_BAR_HEIGHT, 0xFF1F1F1F);
        fill(poseStack, x + 1, y + 1, x + width - 1, y + XP_BAR_HEIGHT - 1, 0xFF3F3F3F);
        if (fillWidth > 0) {
            fill(poseStack, x + 1, y + 1, x + 1 + fillWidth, y + 2, 0xFFD8FF77);
            fill(poseStack, x + 1, y + 2, x + 1 + fillWidth, y + 4, 0xFF6FCC20);
        }

        for (int tick = 6; tick < width - 2; tick += 6) {
            fill(poseStack, x + tick, y + 1, x + tick + 1, y + XP_BAR_HEIGHT - 1, 0x88000000);
        }

        if (interactive) {
            int markerX = x + 1 + fillWidth;
            fill(poseStack, markerX - 1, y - 2, markerX + 1, y + XP_BAR_HEIGHT + 2, 0xFFFFFFFF);
            fill(poseStack, markerX, y - 1, markerX + 1, y + XP_BAR_HEIGHT + 1, 0xFF202020);
        }
    }

    @Override
    protected void drawForeground(MatrixStack poseStack, int mouseX, int mouseY) {
        int myColor = this.handler.myLock ? 0x00AA00 : 4210752;
        int otherColor = this.handler.otherLock ? 0x00AA00 : 4210752;

        this.textRenderer.draw(poseStack, TradeMessages.trans("securetrade.gui.me"), 8, 6, myColor);
        this.textRenderer.draw(poseStack, TradeMessages.trans("securetrade.gui.them"), 116, 6, otherColor);
        this.textRenderer.draw(poseStack, TradeMessages.trans("container.inventory"), 8, this.backgroundHeight - 94, 4210752);

        if (this.handler.otherXP > this.lastOtherXP && this.handler.otherXP > 0 && !this.xpTabSelected) {
            this.xpTabSelected = true;
            updateWidgetPositions();
        }
        this.lastOtherXP = this.handler.otherXP;
    }

    @Override
    protected void drawBackground(MatrixStack poseStack, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        Identifier generic54 = new Identifier("minecraft", "textures/gui/container/generic_54.png");

        this.client.getTextureManager().bindTexture(generic54);
        drawTexture(poseStack, x, y, 0, 0, this.backgroundWidth, 4 * 18 + 17);
        drawTexture(poseStack, x, y + 4 * 18 + 17, 0, 126, this.backgroundWidth, 96);
        fill(poseStack, x + CENTER_X, y + 17, x + CENTER_X + CENTER_WIDTH, y + 89, 0xFFC6C6C6);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.xpTabSelected && this.handler.countdownSeconds <= 0 && isOverMySlider(mouseX, mouseY)) {
            this.isDraggingSlider = true;
            updateXPFromSlider(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDraggingSlider = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingSlider && button == 0 && this.handler.countdownSeconds <= 0) {
            updateXPFromSlider(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean isOverMySlider(double mouseX, double mouseY) {
        int sliderX = this.x + XP_BAR_X;
        int sliderY = this.y + XP_BAR_Y;
        int sliderW = XP_BAR_WIDTH;
        int sliderH = XP_BAR_HEIGHT;
        return mouseX >= sliderX - 2 && mouseX <= sliderX + sliderW + 2 && mouseY >= sliderY - 4 && mouseY <= sliderY + sliderH + 4;
    }

    private void updateXPFromSlider(double mouseX) {
        int totalXP = XPMath.getPlayerXP(this.client.player);
        if (totalXP <= 0) {
            this.sliderValue = 0.0f;
            setOfferedXP(0);
            return;
        }

        int sliderX = this.x + XP_BAR_X;
        int sliderW = XP_BAR_WIDTH;
        double pct = (mouseX - (sliderX + 1)) / (double)(sliderW - 2);
        pct = Math.max(0.0, Math.min(1.0, pct));

        int newXP = (int) (pct * totalXP);
        this.sliderValue = (float) newXP / totalXP;
        setOfferedXP(newXP);
    }

    private void setOfferedXP(int xp) {
        if (this.handler.myXP != xp) {
            this.handler.myXP = xp;
            Services.PLATFORM.sendXPChangePacket(xp);
        }
    }
}

