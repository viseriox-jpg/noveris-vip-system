package com.noveris.vip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class NoverisVaultScreen extends AbstractContainerScreen<NoverisVaultMenu> {
    private static final int PANEL_WIDTH = 286, PANEL_HEIGHT = 188, SIDEBAR_WIDTH = 43;
    private static final int PREVIOUS = 45, PAGE = 46, NEXT = 52, CLOSE = 53;
    private static final int GOLD = 0xFFE0AD3C, TEXT = 0xFFC9C5B9, MUTED = 0xFF85837C;
    private long openedAt;

    NoverisVaultScreen(NoverisVaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        titleLabelX = 56;
        titleLabelY = 17;
    }

    @Override protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderNavigation(graphics, mouseX, mouseY);
        float opening = Math.min(1.0F, (System.currentTimeMillis() - openedAt) / 160.0F);
        if (opening < 1.0F) {
            int alpha = Math.round((1.0F - opening) * 120.0F);
            graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, alpha << 24);
        }
        renderTooltip(graphics, mouseX, mouseY);
        renderControlTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF0D0E0F);
        graphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, 0xFF38393A);
        graphics.fill(x + 2, y + 2, x + PANEL_WIDTH - 2, y + PANEL_HEIGHT - 2, 0xFF191A1C);
        drawMetalTexture(graphics, x + SIDEBAR_WIDTH, y + 3,
                PANEL_WIDTH - SIDEBAR_WIDTH - 3, PANEL_HEIGHT - 6);
        graphics.fill(x + SIDEBAR_WIDTH, y + 2, x + SIDEBAR_WIDTH + 1,
                y + PANEL_HEIGHT - 2, 0xFF3A3935);
        graphics.fill(x + 52, y + 36, x + PANEL_WIDTH - 8, y + 37, 0xFF35332D);
        graphics.fill(x + 52, y + 165, x + PANEL_WIDTH - 8, y + 166, 0xFF2E2E2D);
        renderSidebar(graphics, x, y);
        for (int index = 0; index < 45; index++) {
            Slot slot = menu.getSlot(index);
            drawSlotFrame(graphics, x + slot.x, y + slot.y);
        }
    }

    private void drawMetalTexture(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF1C1D1F);
        for (int line = 15; line < height; line += 24)
            graphics.fill(x + 1, y + line, x + width - 1, y + line + 1, 0xFF202123);
        for (int column = 26; column < width; column += 53)
            graphics.fill(x + column, y + 1, x + column + 1, y + height - 1, 0xFF191A1B);
    }

    private void renderSidebar(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 7, y + 9, x + 36, y + 42, 0xFF8A6826);
        graphics.fill(x + 8, y + 10, x + 35, y + 41, 0xFF2A271F);
        graphics.fill(x + 8, y + 10, x + 10, y + 41, GOLD);
        graphics.renderItem(new ItemStack(Items.CHEST), x + 14, y + 18);
    }

    private void drawSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 3, y - 3, x + 19, y + 19, 0xFF08090A);
        graphics.fill(x - 2, y - 2, x + 18, y + 18, 0xFF3A3B3C);
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF101113);
        graphics.fill(x, y, x + 16, y + 1, 0xFF08090A);
        graphics.fill(x, y + 1, x + 1, y + 16, 0xFF08090A);
    }

    @Override protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot.index < 45) super.renderSlot(graphics, slot);
    }

    private void renderNavigation(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = topPos + 170;
        if (menu.getSlot(PREVIOUS).getItem().is(Items.ARROW))
            drawNavigationButton(graphics, leftPos + 55, y, false,
                    hovered(mouseX, mouseY, 55, 170, 20, 13));
        String pageText = menu.getSlot(PAGE).getItem().getHoverName().getString();
        int separator = pageText.indexOf('•');
        if (separator >= 0) pageText = pageText.substring(0, separator).trim();
        graphics.drawCenteredString(font, pageText, leftPos + 166, y + 3, MUTED);
        if (menu.getSlot(NEXT).getItem().is(Items.ARROW))
            drawNavigationButton(graphics, leftPos + 240, y, true,
                    hovered(mouseX, mouseY, 240, 170, 20, 13));
        boolean closeHovered = hovered(mouseX, mouseY, 264, 168, 16, 16);
        graphics.fill(leftPos + 264, topPos + 168, leftPos + 280, topPos + 184, 0xFF0B0C0D);
        graphics.fill(leftPos + 265, topPos + 169, leftPos + 279, topPos + 183,
                closeHovered ? 0xFF7A2D2D : 0xFF4B2222);
        graphics.drawCenteredString(font, "×", leftPos + 272, topPos + 172, 0xFFE5B1A6);
    }

    private void drawNavigationButton(GuiGraphics graphics, int x, int y, boolean right, boolean over) {
        graphics.fill(x, y, x + 20, y + 13, 0xFF08090A);
        graphics.fill(x + 1, y + 1, x + 19, y + 12, over ? 0xFF303235 : 0xFF242628);
        int color = over ? GOLD : TEXT;
        int start = right ? x + 8 : x + 11;
        for (int row = 0; row < 4; row++) {
            int px = right ? start + row : start - row;
            graphics.fill(px, y + 3 + row, px + 1, y + 5 + row, color);
        }
    }

    private boolean hovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY >= topPos + y && mouseY < topPos + y + height;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hovered(mouseX, mouseY, 55, 170, 20, 13)
                    && menu.getSlot(PREVIOUS).getItem().is(Items.ARROW)) {
                slotClicked(menu.getSlot(PREVIOUS), PREVIOUS, 0, ClickType.PICKUP);
                return true;
            }
            if (hovered(mouseX, mouseY, 240, 170, 20, 13)
                    && menu.getSlot(NEXT).getItem().is(Items.ARROW)) {
                slotClicked(menu.getSlot(NEXT), NEXT, 0, ClickType.PICKUP);
                return true;
            }
            if (hovered(mouseX, mouseY, 264, 168, 16, 16)) {
                slotClicked(menu.getSlot(CLOSE), CLOSE, 0, ClickType.PICKUP);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderControlTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Component tooltip = null;
        if (hovered(mouseX, mouseY, 55, 170, 20, 13)
                && menu.getSlot(PREVIOUS).getItem().is(Items.ARROW))
            tooltip = Component.literal("Página anterior");
        else if (hovered(mouseX, mouseY, 240, 170, 20, 13)
                && menu.getSlot(NEXT).getItem().is(Items.ARROW))
            tooltip = Component.literal("Próxima página");
        else if (hovered(mouseX, mouseY, 264, 168, 16, 16))
            tooltip = Component.literal("Fechar");
        if (tooltip != null) graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int titleWidth = PANEL_WIDTH - titleLabelX - 12;
        String fittedTitle = font.plainSubstrByWidth(title.getString(), titleWidth);
        graphics.drawString(font, fittedTitle, titleLabelX, titleLabelY, GOLD, false);
    }
}
