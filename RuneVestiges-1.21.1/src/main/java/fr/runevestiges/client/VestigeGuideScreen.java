package fr.runevestiges.client;

import fr.runevestiges.content.ModGuide;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.FormattedCharSequence;

public final class VestigeGuideScreen extends Screen {
	private static final int ENCHANTMENT_START_PAGE = 5;
	private static final int BOOK_PAGE_WIDTH = 320;
	private final List<Component> pages;
	private int pageIndex;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	public VestigeGuideScreen(List<Component> pages) {
		super(Component.translatable("screen.runevestiges.guide.title"));
		this.pages = pages.isEmpty()
			? List.of(Component.literal("The grimoire is empty."))
			: List.copyOf(pages);
	}

	@Override
	protected void init() {
		panelWidth = Math.min(BOOK_PAGE_WIDTH * 2 + 36, width - 24);
		panelHeight = Math.min(420, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		pageIndex = Math.min(pageIndex, Math.max(0, pages.size() - 1));

		addRenderableWidget(Button.builder(Component.literal("<"), button -> previousSpread())
			.bounds(panelX + panelWidth / 2 - 38, panelY + panelHeight - 34, 28, 20)
			.build());
		addRenderableWidget(Button.builder(Component.literal(">"), button -> nextSpread())
			.bounds(panelX + panelWidth / 2 + 10, panelY + panelHeight - 34, 28, 20)
			.build());
	}

	private void previousSpread() {
		pageIndex = Math.max(0, pageIndex - 2);
	}

	private void nextSpread() {
		pageIndex = Math.min(Math.max(0, pages.size() - 1), pageIndex + 2);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xb0101018);

		graphics.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight + 5, 0xff17121b);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xff3b2928);

		int pageTop = panelY + 8;
		int pageBottom = panelY + panelHeight - 8;
		int spineX = panelX + panelWidth / 2;
		graphics.fill(panelX + 7, pageTop, spineX - 2, pageBottom, 0xffd8dce0);
		graphics.fill(spineX + 2, pageTop, panelX + panelWidth - 7, pageBottom, 0xffd8dce0);
		graphics.fill(panelX + 12, pageTop + 4, spineX - 6, pageBottom - 4, 0xffe8edf0);
		graphics.fill(spineX + 6, pageTop + 4, panelX + panelWidth - 12, pageBottom - 4, 0xffe8edf0);
		graphics.fill(spineX - 2, pageTop, spineX + 2, pageBottom, 0xff5b514d);
		graphics.fill(spineX - 1, pageTop, spineX + 1, pageBottom, 0xffa6a7a1);

		int leftX = panelX + 18;
		int rightX = spineX + 6;
		int contentTop = panelY + 20;
		int contentHeight = panelHeight - 54;
		int pageWidth = (panelWidth - 36) / 2 - 4;
		renderPage(graphics, leftX, contentTop, pageWidth, contentHeight, pageIndex);
		if (pageIndex + 1 < pages.size()) {
			renderPage(graphics, rightX, contentTop, pageWidth, contentHeight, pageIndex + 1);
		}

		renderPageNumber(graphics, leftX, pageWidth, pageIndex);
		if (pageIndex + 1 < pages.size()) {
			renderPageNumber(graphics, rightX, pageWidth, pageIndex + 1);
		}

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderPage(
			GuiGraphics graphics,
			int pageX,
			int pageTop,
			int pageWidth,
			int pageHeight,
			int selectedPage
	) {
		int enchantmentIndex = selectedPage - ENCHANTMENT_START_PAGE;
		if (enchantmentIndex >= 0 && enchantmentIndex < ModGuide.enchantmentCount()) {
			renderEnchantmentPage(graphics, pageX, pageTop, pageWidth, pageHeight, enchantmentIndex);
			return;
		}

		List<String> lines = pages.get(selectedPage).getString().lines().toList();
		String title = lines.isEmpty() ? "" : lines.get(0);
		drawCentered(graphics, Component.literal(title), pageX, pageWidth, pageTop + 6, 0xff263d55);
		graphics.fill(pageX + 12, pageTop + 24, pageX + pageWidth - 12, pageTop + 26, 0xff9aa8b0);
		renderWrappedText(
			graphics,
			String.join("\n", lines.subList(Math.min(1, lines.size()), lines.size())),
			pageX + 16,
			pageTop + 38,
			pageWidth - 32,
			Math.max(1, (pageHeight - 54) / 11),
			0xff26313b
		);
	}

	private void renderEnchantmentPage(
			GuiGraphics graphics,
			int pageX,
			int pageTop,
			int pageWidth,
			int pageHeight,
			int enchantmentIndex
	) {
		int centerX = pageX + pageWidth / 2;
		int maxLevel = ModGuide.enchantmentMaxLevel(enchantmentIndex);
		int contentBottom = pageTop + pageHeight - 8;
		boolean compact = pageHeight < 260;

		drawCentered(
			graphics,
			Component.literal(String.format("ENCHANTMENT %02d / %02d", enchantmentIndex + 1, ModGuide.enchantmentCount())),
			pageX,
			pageWidth,
			pageTop + 3,
			0xff627486
		);
		drawCentered(
			graphics,
			Component.literal("I - " + toRoman(maxLevel)),
			pageX,
			pageWidth,
			pageTop + 18,
			0xff627486
		);

		int effectTextY;
		if (compact) {
			drawCentered(
				graphics,
				Component.literal(ModGuide.enchantmentName(enchantmentIndex)),
				pageX,
				pageWidth,
				pageTop + 31,
				0xff263d55
			);
			graphics.fill(pageX + 18, pageTop + 44, pageX + pageWidth - 18, pageTop + 46, 0xff9aa8b0);
			graphics.drawString(font, "Effect", pageX + 14, pageTop + 52, 0xff52677b, false);
			effectTextY = pageTop + 66;
		} else {
			int itemY = pageTop + 30;
			graphics.renderItem(new ItemStack(Items.ENCHANTED_BOOK), centerX - 8, itemY);
			drawCentered(
				graphics,
				Component.literal(ModGuide.enchantmentName(enchantmentIndex)),
				pageX,
				pageWidth,
				itemY + 28,
				0xff263d55
			);
			graphics.fill(pageX + 18, itemY + 43, pageX + pageWidth - 18, itemY + 45, 0xff9aa8b0);
			graphics.drawString(font, "Effect", pageX + 14, itemY + 53, 0xff52677b, false);
			effectTextY = itemY + 67;
		}
		int compatibilitySpace = compact ? 64 : 68;
		int maxEffectLines = Math.max(1, (contentBottom - effectTextY - compatibilitySpace) / 11);
		int effectEndY = renderWrappedText(
			graphics,
			ModGuide.enchantmentDescription(enchantmentIndex),
			pageX + 14,
			effectTextY,
			pageWidth - 28,
			maxEffectLines,
			0xff26313b
		);
		int compatibleY = effectEndY + 8;
		graphics.fill(pageX + 18, compatibleY - 7, pageX + pageWidth - 18, compatibleY - 5, 0xff9aa8b0);
		graphics.drawString(font, "Compatible with", pageX + 14, compatibleY, 0xff52677b, false);
		List<ItemStack> examples = compatibleItems(enchantmentIndex);
		int compatibilityEndY = renderWrappedText(
			graphics,
			ModGuide.enchantmentCompatibility(enchantmentIndex),
			pageX + 14,
			compatibleY + 14,
			pageWidth - 28,
			2,
			0xff26313b
		);
		int iconStartX = centerX - examples.size() * 12;
		for (int index = 0; index < examples.size(); index++) {
			graphics.renderItem(examples.get(index), iconStartX + index * 24, compatibilityEndY + 2);
		}
	}

	private List<ItemStack> compatibleItems(int index) {
		return switch (index) {
			case 0, 2, 8, 25, 26 -> List.of(new ItemStack(Items.DIAMOND_CHESTPLATE));
			case 1 -> List.of(new ItemStack(Items.IRON_SHOVEL));
			case 3, 4 -> List.of(new ItemStack(Items.DIAMOND_SWORD));
			case 5 -> List.of(new ItemStack(Items.FISHING_ROD));
			case 6 -> List.of(new ItemStack(Items.CROSSBOW));
			case 7, 21 -> List.of(new ItemStack(Items.IRON_HORSE_ARMOR));
			case 9 -> List.of(new ItemStack(Items.SHEARS));
			case 10, 27 -> List.of(new ItemStack(Items.ELYTRA));
			case 11 -> List.of(new ItemStack(Items.BOW));
			case 12 -> List.of(new ItemStack(Items.DIAMOND_HOE));
			case 13 -> List.of(new ItemStack(Items.DIAMOND_HELMET));
			case 17 -> List.of(new ItemStack(Items.SHIELD));
			case 19 -> List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_SWORD));
			case 22 -> List.of(new ItemStack(Items.DIAMOND_LEGGINGS));
			case 23 -> List.of(new ItemStack(Items.TRIDENT));
			case 24 -> List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.IRON_SHOVEL));
			case 28, 29 -> List.of(new ItemStack(Items.DIAMOND_BOOTS));
			case 30 -> List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_AXE));
			default -> List.of(new ItemStack(Items.DIAMOND_PICKAXE));
		};
	}

	private String toRoman(int level) {
		return switch (level) {
			case 5 -> "V";
			case 4 -> "IV";
			case 3 -> "III";
			case 2 -> "II";
			default -> "I";
		};
	}

	private int renderWrappedText(
			GuiGraphics graphics,
			String text,
			int x,
			int y,
			int maxWidth,
			int maxLines,
			int color
	) {
		int lineY = y;
		int lineCount = 0;
		for (String paragraph : text.split("\\R", -1)) {
			if (paragraph.isBlank()) {
				lineY += 6;
				continue;
			}
			List<FormattedCharSequence> wrapped = font.split(Component.literal(paragraph), maxWidth);
			for (FormattedCharSequence line : wrapped) {
				if (lineCount >= maxLines) {
					graphics.drawString(font, "...", x, lineY, color, false);
					return lineY + 11;
				}
				graphics.drawString(font, line, x, lineY, color, false);
				lineY += 11;
				lineCount++;
			}
		}
		return lineY;
	}

	private void renderPageNumber(GuiGraphics graphics, int pageX, int pageWidth, int selectedPage) {
		drawCentered(
			graphics,
			Component.literal("Page " + (selectedPage + 1) + " of " + pages.size()),
			pageX,
			pageWidth,
			panelY + panelHeight - 24,
			0xff354b60
		);
	}

	private void drawCentered(
			GuiGraphics graphics,
			Component text,
			int pageX,
			int pageWidth,
			int y,
			int color
	) {
		FormattedCharSequence visualText = text.getVisualOrderText();
		graphics.drawString(font, visualText, pageX + (pageWidth - font.width(visualText)) / 2, y, color, false);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 263) {
			previousSpread();
			return true;
		}
		if (keyCode == 262) {
			nextSpread();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
