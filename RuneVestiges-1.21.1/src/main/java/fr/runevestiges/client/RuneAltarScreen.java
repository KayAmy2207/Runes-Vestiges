package fr.runevestiges.client;

import fr.runevestiges.content.RuneAltarBlock;
import fr.runevestiges.content.RuneAltarMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class RuneAltarScreen extends AbstractContainerScreen<RuneAltarMenu> {
	private static final int PANEL = 0xFF24202A;
	private static final int INNER_PANEL = 0xFF332B36;
	private static final int TRIM = 0xFF8A6A3A;
	private static final int TEXT = 0xFFE4D8BD;
	private static final ResourceLocation SLOT_SPRITE =
		ResourceLocation.withDefaultNamespace("container/slot");

	public RuneAltarScreen(RuneAltarMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = 194;
		imageHeight = 166;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		graphics.fill(left, top, left + imageWidth, top + imageHeight, PANEL);
		graphics.fill(left + 3, top + 3, left + imageWidth - 3, top + 66, INNER_PANEL);
		graphics.fill(left + 3, top + 68, left + imageWidth - 3, top + imageHeight - 3, INNER_PANEL);
		graphics.fill(left, top, left + imageWidth, top + 2, TRIM);
		graphics.fill(left, top + imageHeight - 2, left + imageWidth, top + imageHeight, TRIM);
		graphics.fill(left, top, left + 2, top + imageHeight, TRIM);
		graphics.fill(left + imageWidth - 2, top, left + imageWidth, top + imageHeight, TRIM);

		for (var slot : menu.slots) {
			graphics.blitSprite(SLOT_SPRITE, left + slot.x, top + slot.y, 18, 18);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		Component title = Component.translatable(titleKey());
		drawCentered(graphics, title, imageWidth / 2, 7, TEXT);

		RuneAltarBlock.Operation operation = menu.getOperation();
		Component firstInput = switch (operation) {
			case ENCHANT -> Component.translatable("screen.runevestiges.altar.enchanted_book");
			case DISENCHANT -> Component.translatable("screen.runevestiges.altar.item");
			case COMBINE -> Component.translatable("screen.runevestiges.altar.first_book");
		};
		Component secondInput = switch (operation) {
			case ENCHANT -> Component.translatable("screen.runevestiges.altar.item");
			case DISENCHANT -> Component.translatable("screen.runevestiges.altar.plain_book");
			case COMBINE -> Component.translatable("screen.runevestiges.altar.second_book");
		};

		drawCentered(graphics, firstInput, 34, 23, TEXT);
		drawCentered(graphics, secondInput, 70, 23, TEXT);
		drawCentered(graphics, Component.translatable("screen.runevestiges.altar.runic_shard"), 107, 23, TEXT);
		drawCentered(graphics, Component.translatable("screen.runevestiges.altar.result"), 161, 23, TEXT);
		graphics.drawString(font, "»", 132, 39, TEXT, false);
		drawCentered(graphics, Component.translatable(costKey()), imageWidth / 2, 57, TEXT);
		graphics.drawString(
			font,
			Component.translatable("container.inventory"),
			16,
			73,
			TEXT,
			false
		);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	private String titleKey() {
		return switch (menu.getOperation()) {
			case ENCHANT -> "screen.runevestiges.altar.enchanter";
			case DISENCHANT -> "screen.runevestiges.altar.disenchanter";
			case COMBINE -> "screen.runevestiges.altar.combining";
		};
	}

	private String costKey() {
		return switch (menu.getOperation()) {
			case ENCHANT -> "screen.runevestiges.altar.cost_enchant";
			case DISENCHANT -> "screen.runevestiges.altar.cost_disenchant";
			case COMBINE -> "screen.runevestiges.altar.cost_combine";
		};
	}

	private void drawCentered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
		graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
	}
}
