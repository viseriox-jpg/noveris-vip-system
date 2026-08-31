package com.noveris.vip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class NoverisVaultScreen extends AbstractContainerScreen<NoverisVaultMenu> {
    private static final int PANEL_WIDTH = 236;
    private static final int PANEL_HEIGHT = 148;
    private static final int SIDEBAR_WIDTH = 46;
    private static final int GOLD = 0xFFE1B54F;
    private static final int CONTROL_ROW_START = 45;
    private long openedAt;

    NoverisVaultScreen(NoverisVaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        titleLabelX = NoverisVaultMenu.VAULT_X;
        titleLabelY = 7;
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        float opening = Math.min(1.0F, (System.currentTimeMillis() - openedAt) / 200.0F);
        if (opening < 1.0F) {
            int alpha = Math.round((1.0F - opening) * 150.0F);
            graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, alpha << 24);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF111214);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF1B1C1E);
        graphics.fill(x + SIDEBAR_WIDTH, y + 2, x + SIDEBAR_WIDTH + 1, y + imageHeight - 2, 0xFF4A463B);
        graphics.fill(x + 3, y + 3, x + SIDEBAR_WIDTH, y + imageHeight - 3, 0xFF17181A);
        drawMetalTexture(graphics, x + SIDEBAR_WIDTH + 1, y + 3, imageWidth - SIDEBAR_WIDTH - 4, imageHeight - 6);
        graphics.fill(x + NoverisVaultMenu.VAULT_X - 3, y + 22,
                x + imageWidth - 9, y + 23, 0xFF302E2A);

        renderSidebar(graphics, x, y);
        for (Slot slot : menu.slots) {
            drawSlotFrame(graphics, x + slot.x, y + slot.y);
        }
    }

    private void drawMetalTexture(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF202123);
        for (int line = 0; line < height; line += 17) {
            int shade = (line / 17) % 2 == 0 ? 0xFF232426 : 0xFF1D1E20;
            graphics.fill(x + 1, y + line, x + width - 1, y + line + 1, shade);
        }
        for (int column = 12; column < width; column += 37) {
            graphics.fill(x + column, y + 1, x + column + 1, y + height - 1, 0xFF1A1B1C);
        }
    }

    private void drawSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF3B3B3D);
        graphics.fill(x, y, x + 16, y + 16, 0xFF0E0F10);
        graphics.fill(x + 1, y + 1, x + 16, y + 2, 0xFF080808);
        graphics.fill(x + 1, y + 2, x + 2, y + 16, 0xFF080808);
        graphics.fill(x + 15, y + 2, x + 16, y + 16, 0xFF262729);
        graphics.fill(x + 2, y + 15, x + 16, y + 16, 0xFF262729);
    }

    private void renderSidebar(GuiGraphics graphics, int x, int y) {
        int buttonX = x + 7;
        int buttonY = y + 10;
        graphics.fill(buttonX, buttonY, buttonX + 32, buttonY + 35, 0xFF8D6A22);
        graphics.fill(buttonX + 1, buttonY + 1, buttonX + 31, buttonY + 34, 0xFF302C22);
        graphics.fill(buttonX + 1, buttonY + 1, buttonX + 3, buttonY + 34, GOLD);
        graphics.renderItem(new ItemStack(Items.CHEST), buttonX + 9, buttonY + 9);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot.index < CONTROL_ROW_START) {
            super.renderSlot(graphics, slot);
            return;
        }

        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || stack.is(Items.BLACK_STAINED_GLASS_PANE) || stack.is(Items.GRAY_DYE)) {
            return;
        }
        if (stack.is(Items.ARROW)) {
            drawArrow(graphics, slot.x, slot.y, slot.index == CONTROL_ROW_START);
        } else if (stack.is(Items.PAPER)) {
            drawPage(graphics, slot.x, slot.y);
        } else if (stack.is(Items.BARRIER)) {
            drawClose(graphics, slot.x, slot.y);
        }
    }

    private void drawArrow(GuiGraphics graphics, int x, int y, boolean left) {
        int color = 0xFF777A7E;
        int shadow = 0xFF303234;
        int center = x + 8;
        graphics.fill(x + 4, y + 7, x + 12, y + 10, shadow);
        graphics.fill(x + 5, y + 6, x + 12, y + 9, color);
        for (int row = 0; row < 3; row++) {
            int px = left ? center - row - 3 : center + row + 1;
            graphics.fill(px, y + 5 - row, px + 2, y + 11 + row, shadow);
            graphics.fill(px, y + 4 - row, px + 1, y + 10 + row, color);
        }
    }

    private void drawPage(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 4, y + 3, x + 12, y + 14, 0xFF313235);
        graphics.fill(x + 5, y + 3, x + 13, y + 13, 0xFF77746C);
        graphics.fill(x + 7, y + 6, x + 11, y + 7, 0xFF292A2C);
        graphics.fill(x + 7, y + 9, x + 11, y + 10, 0xFF292A2C);
    }

    private void drawClose(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 3, y + 3, x + 13, y + 13, 0xFF3A1717);
        for (int offset = 0; offset < 3; offset++) {
            graphics.fill(x + 4 + offset, y + 4 + offset, x + 6 + offset, y + 6 + offset, 0xFF9D3434);
            graphics.fill(x + 10 - offset, y + 4 + offset, x + 12 - offset, y + 6 + offset, 0xFF9D3434);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int titleWidth = imageWidth - titleLabelX - 10;
        String fittedTitle = font.plainSubstrByWidth(title.getString(), titleWidth);
        graphics.drawString(font, fittedTitle, titleLabelX, titleLabelY, GOLD, false);
    }
}
