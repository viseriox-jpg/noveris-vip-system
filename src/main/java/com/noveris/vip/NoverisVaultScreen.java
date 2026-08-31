package com.noveris.vip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

final class NoverisVaultScreen extends AbstractContainerScreen<NoverisVaultMenu> {
    private static final int PANEL_WIDTH = 192;
    private static final int PANEL_HEIGHT = 157;
    private static final int VIP_GOLD = 0xFFE1B54F;
    private static final int LORE_GOLD = 0xFFC88745;
    private static final int MUTED = 0xFF99978F;
    private final boolean loreVault;
    private long openedAt;

    NoverisVaultScreen(NoverisVaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        loreVault = title.getString().toLowerCase(Locale.ROOT).contains("relíquia");
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
        drawMetalTexture(graphics, x + 3, y + 3, imageWidth - 6, imageHeight - 6);
        graphics.fill(x + NoverisVaultMenu.VAULT_X - 2, y + 30,
                x + imageWidth - 14, y + 31, loreVault ? 0xFF5D332B : 0xFF4B4435);
        graphics.fill(x + NoverisVaultMenu.VAULT_X - 2, y + 127,
                x + imageWidth - 14, y + 128, 0xFF343333);

        boolean empty = isEmpty();
        for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
            Slot slot = menu.slots.get(slotIndex);
            if (empty && slotIndex < 45) continue;
            if (slot.x < 0) continue;
            boolean hovered = mouseX >= x + slot.x - 1 && mouseX < x + slot.x + 17
                    && mouseY >= y + slot.y - 1 && mouseY < y + slot.y + 17;
            drawSlotFrame(graphics, x + slot.x, y + slot.y, hovered);
        }
    }

    private void drawMetalTexture(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, loreVault ? 0xFF211D1D : 0xFF202123);
        for (int index = 0; index < 38; index++) {
            int dotX = x + 3 + (index * 47) % Math.max(4, width - 6);
            int dotY = y + 3 + (index * 29) % Math.max(4, height - 6);
            int shade = index % 3 == 0 ? 0xFF28282A : 0xFF1B1C1E;
            graphics.fill(dotX, dotY, dotX + (index % 4 == 0 ? 2 : 1), dotY + 1, shade);
        }
    }

    private void drawSlotFrame(GuiGraphics graphics, int x, int y, boolean hovered) {
        int border = hovered ? accent() : 0xFF353537;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, border);
        graphics.fill(x, y, x + 16, y + 16, 0xFF0C0D0E);
        graphics.fill(x + 1, y + 1, x + 15, y + 2, 0xFF161719);
        graphics.fill(x + 1, y + 2, x + 2, y + 15, 0xFF161719);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int titleWidth = 146;
        String fittedTitle = font.plainSubstrByWidth(title.getString(), titleWidth);
        graphics.drawString(font, fittedTitle, titleLabelX, titleLabelY, accent(), false);
        graphics.drawString(font, Component.literal(loreVault ? "RELÍQUIAS SOB CUSTÓDIA" : "ITENS SOB CUSTÓDIA"),
                NoverisVaultMenu.VAULT_X, 20, MUTED, false);

        if (isEmpty()) {
            String empty = loreVault ? "Nenhuma relíquia repousa sob custódia."
                    : "Nenhum item repousa sob custódia.";
            int maxWidth = imageWidth - 28;
            String fitted = font.plainSubstrByWidth(empty, maxWidth);
            graphics.drawString(font, fitted, (imageWidth - font.width(fitted)) / 2, 78, MUTED, false);
        }

        String page = pageText();
        graphics.drawString(font, page, (imageWidth - font.width(page)) / 2,
                NoverisVaultMenu.FOOTER_Y + 4, MUTED, false);
    }

    private boolean isEmpty() {
        for (int slot = 0; slot < 45; slot++) if (menu.getSlot(slot).hasItem()) return false;
        return true;
    }

    private String pageText() {
        ItemStack page = menu.getSlot(46).getItem();
        return page.isEmpty() ? "PÁGINA 1/1 • 0 ITENS" : page.getHoverName().getString();
    }

    private int accent() {
        return loreVault ? LORE_GOLD : VIP_GOLD;
    }
}
