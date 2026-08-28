package com.noveris.vip.client;

import com.noveris.vip.VaultViewMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class VipViewScreen extends NoverisContainerScreen<VaultViewMenu> {
    VipViewScreen(VaultViewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderSections(GuiGraphics graphics) {
        if (title.getString().startsWith("Kit ")) {
            panel(graphics, 34, 0x443F2200);
            panel(graphics, 88, 0x44244F63);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFD675, true);
        if (title.getString().startsWith("Kit ")) {
            graphics.drawString(font, "ITENS TEMPORÁRIOS", 10, 23, 0xFFFFB52E, true);
            graphics.drawString(font, "ITENS PERMANENTES", 10, 77, 0xFF6DDBFF, true);
        } else {
            graphics.drawString(font, "ARQUIVO PROTEGIDO", 61, 116, 0xFFFFC84A, true);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE8D7B5, false);
    }
}
