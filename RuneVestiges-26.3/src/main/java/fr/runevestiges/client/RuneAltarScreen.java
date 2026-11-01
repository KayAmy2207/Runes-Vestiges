/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.Slot
 */
package fr.runevestiges.client;

import fr.runevestiges.content.RuneAltarBlock;
import fr.runevestiges.content.RuneAltarMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

public final class RuneAltarScreen
extends AbstractContainerScreen<RuneAltarMenu> {
    private static final int PANEL = -14409686;
    private static final int INNER_PANEL = -13423818;
    private static final int TRIM = -7706054;
    private static final int TEXT = -1779523;

    public RuneAltarScreen(RuneAltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, -14409686);
        graphics.fill(left + 3, top + 3, left + this.imageWidth - 3, top + 66, -13423818);
        graphics.fill(left + 3, top + 68, left + this.imageWidth - 3, top + this.imageHeight - 3, -13423818);
        graphics.fill(left, top, left + this.imageWidth, top + 2, -7706054);
        graphics.fill(left, top + this.imageHeight - 2, left + this.imageWidth, top + this.imageHeight, -7706054);
        graphics.fill(left, top, left + 2, top + this.imageHeight, -7706054);
        graphics.fill(left + this.imageWidth - 2, top, left + this.imageWidth, top + this.imageHeight, -7706054);
        for (Slot slot : ((RuneAltarMenu)this.menu).slots) {
            graphics.fill(left + slot.x, top + slot.y, left + slot.x + 18, top + slot.y + 18, -15396326);
            graphics.fill(left + slot.x + 1, top + slot.y + 1, left + slot.x + 17, top + slot.y + 17, -12239802);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        MutableComponent title = Component.translatable((String)this.titleKey());
        this.drawCentered(graphics, (Component)title, this.imageWidth / 2, 7, -1779523);
        RuneAltarBlock.Operation operation = ((RuneAltarMenu)this.menu).getOperation();
        MutableComponent firstInput = switch (operation) {
            default -> throw new MatchException(null, null);
            case RuneAltarBlock.Operation.ENCHANT -> Component.translatable((String)"screen.runevestiges.altar.enchanted_book");
            case RuneAltarBlock.Operation.DISENCHANT -> Component.translatable((String)"screen.runevestiges.altar.item");
            case RuneAltarBlock.Operation.COMBINE -> Component.translatable((String)"screen.runevestiges.altar.first_book");
        };
        MutableComponent secondInput = switch (operation) {
            default -> throw new MatchException(null, null);
            case RuneAltarBlock.Operation.ENCHANT -> Component.translatable((String)"screen.runevestiges.altar.item");
            case RuneAltarBlock.Operation.DISENCHANT -> Component.translatable((String)"screen.runevestiges.altar.plain_book");
            case RuneAltarBlock.Operation.COMBINE -> Component.translatable((String)"screen.runevestiges.altar.second_book");
        };
        this.drawCentered(graphics, (Component)firstInput, 34, 23, -1779523);
        this.drawCentered(graphics, (Component)secondInput, 70, 23, -1779523);
        this.drawCentered(graphics, (Component)Component.translatable((String)"screen.runevestiges.altar.runic_shard"), 106, 23, -1779523);
        this.drawCentered(graphics, (Component)Component.translatable((String)"screen.runevestiges.altar.result"), 142, 23, -1779523);
        graphics.text(this.font, "\u00bb", 118, 39, -1779523);
        this.drawCentered(graphics, (Component)Component.translatable((String)this.costKey()), this.imageWidth / 2, 57, -1779523);
        graphics.text(this.font, (Component)Component.translatable((String)"container.inventory"), 8, 73, -1779523);
    }

    private String titleKey() {
        return switch (((RuneAltarMenu)this.menu).getOperation()) {
            default -> throw new MatchException(null, null);
            case RuneAltarBlock.Operation.ENCHANT -> "screen.runevestiges.altar.enchanter";
            case RuneAltarBlock.Operation.DISENCHANT -> "screen.runevestiges.altar.disenchanter";
            case RuneAltarBlock.Operation.COMBINE -> "screen.runevestiges.altar.combining";
        };
    }

    private String costKey() {
        return switch (((RuneAltarMenu)this.menu).getOperation()) {
            default -> throw new MatchException(null, null);
            case RuneAltarBlock.Operation.ENCHANT -> "screen.runevestiges.altar.cost_enchant";
            case RuneAltarBlock.Operation.DISENCHANT -> "screen.runevestiges.altar.cost_disenchant";
            case RuneAltarBlock.Operation.COMBINE -> "screen.runevestiges.altar.cost_combine";
        };
    }

    private void drawCentered(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int color) {
        graphics.text(this.font, text, centerX - this.font.width((FormattedText)text) / 2, y, color);
    }
}
