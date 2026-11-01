package fr.runevestiges.client;

import fr.runevestiges.content.ModGuide;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VestigeGuideScreen extends Screen {
   private static final int ENCHANTMENT_START_PAGE = 5;
   private static final int BOOK_PAGE_WIDTH = 400;
   private final List<Component> pages;
   private int pageIndex;
   private int panelX;
   private int panelY;
   private int panelWidth;
   private int panelHeight;
   private int scrollOffset;
   private int maxScrollOffset;

   public VestigeGuideScreen(List<Component> pages) {
      super(Component.translatable("screen.runevestiges.guide.title"));
      this.pages = pages.isEmpty() ? List.of(Component.literal("The grimoire is empty.")) : List.copyOf(pages);
   }

   protected void init() {
      this.panelWidth = Math.min(836, this.width - 24);
      this.panelHeight = Math.min(460, this.height - 24);
      this.panelX = (this.width - this.panelWidth) / 2;
      this.panelY = (this.height - this.panelHeight) / 2;
      this.pageIndex = Math.min(this.pageIndex, Math.max(0, this.pages.size() - 1));
      this.addRenderableWidget(
         Button.builder(Component.literal("<"), button -> this.previousSpread()).bounds(this.panelX + 12, this.panelY + this.panelHeight - 34, 28, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal(">"), button -> this.nextSpread())
            .bounds(this.panelX + this.panelWidth - 40, this.panelY + this.panelHeight - 34, 28, 20)
            .build()
      );
   }

   private void previousSpread() {
      this.pageIndex = this.pageIndex <= 4 ? Math.max(0, this.pageIndex - 2) : this.pageIndex - 1;
      this.scrollOffset = 0;
   }

   private void nextSpread() {
      this.pageIndex = this.pageIndex < 4 ? Math.min(4, this.pageIndex + 2) : Math.min(this.pages.size() - 1, this.pageIndex + 1);
      this.scrollOffset = 0;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      this.maxScrollOffset = 0;
      graphics.fill(0, 0, this.width, this.height, -1341124584);
      graphics.fill(this.panelX - 5, this.panelY - 5, this.panelX + this.panelWidth + 5, this.panelY + this.panelHeight + 5, -15265253);
      graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, -12900056);
      int pageTop = this.panelY + 8;
      int pageBottom = this.panelY + this.panelHeight - 8;
      int spineX = this.panelX + this.panelWidth / 2;
      boolean widePage = this.pageIndex >= 4;
      if (widePage) {
         graphics.fill(this.panelX + 7, pageTop, this.panelX + this.panelWidth - 7, pageBottom, -2564896);
         graphics.fill(this.panelX + 12, pageTop + 4, this.panelX + this.panelWidth - 12, pageBottom - 4, -1511952);
      } else {
         graphics.fill(this.panelX + 7, pageTop, spineX - 2, pageBottom, -2564896);
         graphics.fill(spineX + 2, pageTop, this.panelX + this.panelWidth - 7, pageBottom, -2564896);
         graphics.fill(this.panelX + 12, pageTop + 4, spineX - 6, pageBottom - 4, -1511952);
         graphics.fill(spineX + 6, pageTop + 4, this.panelX + this.panelWidth - 12, pageBottom - 4, -1511952);
         graphics.fill(spineX - 2, pageTop, spineX + 2, pageBottom, -10792627);
         graphics.fill(spineX - 1, pageTop, spineX + 1, pageBottom, -5855327);
      }

      int contentTop = this.panelY + 20;
      int contentHeight = this.panelHeight - 54;
      if (widePage) {
         int pageX = this.panelX + 18;
         int pageWidth = this.panelWidth - 36;
         this.renderPage(graphics, pageX, contentTop, pageWidth, contentHeight, this.pageIndex);
         this.renderPageNumber(graphics, pageX, pageWidth, this.pageIndex);
      } else {
         int leftX = this.panelX + 18;
         int rightX = spineX + 6;
         int pageWidth = (this.panelWidth - 36) / 2 - 4;
         this.renderPage(graphics, leftX, contentTop, pageWidth, contentHeight, this.pageIndex);
         if (this.pageIndex + 1 < this.pages.size()) {
            this.renderPage(graphics, rightX, contentTop, pageWidth, contentHeight, this.pageIndex + 1);
         }

         this.renderPageNumber(graphics, leftX, pageWidth, this.pageIndex);
         if (this.pageIndex + 1 < this.pages.size()) {
            this.renderPageNumber(graphics, rightX, pageWidth, this.pageIndex + 1);
         }
      }

      if (this.maxScrollOffset > 0) {
         this.drawCentered(
            graphics, Component.literal("Use mouse wheel to read more"), this.panelX, this.panelWidth, this.panelY + this.panelHeight - 45, -10324858
         );
      }

      this.scrollOffset = Math.min(this.scrollOffset, this.maxScrollOffset);
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
   }

   private void renderPage(GuiGraphicsExtractor graphics, int pageX, int pageTop, int pageWidth, int pageHeight, int selectedPage) {
      int enchantmentIndex = selectedPage - 5;
      if (enchantmentIndex >= 0 && enchantmentIndex < ModGuide.enchantmentCount()) {
         this.renderEnchantmentPage(graphics, pageX, pageTop, pageWidth, pageHeight, enchantmentIndex);
      } else {
         List<String> lines = this.pages.get(selectedPage).getString().lines().toList();
         String title = lines.isEmpty() ? "" : lines.get(0);
         List<FormattedCharSequence> titleLines = this.wrapText(title, pageWidth - 28);
         int titleY = pageTop + 4;

         for (FormattedCharSequence line : titleLines) {
            graphics.text(this.font, line, pageX + (pageWidth - this.font.width(line)) / 2, titleY, -14271147, false);
            titleY += 11;
         }

         int dividerY = titleY + 2;
         graphics.fill(pageX + 12, dividerY, pageX + pageWidth - 12, dividerY + 2, -6641488);
         this.renderScrollableText(
            graphics,
            String.join("\n", lines.subList(Math.min(1, lines.size()), lines.size())),
            pageX + 16,
            dividerY + 10,
            pageWidth - 32,
            pageTop + pageHeight - 30,
            -14274245
         );
      }
   }

   private void renderEnchantmentPage(GuiGraphicsExtractor graphics, int pageX, int pageTop, int pageWidth, int pageHeight, int enchantmentIndex) {
      this.drawCentered(
         graphics,
         Component.literal(String.format("ENCHANTMENT %02d / %02d", enchantmentIndex + 1, ModGuide.enchantmentCount())),
         pageX,
         pageWidth,
         pageTop + 2,
         -10324858
      );
      this.drawCentered(
         graphics,
         Component.literal(ModGuide.enchantmentName(enchantmentIndex) + "  (I - " + this.toRoman(ModGuide.enchantmentMaxLevel(enchantmentIndex)) + ")"),
         pageX,
         pageWidth,
         pageTop + 15,
         -14271147
      );
      graphics.fill(pageX + 12, pageTop + 29, pageX + pageWidth - 12, pageTop + 31, -6641488);
      int iconY = pageTop + pageHeight - 26;
      List<FormattedCharSequence> compatibilityLines = this.wrapText("Compatible with: " + ModGuide.enchantmentCompatibility(enchantmentIndex), pageWidth - 28);
      int compatibilityY = iconY - compatibilityLines.size() * 11 - 6;

      for (int line = 0; line < compatibilityLines.size(); line++) {
         graphics.text(this.font, compatibilityLines.get(line), pageX + 14, compatibilityY + line * 11, -14274245, false);
      }

      List<ItemStack> examples = this.compatibleItems(enchantmentIndex);
      int iconStartX = pageX + pageWidth / 2 - examples.size() * 12;

      for (int index = 0; index < examples.size(); index++) {
         graphics.item(examples.get(index), iconStartX + index * 24, iconY);
      }

      this.renderScrollableText(
         graphics,
         "Effects by level:\n" + ModGuide.enchantmentDescription(enchantmentIndex),
         pageX + 14,
         pageTop + 39,
         pageWidth - 28,
         compatibilityY - 8,
         -14274245
      );
   }

   private List<ItemStack> compatibleItems(int index) {
      return switch (index) {
         case 0, 2, 8, 26, 27 -> List.of(new ItemStack(Items.DIAMOND_CHESTPLATE));
         case 1 -> List.of(new ItemStack(Items.IRON_SHOVEL));
         case 3, 4 -> List.of(new ItemStack(Items.DIAMOND_SWORD));
         case 5 -> List.of(new ItemStack(Items.FISHING_ROD));
         case 6 -> List.of(new ItemStack(Items.CROSSBOW));
         case 7, 22 -> List.of(new ItemStack(Items.IRON_HORSE_ARMOR));
         case 9 -> List.of(new ItemStack(Items.SHEARS));
         case 10 -> List.of(new ItemStack(Items.DIAMOND_AXE));
         case 11, 28 -> List.of(new ItemStack(Items.ELYTRA));
         case 12 -> List.of(new ItemStack(Items.BOW));
         case 13 -> List.of(new ItemStack(Items.DIAMOND_HOE));
         case 14, 15 -> List.of(new ItemStack(Items.DIAMOND_HELMET));
         case 16, 17, 19, 21 -> List.of(new ItemStack(Items.DIAMOND_PICKAXE));
         case 18 -> List.of(new ItemStack(Items.SHIELD));
         case 20 -> List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_SWORD));
         case 23 -> List.of(new ItemStack(Items.DIAMOND_LEGGINGS));
         case 24 -> List.of(new ItemStack(Items.TRIDENT));
         case 25 -> List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.IRON_SHOVEL));
         case 29, 30 -> List.of(new ItemStack(Items.DIAMOND_BOOTS));
         case 31 -> List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_AXE));
         default -> List.of();
      };
   }

   private String toRoman(int level) {
      return switch (level) {
         case 2 -> "II";
         case 3 -> "III";
         case 4 -> "IV";
         case 5 -> "V";
         default -> "I";
      };
   }

   private List<FormattedCharSequence> wrapText(String text, int maxWidth) {
      List<FormattedCharSequence> lines = new ArrayList<>();

      for (String paragraph : text.split("\\R", -1)) {
         if (paragraph.isBlank()) {
            lines.add(Component.empty().getVisualOrderText());
         } else {
            lines.addAll(this.font.split(Component.literal(paragraph), maxWidth));
         }
      }

      return lines;
   }

   private void renderScrollableText(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int bottom, int color) {
      List<FormattedCharSequence> lines = this.wrapText(text, maxWidth);
      int visibleLineCount = Math.max(1, (bottom - y + 10) / 11);
      this.maxScrollOffset = Math.max(this.maxScrollOffset, lines.size() - visibleLineCount);
      graphics.enableScissor(x, y, x + maxWidth, bottom);

      for (int lineIndex = this.scrollOffset; lineIndex < lines.size(); lineIndex++) {
         int lineY = y + (lineIndex - this.scrollOffset) * 11;
         if (lineY >= bottom) {
            break;
         }

         graphics.text(this.font, lines.get(lineIndex), x, lineY, color, false);
      }

      graphics.disableScissor();
   }

   private void renderPageNumber(GuiGraphicsExtractor graphics, int pageX, int pageWidth, int selectedPage) {
      this.drawCentered(
         graphics,
         Component.literal("Page " + (selectedPage + 1) + " of " + this.pages.size()),
         pageX,
         pageWidth,
         this.panelY + this.panelHeight - 24,
         -13284512
      );
   }

   private void drawCentered(GuiGraphicsExtractor graphics, Component text, int pageX, int pageWidth, int y, int color) {
      FormattedCharSequence visualText = text.getVisualOrderText();
      graphics.text(this.font, visualText, pageX + (pageWidth - this.font.width(visualText)) / 2, y, color, false);
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
   }

   public boolean keyPressed(KeyEvent event) {
      if (event.key() == 263) {
         this.previousSpread();
         return true;
      } else if (event.key() == 262) {
         this.nextSpread();
         return true;
      } else {
         return super.keyPressed(event);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (scrollY != 0.0
         && this.maxScrollOffset != 0
         && !(mouseX < this.panelX)
         && !(mouseX >= this.panelX + this.panelWidth)
         && !(mouseY < this.panelY)
         && !(mouseY >= this.panelY + this.panelHeight)) {
         int direction = scrollY < 0.0 ? 1 : -1;
         this.scrollOffset = Math.clamp((long)(this.scrollOffset + direction), 0, this.maxScrollOffset);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }
}
