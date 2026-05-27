package com.securetrade.client;

import com.securetrade.menu.TradeMenu;
import com.securetrade.network.TradeLockPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class TradeScreen extends AbstractContainerScreen<TradeMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("securetrade", "textures/gui/trade_gui.png");
    private Button lockButton;

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

        this.lockButton = Button.builder(Component.translatable("securetrade.gui.lock"), button -> {
            boolean newState = !this.menu.myLock;
            PacketDistributor.sendToServer(new TradeLockPacket(newState));
        }).bounds(x + 63, y + 42, 50, 20).build();

        this.addRenderableWidget(this.lockButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Draw locked slot highlights
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        
        if (this.menu.myLock) {
            for (int row = 0; row < 4; ++row) {
                for (int col = 0; col < 3; ++col) {
                    guiGraphics.fill(x + 8 + col * 18, y + 18 + row * 18, x + 8 + col * 18 + 16, y + 18 + row * 18 + 16, 0x3500FF00);
                }
            }
        }
        
        if (this.menu.otherLock) {
            for (int row = 0; row < 4; ++row) {
                for (int col = 0; col < 3; ++col) {
                    guiGraphics.fill(x + 116 + col * 18, y + 18 + row * 18, x + 116 + col * 18 + 16, y + 18 + row * 18 + 16, 0x3500FF00);
                }
            }
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
        
        // Update button text based on state
        if (this.menu.countdownSeconds > 0) {
            this.lockButton.setMessage(Component.translatable("securetrade.gui.cancel"));
        } else {
            this.lockButton.setMessage(Component.translatable(this.menu.myLock ? "securetrade.gui.ready" : "securetrade.gui.lock"));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int myColor = this.menu.myLock ? 0x00AA00 : 4210752;
        int otherColor = this.menu.otherLock ? 0x00AA00 : 4210752;

        guiGraphics.drawString(this.font, Component.translatable("securetrade.gui.me"), 8, 6, myColor, false);
        guiGraphics.drawString(this.font, Component.translatable("securetrade.gui.them"), 116, 6, otherColor, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, this.imageHeight - 94, 4210752, false);

        // Draw countdown label
        if (this.menu.countdownSeconds > 0) {
            String text = this.menu.countdownSeconds + "...";
            int textWidth = this.font.width(text);
            guiGraphics.drawString(this.font, text, 88 - textWidth / 2, 28, 0x00AA00, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        
        ResourceLocation generic54 = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
        
        // Draw top half (4 rows of 9 slots)
        guiGraphics.blit(generic54, x, y, 0, 0, this.imageWidth, 4 * 18 + 17);
        // Draw bottom half (player inventory)
        guiGraphics.blit(generic54, x, y + 4 * 18 + 17, 0, 126, this.imageWidth, 96);
        
        // Cover the middle 3 columns of slots with flat grey background
        guiGraphics.fill(x + 61, y + 17, x + 115, y + 89, 0xFFC6C6C6);
    }
}
