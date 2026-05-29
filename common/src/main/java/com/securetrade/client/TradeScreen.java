package com.securetrade.client;

import com.securetrade.XPMath;
import com.securetrade.TradeMessages;
import com.securetrade.menu.TradeMenu;
import com.securetrade.platform.Services;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class TradeScreen extends AbstractContainerScreen<TradeMenu> {
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

    private Button lockButton;
    private Button itemsTabButton;
    private Button xpTabButton;
    private float sliderValue = 0.0f;
    private boolean isDraggingSlider = false;
    private boolean xpTabSelected = false;
    private int lastOtherXP = 0;

    public TradeScreen(TradeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 185;
    }

    @Override
    protected void init() {
        super.init();

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        this.itemsTabButton = new Button(x + 61, y + 18, 34, 14, TradeMessages.trans("securetrade.gui.tab.items"), button -> {
            this.xpTabSelected = false;
            updateWidgetPositions();
        });

        this.xpTabButton = new Button(x + 95, y + 18, 20, 14, TradeMessages.trans("securetrade.gui.tab.xp"), button -> {
            this.xpTabSelected = true;
            updateWidgetPositions();
        });

        this.lockButton = new Button(x + 63, y + 67, 50, 18, TradeMessages.trans("securetrade.gui.lock"), button -> {
            boolean newState = !this.menu.myLock;
            Services.PLATFORM.sendLockPacket(newState);
        });

        this.addRenderableWidget(this.itemsTabButton);
        this.addRenderableWidget(this.xpTabButton);
        this.addRenderableWidget(this.lockButton);

        if (this.menu.myXP > 0 || this.menu.otherXP > 0) {
            this.xpTabSelected = true;
        }
        this.lastOtherXP = this.menu.otherXP;

        updateWidgetPositions();
    }

    private void updateWidgetPositions() {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        this.itemsTabButton.x = x + 61;
        this.itemsTabButton.y = y + 18;
        this.xpTabButton.x = x + 95;
        this.xpTabButton.y = y + 18;
        this.lockButton.x = x + 63;
        this.lockButton.y = y + 67;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        updateDynamicWidgetText();

        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        drawSlotHighlights(poseStack, x, y);
        if (this.xpTabSelected) {
            drawXPTab(poseStack, x, y, mouseX, mouseY);
        } else {
            drawItemsTab(poseStack, x, y, mouseX, mouseY);
        }

        this.renderTooltip(poseStack, mouseX, mouseY);
    }

    private void updateDynamicWidgetText() {
        this.lockButton.setMessage(TradeMessages.trans(
                this.menu.countdownSeconds > 0 || this.menu.myLock
                        ? "securetrade.gui.cancel"
                        : "securetrade.gui.lock"
        ));
    }

    private void drawSlotHighlights(PoseStack poseStack, int x, int y) {
        if (this.menu.myLock) {
            for (int row = 0; row < 4; ++row) {
                for (int col = 0; col < 3; ++col) {
                    fill(poseStack, x + 8 + col * 18, y + 18 + row * 18, x + 24 + col * 18, y + 34 + row * 18, 0x3500FF00);
                }
            }
        }

        if (this.menu.otherLock) {
            for (int row = 0; row < 4; ++row) {
                for (int col = 0; col < 3; ++col) {
                    fill(poseStack, x + 116 + col * 18, y + 18 + row * 18, x + 132 + col * 18, y + 34 + row * 18, 0x3500FF00);
                }
            }
        }
    }

    private void drawItemsTab(PoseStack poseStack, int x, int y, int mouseX, int mouseY) {
        drawStatusWell(poseStack, x, y);

        if (this.menu.countdownSeconds > 0) {
            drawScaledCenteredText(poseStack, String.valueOf(this.menu.countdownSeconds), x + 88, y + 40, 2.0f, 0x55FF55, false);
        }

        if (this.menu.countdownSeconds <= 0 && !this.menu.myLock && (isOverItemsHint(mouseX, mouseY) || this.lockButton.isMouseOver(mouseX, mouseY))) {
            this.renderTooltip(poseStack, TradeMessages.trans("securetrade.gui.items_help"), mouseX, mouseY);
        }
    }

    private void drawStatusWell(PoseStack poseStack, int x, int y) {
        int left = x + STATUS_LEFT_OFFSET;
        int top = y + STATUS_TOP_OFFSET;
        int right = x + STATUS_RIGHT_OFFSET;
        int bottom = y + STATUS_BOTTOM_OFFSET;

        fill(poseStack, left, top, right, bottom, 0x55303030);
        fill(poseStack, left, top, right, top + 1, 0x66464646);
        fill(poseStack, left, bottom - 1, right, bottom, 0x66FFFFFF);
        fill(poseStack, left, top, left + 1, bottom, 0x66464646);
        fill(poseStack, right - 1, top, right, bottom, 0x66FFFFFF);

        if (this.menu.countdownSeconds > 0) {
            return;
        }

        if (!this.menu.myLock && !this.menu.otherLock) {
            drawItemsHintIcon(poseStack, left, top);
            return;
        }

        if (this.menu.myLock || this.menu.otherLock) {
            int readyLeft = left + 4;
            int readyRight = right - 4;
            int readyY = bottom - 5;
            if (this.menu.myLock) {
                fill(poseStack, readyLeft, readyY, readyLeft + 18, readyY + 2, 0xFF55FF55);
            }
            if (this.menu.otherLock) {
                fill(poseStack, readyRight - 18, readyY, readyRight, readyY + 2, 0xFF55FF55);
            }
        }
    }

    private void drawItemsHintIcon(PoseStack poseStack, int left, int top) {
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

    private void drawXPTab(PoseStack poseStack, int x, int y, int mouseX, int mouseY) {
        int totalXP = XPMath.getPlayerXP(this.minecraft.player);
        if (!this.isDraggingSlider) {
            this.sliderValue = totalXP > 0 ? (float) this.menu.myXP / totalXP : 0.0f;
        }

        if (this.menu.countdownSeconds > 0) {
            drawStatusWell(poseStack, x, y);
            drawScaledCenteredText(poseStack, String.valueOf(this.menu.countdownSeconds), x + 88, y + 40, 2.0f, 0x55FF55, false);
            return;
        }

        int myLevel = XPMath.getLevelForXp(this.menu.myXP);
        int otherLevel = XPMath.getLevelForXp(this.menu.otherXP);
        String myText = this.menu.myXP > 0 ? "-" + this.menu.myXP + " XP" : "0 XP";
        drawCenteredText(poseStack, myText, x + 88, y + 36, this.menu.myXP > 0 ? 0xC84E4E : 0x686868, false);

        drawExperienceBar(poseStack, x + XP_BAR_X, y + XP_BAR_Y, XP_BAR_WIDTH, this.sliderValue, true);
        if (this.menu.otherXP > 0) {
            String otherText = "+" + this.menu.otherXP + " XP";
            drawScaledCenteredText(poseStack, otherText, x + 88, y + 56, 0.85f, 0x1FB81F, false);
        }

        if (mouseX >= x + XP_BAR_X && mouseX <= x + XP_BAR_X + XP_BAR_WIDTH && mouseY >= y + 39 && mouseY <= y + 56) {
            Component tooltip = this.menu.myXP > 0
                    ? TradeMessages.trans("securetrade.gui.give_xp", this.menu.myXP, myLevel)
                    : TradeMessages.trans("securetrade.gui.choose_xp");
            this.renderTooltip(poseStack, tooltip, mouseX, mouseY);
        }
        if (mouseX >= x + 64 && mouseX <= x + 112 && mouseY >= y + 56 && mouseY <= y + 70) {
            this.renderTooltip(poseStack, TradeMessages.trans("securetrade.gui.receive_xp", this.menu.otherXP, otherLevel), mouseX, mouseY);
        }
    }

    private void drawCenteredText(PoseStack poseStack, String text, int centerX, int y, int color) {
        drawCenteredText(poseStack, text, centerX, y, color, true);
    }

    private void drawCenteredText(PoseStack poseStack, String text, int centerX, int y, int color, boolean shadow) {
        int textWidth = this.font.width(text);
        if (shadow) {
            this.font.drawShadow(poseStack, text, centerX - textWidth / 2.0f, y, color);
        } else {
            this.font.draw(poseStack, text, centerX - textWidth / 2.0f, y, color);
        }
    }

    private void drawScaledCenteredText(PoseStack poseStack, String text, int centerX, int y, float scale, int color) {
        drawScaledCenteredText(poseStack, text, centerX, y, scale, color, true);
    }

    private void drawScaledCenteredText(PoseStack poseStack, String text, int centerX, int y, float scale, int color, boolean shadow) {
        int textWidth = this.font.width(text);
        poseStack.pushPose();
        poseStack.scale(scale, scale, 1.0f);
        float textX = (centerX - textWidth * scale / 2.0f) / scale;
        float textY = y / scale;
        if (shadow) {
            this.font.drawShadow(poseStack, text, textX, textY, color);
        } else {
            this.font.draw(poseStack, text, textX, textY, color);
        }
        poseStack.popPose();
    }

    private boolean isOverItemsHint(double mouseX, double mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        return !this.xpTabSelected
                && mouseX >= x + STATUS_LEFT_OFFSET
                && mouseX <= x + STATUS_RIGHT_OFFSET
                && mouseY >= y + STATUS_TOP_OFFSET
                && mouseY <= y + STATUS_BOTTOM_OFFSET;
    }

    private void drawExperienceBar(PoseStack poseStack, int x, int y, int width, float value, boolean interactive) {
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
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        int myColor = this.menu.myLock ? 0x00AA00 : 4210752;
        int otherColor = this.menu.otherLock ? 0x00AA00 : 4210752;

        this.font.draw(poseStack, TradeMessages.trans("securetrade.gui.me"), 8, 6, myColor);
        this.font.draw(poseStack, TradeMessages.trans("securetrade.gui.them"), 116, 6, otherColor);
        this.font.draw(poseStack, this.playerInventoryTitle, 8, this.imageHeight - 94, 4210752);

        if (this.menu.otherXP > this.lastOtherXP && this.menu.otherXP > 0 && !this.xpTabSelected) {
            this.xpTabSelected = true;
            updateWidgetPositions();
        }
        this.lastOtherXP = this.menu.otherXP;
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        ResourceLocation generic54 = new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

        RenderSystem.setShaderTexture(0, generic54);
        blit(poseStack, x, y, 0, 0, this.imageWidth, 4 * 18 + 17);
        blit(poseStack, x, y + 4 * 18 + 17, 0, 126, this.imageWidth, 96);
        fill(poseStack, x + CENTER_X, y + 17, x + CENTER_X + CENTER_WIDTH, y + 89, 0xFFC6C6C6);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.xpTabSelected && this.menu.countdownSeconds <= 0 && isOverMySlider(mouseX, mouseY)) {
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
        if (this.isDraggingSlider && button == 0 && this.menu.countdownSeconds <= 0) {
            updateXPFromSlider(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean isOverMySlider(double mouseX, double mouseY) {
        int sliderX = this.leftPos + XP_BAR_X;
        int sliderY = this.topPos + XP_BAR_Y;
        int sliderW = XP_BAR_WIDTH;
        int sliderH = XP_BAR_HEIGHT;
        return mouseX >= sliderX - 2 && mouseX <= sliderX + sliderW + 2 && mouseY >= sliderY - 4 && mouseY <= sliderY + sliderH + 4;
    }

    private void updateXPFromSlider(double mouseX) {
        int totalXP = XPMath.getPlayerXP(this.minecraft.player);
        if (totalXP <= 0) {
            this.sliderValue = 0.0f;
            setOfferedXP(0);
            return;
        }

        int sliderX = this.leftPos + XP_BAR_X;
        int sliderW = XP_BAR_WIDTH;
        double pct = (mouseX - (sliderX + 1)) / (double)(sliderW - 2);
        pct = Math.max(0.0, Math.min(1.0, pct));

        int newXP = (int) (pct * totalXP);
        this.sliderValue = (float) newXP / totalXP;
        setOfferedXP(newXP);
    }

    private void setOfferedXP(int xp) {
        if (this.menu.myXP != xp) {
            this.menu.myXP = xp;
            Services.PLATFORM.sendXPChangePacket(xp);
        }
    }
}
