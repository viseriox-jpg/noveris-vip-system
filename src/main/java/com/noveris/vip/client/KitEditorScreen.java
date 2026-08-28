package com.noveris.vip.client;

import com.noveris.vip.KitEditorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class KitEditorScreen extends NoverisContainerScreen<KitEditorMenu> {
    KitEditorScreen(KitEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderSections(GuiGraphics graphics) {
        panel(graphics, 34, 0x553F2200);
        panel(graphics, 88, 0x55244F63);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFD675, true);
        graphics.drawString(font, "TEMPORÁRIOS", 10, 23, 0xFFFFB52E, true);
        graphics.drawString(font, "PERMANENTES", 10, 77, 0xFF6DDBFF, true);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE8D7B5, false);
    }
}
