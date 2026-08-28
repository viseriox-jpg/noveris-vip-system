package com.noveris.vip.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

abstract class NoverisContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("noveris_vip_system", "textures/gui/vip_container.png");
    private static final ResourceLocation SLOTS =
            ResourceLocation.fromNamespaceAndPath("noveris_vip_system", "textures/gui/vip_slots.png");

    NoverisContainerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = 128;
        titleLabelX = 8;
        titleLabelY = 7;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        renderSections(graphics);
        graphics.blit(SLOTS, leftPos + 7, topPos + 17, 0, 0, 162, 108, 256, 256);
    }

    protected abstract void renderSections(GuiGraphics graphics);

    protected void panel(GuiGraphics graphics, int y, int color) {
        graphics.fill(leftPos + 6, topPos + y, leftPos + 170, topPos + y + 38, color);
    }
}
