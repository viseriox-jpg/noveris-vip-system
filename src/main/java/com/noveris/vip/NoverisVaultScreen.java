package com.noveris.vip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

final class NoverisVaultScreen extends AbstractContainerScreen<NoverisVaultMenu> {
    private static final int PANEL_WIDTH = 286, PANEL_HEIGHT = 210, SIDEBAR_WIDTH = 43;
    private static final int PREVIOUS = 45, PAGE = 46, TAB_VIP = 47, TAB_LORE = 48, NEXT = 52, CLOSE = 53;
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
        graphics.fill(x + SIDEBAR_WIDTH + 1, y + 3,
                x + PANEL_WIDTH - 3, y + PANEL_HEIGHT - 3, 0xFF1C1D1F);
        graphics.fill(x + SIDEBAR_WIDTH, y + 2, x + SIDEBAR_WIDTH + 1,
                y + PANEL_HEIGHT - 2, 0xFF3A3935);
        graphics.fill(x + 52, y + 36, x + PANEL_WIDTH - 8, y + 37, 0xFF35332D);
        graphics.fill(x + 52, y + 165, x + PANEL_WIDTH - 8, y + 166, 0xFF2E2E2D);
        graphics.fill(x + 52, y + 188, x + PANEL_WIDTH - 8, y + 189, 0xFF2E2E2D);
        renderSidebar(graphics, x, y);
        for (int index = 0; index < 45; index++) {
            Slot slot = menu.getSlot(index);
            drawSlotFrame(graphics, x + slot.x, y + slot.y);
        }
    }

    private void renderSidebar(GuiGraphics graphics, int x, int y) {
        boolean vipActive = menu.getSlot(TAB_VIP).getItem().is(Items.GOLD_BLOCK);
        boolean loreActive = menu.getSlot(TAB_LORE).getItem().is(Items.AMETHYST_BLOCK);
        drawTab(graphics, x + 7, y + 9, vipActive, false);
        drawTab(graphics, x + 7, y + 44, loreActive, true);
    }

    private void drawTab(GuiGraphics graphics, int x, int y, boolean active, boolean lore) {
        int accent = lore ? 0xFF9B6CB7 : GOLD;
        if (active) {
            graphics.fill(x, y, x + 29, y + 30, 0xFF765A24);
            graphics.fill(x + 1, y + 1, x + 28, y + 29, 0xFF292720);
            graphics.fill(x + 1, y + 1, x + 3, y + 29, accent);
        }
        int color = active ? accent : 0xFF777872;
        if (lore) drawHourglass(graphics, x + 10, y + 8, color);
        else drawChest(graphics, x + 9, y + 9, color);
    }

    private void drawChest(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 1, y + 3, x + 11, y + 4, color);
        graphics.fill(x, y + 4, x + 12, y + 6, color);
        graphics.fill(x + 1, y + 6, x + 11, y + 13, color);
        graphics.fill(x + 2, y + 7, x + 10, y + 12, 0xFF242321);
        graphics.fill(x + 5, y + 6, x + 7, y + 9, color);
    }

    private void drawHourglass(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 10, y + 2, color);
        graphics.fill(x, y + 12, x + 10, y + 14, color);
        for (int row = 0; row < 5; row++) {
            graphics.fill(x + 1 + row, y + 2 + row, x + 3 + row, y + 3 + row, color);
            graphics.fill(x + 7 - row, y + 2 + row, x + 9 - row, y + 3 + row, color);
            graphics.fill(x + 4 - row, y + 7 + row, x + 6 - row, y + 8 + row, color);
            graphics.fill(x + 4 + row, y + 7 + row, x + 6 + row, y + 8 + row, color);
        }
    }

    private void drawSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 2, y - 2, x + 18, y + 18, 0xFF454648);
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF111214);
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
        boolean closeHovered = hovered(mouseX, mouseY, 230, 192, 48, 14);
        graphics.fill(leftPos + 230, topPos + 192, leftPos + 278, topPos + 206, 0xFF494A48);
        graphics.fill(leftPos + 231, topPos + 193, leftPos + 277, topPos + 205,
                closeHovered ? 0xFF303133 : 0xFF202123);
        graphics.drawCenteredString(font, "FECHAR", leftPos + 254, topPos + 195,
                closeHovered ? GOLD : TEXT);
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
            if (hovered(mouseX, mouseY, 7, 9, 29, 30)
                    && !menu.getSlot(TAB_VIP).getItem().is(Items.GOLD_BLOCK)) {
                slotClicked(menu.getSlot(TAB_VIP), TAB_VIP, 0, ClickType.PICKUP);
                return true;
            }
            if (hovered(mouseX, mouseY, 7, 44, 29, 30)
                    && !menu.getSlot(TAB_LORE).getItem().is(Items.AMETHYST_BLOCK)) {
                slotClicked(menu.getSlot(TAB_LORE), TAB_LORE, 0, ClickType.PICKUP);
                return true;
            }
            if (hovered(mouseX, mouseY, 230, 192, 48, 14)) {
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
        else if (hovered(mouseX, mouseY, 7, 9, 29, 30))
            tooltip = Component.literal("Cofre VIP");
        else if (hovered(mouseX, mouseY, 7, 44, 29, 30))
            tooltip = Component.literal("Cofre de relíquias");
        else if (hovered(mouseX, mouseY, 230, 192, 48, 14))
            tooltip = Component.literal("Fechar");
        if (tooltip != null) graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int titleWidth = 183;
        String fittedTitle = font.plainSubstrByWidth(title.getString(), titleWidth);
        graphics.drawString(font, fittedTitle, titleLabelX, titleLabelY, GOLD, false);
        graphics.drawString(font, "Todos", 251, titleLabelY, MUTED, false);
    }
}
