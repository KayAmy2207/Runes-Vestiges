package fr.runevestiges.client;

import fr.runevestiges.economy.EconomyPayloads;
import fr.runevestiges.economy.ShopPricing;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class EconomyScreen extends Screen {
   private static final int PANEL_WIDTH = 420;
   private static final int PANEL_HEIGHT = 400;
   private static final int NAVIGATION_Y = 34;
   private static final int BALANCE_Y = 65;
   private static final int MARKET_CONTROL_Y = 96;
   private static final int INVENTORY_LABEL_Y = 128;
   private static final int INVENTORY_GRID_Y = 146;
   private static final int INVENTORY_SLOT_SIZE = 18;
   private static final int INVENTORY_SLOT_SPACING = 20;
   private static final int INVENTORY_COLUMNS = 9;
   private static final int INVENTORY_SLOT_COUNT = 36;
   private static final int MARKET_VIEW_CREATE = 2;
   private static final int MARKET_ROW_HEIGHT = 38;
   private static EconomyScreen current;
   private int panelX;
   private int panelY;
   private int panelWidth;
   private int panelHeight;
   private int tab;
   private EconomyPayloads.Snapshot snapshot;
   private Component localStatus;
   private EditBox recipientField;
   private EditBox transferAmountField;
   private EditBox listingPriceField;
   private EditBox offerAmountField;
   private Button listSelectedButton;
   private Button submitOfferButton;
   private String recipientValue = "";
   private String transferAmountValue = "";
   private String listingPriceValue = "";
   private String offerAmountValue = "";
   private int selectedInventorySlot = -1;
   private boolean listingRequestPending;
   private boolean offerRequestPending;
   private boolean creatingListing;
   private boolean offersInbox;
   private long offeringListingId;
   private int marketScrollOffset;
   private int onlinePlayersPage;

   private EconomyScreen(int tab) {
      super(Component.translatable("screen.runevestiges.economy.title"));
      this.tab = tab;
   }

   public static void open(int tab) {
      Minecraft minecraft = Minecraft.getInstance();
      EconomyScreen screen = new EconomyScreen(tab);
      current = screen;
      minecraft.setScreenAndShow(screen);

      int action = switch (tab) {
         case 1 -> 1;
         case 2 -> 7;
         case 3 -> 10;
         default -> 0;
      };
      if (!EconomyClientNetworking.send(action(action, 0L, "", 0L, 0, tab))) {
         screen.localStatus = Component.translatable("message.runevestiges.economy.server_unavailable");
      }
   }

   public static void receive(EconomyPayloads.Snapshot snapshot) {
      Minecraft minecraft = Minecraft.getInstance();
      if (current != null) {
         if (current.recipientField != null) {
            current.recipientValue = current.recipientField.getValue();
         }

         if (current.transferAmountField != null) {
            current.transferAmountValue = current.transferAmountField.getValue();
         }

         if (current.listingPriceField != null) {
            current.listingPriceValue = current.listingPriceField.getValue();
         }

         if (current.offerAmountField != null) {
            current.offerAmountValue = current.offerAmountField.getValue();
         }

         if (current.listingRequestPending) {
            if (snapshot.message().equals("message.runevestiges.economy.success")) {
               current.selectedInventorySlot = -1;
               if (current.tab == 1) {
                  current.creatingListing = false;
               }
            }

            current.listingRequestPending = false;
         }

         if (current.offerRequestPending) {
            if (snapshot.message().equals("message.runevestiges.economy.success")) {
               current.offeringListingId = 0L;
            }

            current.offerRequestPending = false;
         }

         current.offersInbox = snapshot.tab() == 1 && snapshot.view() == 1;
         current.tab = snapshot.tab();
         current.snapshot = snapshot;
         current.onlinePlayersPage = Math.min(current.onlinePlayersPage, current.onlinePlayerPageCount() - 1);
         current.localStatus = null;
         current.init(current.width, current.height);
      }
   }

   public static void updateBalance(long balance) {
      if (current != null && current.snapshot != null && balance >= 0L) {
         EconomyPayloads.Snapshot snapshot = current.snapshot;
         current.snapshot = new EconomyPayloads.Snapshot(
            balance,
            snapshot.page(),
            snapshot.pageCount(),
            snapshot.tab(),
            snapshot.view(),
            snapshot.message(),
            snapshot.listings(),
            snapshot.offers(),
            snapshot.onlinePlayers()
         );
      }
   }

   protected void init() {
      this.recipientField = null;
      this.transferAmountField = null;
      this.listingPriceField = null;
      this.offerAmountField = null;
      this.listSelectedButton = null;
      this.submitOfferButton = null;
      this.panelWidth = Math.max(1, Math.min(420, this.width - 12));
      this.panelHeight = Math.max(1, Math.min(400, this.height - 12));
      this.panelX = (this.width - this.panelWidth) / 2;
      this.panelY = (this.height - this.panelHeight) / 2;
      if (this.tab != 2) {
         this.addRenderableWidget(
            Button.builder(this.navigationLabel("home_tab", "home_short"), button -> this.changeTab(2))
               .bounds(this.navigationX(0), this.panelY + 34, this.navigationButtonWidth(), 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(this.navigationLabel("wallet_tab", "wallet_short"), button -> this.changeTab(0))
               .bounds(this.navigationX(1), this.panelY + 34, this.navigationButtonWidth(), 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(this.navigationLabel("market_tab", "market_short"), button -> this.changeTab(1))
               .bounds(this.navigationX(2), this.panelY + 34, this.navigationButtonWidth(), 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(this.navigationLabel("server_shop_tab", "server_shop_short"), button -> this.changeTab(3))
               .bounds(this.navigationX(3), this.panelY + 34, this.navigationButtonWidth(), 20)
               .build()
         );
      }

      if (this.tab == 2) {
         this.initHomeWidgets();
      } else if (this.tab == 0) {
         this.initWalletWidgets();
      } else if (this.tab == 1) {
         this.initMarketWidgets();
      } else {
         this.initServerShopWidgets();
      }
   }

   private void initHomeWidgets() {
      int buttonWidth = Math.min(200, this.panelWidth - 32);
      int buttonX = this.panelX + (this.panelWidth - buttonWidth) / 2;
      int buttonHeight = Math.min(28, Math.max(20, (this.panelHeight - 172) / 3));
      int buttonGap = 12;
      int firstButtonY = this.panelY + Math.min(140, this.panelHeight - buttonHeight * 3 - buttonGap * 2 - 18);
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.runevestiges.economy.wallet_tab"), button -> this.changeTab(0))
            .bounds(buttonX, firstButtonY, buttonWidth, buttonHeight)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.runevestiges.economy.market_tab"), button -> this.changeTab(1))
            .bounds(buttonX, firstButtonY + buttonHeight + buttonGap, buttonWidth, buttonHeight)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.runevestiges.economy.server_shop_tab"), button -> this.changeTab(3))
            .bounds(buttonX, firstButtonY + (buttonHeight + buttonGap) * 2, buttonWidth, buttonHeight)
            .build()
      );
   }

   private void initWalletWidgets() {
      int fieldY = this.panelY + 108;
      int formWidth = this.walletFormWidth();
      int recipientWidth = this.walletRecipientWidth();
      int amountWidth = this.walletAmountWidth();
      int amountX = recipientWidth + 8;
      int transferX = amountX + amountWidth + 8;
      int formX = this.panelX + (this.panelWidth - formWidth) / 2;
      this.recipientField = new EditBox(this.font, formX, fieldY, recipientWidth, 20, Component.translatable("screen.runevestiges.economy.recipient"));
      this.recipientField.setMaxLength(16);
      this.recipientField.setValue(this.recipientValue);
      this.recipientField.setHint(Component.translatable("screen.runevestiges.economy.recipient_hint"));
      this.addRenderableWidget(this.recipientField);
      this.transferAmountField = new EditBox(this.font, formX + amountX, fieldY, amountWidth, 20, Component.translatable("screen.runevestiges.economy.amount"));
      this.transferAmountField.setMaxLength(19);
      this.transferAmountField.setValue(this.transferAmountValue);
      this.transferAmountField.setHint(Component.translatable("screen.runevestiges.economy.amount_hint"));
      this.addRenderableWidget(this.transferAmountField);
      this.addRenderableWidget(Button.builder(Component.translatable("screen.runevestiges.economy.transfer"), button -> {
         this.recipientValue = this.recipientField.getValue();
         this.transferAmountValue = this.transferAmountField.getValue();
         this.sendAction(6, 0L, this.recipientValue, parseAmount(this.transferAmountValue), 0);
      }).bounds(formX + transferX, fieldY, formWidth - transferX, 20).build());
      this.initOnlinePlayerList(formX, formWidth);
   }

   private void initOnlinePlayerList(int formX, int formWidth) {
      List<String> onlinePlayers = this.snapshot == null ? List.of() : this.snapshot.onlinePlayers();
      int selectorY = this.panelY + this.walletSelectorY();
      int pageCount = this.onlinePlayerPageCount();
      this.onlinePlayersPage = Math.clamp((long)this.onlinePlayersPage, 0, pageCount - 1);
      int pageSize = this.onlinePlayerPageSize();
      if (this.onlinePlayersPage > 0) {
         this.addRenderableWidget(
            Button.builder(Component.literal("<"), button -> this.changeOnlinePlayersPage(this.onlinePlayersPage - 1))
               .bounds(formX + formWidth - 48, selectorY, 20, 18)
               .build()
         );
      }

      if (this.onlinePlayersPage < pageCount - 1) {
         this.addRenderableWidget(
            Button.builder(Component.literal(">"), button -> this.changeOnlinePlayersPage(this.onlinePlayersPage + 1))
               .bounds(formX + formWidth - 24, selectorY, 20, 18)
               .build()
         );
      }

      this.addRenderableWidget(
         Button.builder(Component.literal("↻"), button -> this.sendAction(0, 0L, "", 0L, 0))
            .bounds(formX + formWidth - 72, selectorY, 20, 18)
            .tooltip(Tooltip.create(Component.translatable("screen.runevestiges.economy.online_player_refresh")))
            .build()
      );
      int firstPlayerIndex = this.onlinePlayersPage * pageSize;
      int visiblePlayers = Math.min(pageSize, onlinePlayers.size() - firstPlayerIndex);
      int columnWidth = (formWidth - 4) / 2;
      int rowY = selectorY + 23;

      for (int index = 0; index < visiblePlayers; index++) {
         int playerIndex = firstPlayerIndex + index;
         String playerName = onlinePlayers.get(playerIndex);
         int column = index % 2;
         int row = index / 2;
         this.addRenderableWidget(Button.builder(Component.literal(playerName), button -> {
            this.recipientValue = playerName;
            if (this.recipientField != null) {
               this.recipientField.setValue(playerName);
            }
         }).bounds(formX + column * (columnWidth + 4), rowY + row * 22, columnWidth, 20).build());
      }
   }

   private int onlinePlayerPageSize() {
      int visibleRows = Math.max(1, Math.min(3, (this.panelHeight - this.walletSelectorY() - 92) / 22));
      return visibleRows * 2;
   }

   private int onlinePlayerPageCount() {
      int playerCount = this.snapshot == null ? 0 : this.snapshot.onlinePlayers().size();
      return Math.max(1, (playerCount + this.onlinePlayerPageSize() - 1) / this.onlinePlayerPageSize());
   }

   private void changeOnlinePlayersPage(int page) {
      this.onlinePlayersPage = page;
      this.init(this.width, this.height);
   }

   private void initMarketWidgets() {
      this.initMarketNavigationWidgets();
      if (this.offeringListingId != 0L) {
         this.initOfferWidgets();
      } else if (this.creatingListing) {
         this.initMarketListingWidgets();
      } else if (this.offersInbox) {
         this.initOfferInboxWidgets();
      } else {
         int firstRowY = this.panelY + this.marketListingsY();
         int rowHeight = this.listingRowHeight();
         if (this.snapshot != null) {
            this.clampMarketScrollOffset(this.snapshot.listings().size());
            int lastIndex = Math.min(this.snapshot.listings().size(), this.marketScrollOffset + this.visibleMarketRows());

            for (int index = this.marketScrollOffset; index < lastIndex; index++) {
               EconomyPayloads.Listing listing = this.snapshot.listings().get(index);
               int rowY = firstRowY + (index - this.marketScrollOffset) * rowHeight;
               int buttonHeight = Math.min(20, rowHeight - 2);
               int buttonY = rowY + (rowHeight - buttonHeight) / 2;
               if (listing.own()) {
                  this.addListingActionButton(
                     listing, "screen.runevestiges.economy.cancel_listing", 5, this.panelX + this.panelWidth - 78, buttonY, 66, buttonHeight
                  );
               } else {
                  this.addListingActionButton(
                     listing, "screen.runevestiges.economy.make_offer", 11, this.panelX + this.panelWidth - 158, buttonY, 76, buttonHeight
                  );
                  this.addListingActionButton(
                     listing, "screen.runevestiges.economy.buy_listing", 4, this.panelX + this.panelWidth - 78, buttonY, 66, buttonHeight
                  );
               }
            }
         }

         this.addRenderableWidget(
            Button.builder(Component.literal("<"), button -> this.changeMarketPage(Math.max(0, this.currentPage() - 1)))
               .bounds(this.panelX + this.panelWidth / 2 - 72, this.panelY + this.panelHeight - 26, 28, 18)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.literal(">"), button -> this.changeMarketPage(this.currentPage() + 1))
               .bounds(this.panelX + this.panelWidth / 2 + 44, this.panelY + this.panelHeight - 26, 28, 18)
               .build()
         );
      }
   }

   private void initMarketNavigationWidgets() {
      int refreshWidth = this.panelWidth < 360 ? 0 : 84;
      int gap = 4;
      int navigationWidth = this.panelWidth - 24 - refreshWidth - gap * (refreshWidth == 0 ? 2 : 3);
      int tabWidth = navigationWidth / 3;
      int controlY = this.marketControlY();
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.runevestiges.economy.browse_tab"), button -> this.setMarketView(0))
            .bounds(this.panelX + 12, this.panelY + controlY, tabWidth, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.runevestiges.economy.create_listing_tab"), button -> this.setMarketView(2))
            .bounds(this.panelX + 12 + tabWidth + gap, this.panelY + controlY, tabWidth, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.runevestiges.economy.offers_tab"), button -> this.setMarketView(1))
            .bounds(this.panelX + 12 + (tabWidth + gap) * 2, this.panelY + controlY, tabWidth, 20)
            .build()
      );
      if (refreshWidth > 0 && this.offeringListingId == 0L && !this.creatingListing) {
         this.addRenderableWidget(
            Button.builder(Component.translatable("screen.runevestiges.economy.refresh"), button -> this.sendAction(2, 0L, "", 0L, this.currentPage()))
               .bounds(this.panelX + this.panelWidth - 12 - refreshWidth, this.panelY + controlY, refreshWidth, 20)
               .build()
         );
      }
   }

   private void addListingActionButton(EconomyPayloads.Listing listing, String labelKey, int action, int x, int y, int buttonWidth, int buttonHeight) {
      this.addRenderableWidget(Button.builder(Component.translatable(labelKey), button -> {
         if (action == 11) {
            this.offeringListingId = listing.id();
            this.offerAmountValue = "";
            this.init(this.width, this.height);
         } else {
            this.marketScrollOffset = 0;
            this.sendAction(action, listing.id(), "", 0L, this.currentPage());
         }
      }).bounds(x, y, buttonWidth, buttonHeight).build());
   }

   private void initOfferInboxWidgets() {
      int firstRowY = this.panelY + this.marketListingsY();
      int rowHeight = this.listingRowHeight();
      if (this.snapshot != null) {
         this.clampMarketScrollOffset(this.snapshot.offers().size());
         int lastIndex = Math.min(this.snapshot.offers().size(), this.marketScrollOffset + this.visibleMarketRows());

         for (int index = this.marketScrollOffset; index < lastIndex; index++) {
            EconomyPayloads.Offer offer = this.snapshot.offers().get(index);
            int rowY = firstRowY + (index - this.marketScrollOffset) * rowHeight;
            int buttonHeight = Math.min(20, rowHeight - 2);
            this.addRenderableWidget(Button.builder(Component.translatable("screen.runevestiges.economy.accept_offer"), button -> {
               this.marketScrollOffset = 0;
               this.sendAction(12, offer.id(), "", 0L, this.currentPage());
            }).bounds(this.panelX + this.panelWidth - 120, rowY + (rowHeight - buttonHeight) / 2, 58, buttonHeight).build());
            this.addRenderableWidget(Button.builder(Component.translatable("screen.runevestiges.economy.decline_offer"), button -> {
               this.marketScrollOffset = 0;
               this.sendAction(13, offer.id(), "", 0L, this.currentPage());
            }).bounds(this.panelX + this.panelWidth - 58, rowY + (rowHeight - buttonHeight) / 2, 54, buttonHeight).build());
         }
      }

      this.addMarketPaginationWidgets();
   }

   private void initOfferWidgets() {
      int fieldY = this.offerFieldY();
      this.offerAmountField = new EditBox(
         this.font,
         this.panelX + (this.panelWidth - 120) / 2,
         this.panelY + fieldY,
         120,
         20,
         Component.translatable("screen.runevestiges.economy.offer_amount")
      );
      this.offerAmountField.setMaxLength(19);
      this.offerAmountField.setValue(this.offerAmountValue);
      this.offerAmountField.setHint(Component.translatable("screen.runevestiges.economy.amount_hint"));
      this.addRenderableWidget(this.offerAmountField);
      this.submitOfferButton = Button.builder(Component.translatable("screen.runevestiges.economy.send_offer"), button -> {
         this.offerAmountValue = this.offerAmountField.getValue();
         this.offerRequestPending = this.sendAction(11, this.offeringListingId, "", parseAmount(this.offerAmountValue), this.currentPage());
      }).bounds(this.panelX + (this.panelWidth - 160) / 2, this.panelY + fieldY + 26, 160, 22).build();
      this.submitOfferButton.active = false;
      this.addRenderableWidget(this.submitOfferButton);
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.runevestiges.economy.back_to_listings"), button -> this.setMarketView(0))
            .bounds(this.panelX + (this.panelWidth - 160) / 2, this.panelY + fieldY + 52, 160, 20)
            .build()
      );
   }

   private void addMarketPaginationWidgets() {
      this.addRenderableWidget(
         Button.builder(Component.literal("<"), button -> this.changeMarketPage(Math.max(0, this.currentPage() - 1)))
            .bounds(this.panelX + this.panelWidth / 2 - 72, this.panelY + this.panelHeight - 26, 28, 18)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal(">"), button -> this.changeMarketPage(this.currentPage() + 1))
            .bounds(this.panelX + this.panelWidth / 2 + 44, this.panelY + this.panelHeight - 26, 28, 18)
            .build()
      );
   }

   private void initMarketListingWidgets() {
      this.listingPriceField = new EditBox(
         this.font, this.panelX + 12, this.marketPriceControlY(), 92, 20, Component.translatable("screen.runevestiges.economy.price")
      );
      this.listingPriceField.setMaxLength(19);
      this.listingPriceField.setValue(this.listingPriceValue);
      this.listingPriceField.setHint(Component.translatable("screen.runevestiges.economy.amount_hint"));
      this.addRenderableWidget(this.listingPriceField);
      this.listSelectedButton = Button.builder(Component.translatable("screen.runevestiges.economy.sell_selected_item"), button -> {
         this.listingPriceValue = this.listingPriceField.getValue();
         this.listingRequestPending = this.sendAction(8, this.selectedInventorySlot, "", parseAmount(this.listingPriceValue), 0);
      }).bounds(this.panelX + 112, this.marketPriceControlY(), this.panelWidth - 124, 20).build();
      this.listSelectedButton.active = false;
      this.addRenderableWidget(this.listSelectedButton);
      this.initInventoryGridWidgets();
   }

   private void initServerShopWidgets() {
      this.listSelectedButton = Button.builder(
            Component.translatable("screen.runevestiges.economy.sell_to_server"),
            button -> this.listingRequestPending = this.sendAction(9, this.selectedInventorySlot, "", 0L, 0)
         )
         .bounds(this.panelX + 12, this.panelY + this.shopSellButtonY(), this.panelWidth - 24, 20)
         .build();
      this.listSelectedButton.active = false;
      this.addRenderableWidget(this.listSelectedButton);
      this.initInventoryGridWidgets();
   }

   private void initInventoryGridWidgets() {
      int slotSize = this.inventorySlotSize();
      int slotSpacing = this.inventorySlotSpacing();
      int inventoryX = this.panelX + (this.panelWidth - 9 * slotSpacing) / 2;
      int inventoryY = this.panelY + this.inventoryGridY();

      for (int slot = 0; slot < 36; slot++) {
         int column = slot % 9;
         int row = slot / 9;
         this.addRenderableWidget(new EconomyScreen.InventorySlotWidget(inventoryX + column * slotSpacing, inventoryY + row * slotSpacing, slotSize, slot));
      }
   }

   private void changeTab(int selectedTab) {
      if (selectedTab == 2) {
         open(2);
      } else {
         open(selectedTab);
      }
   }

   private void setMarketView(int selectedView) {
      if (this.listingPriceField != null) {
         this.listingPriceValue = this.listingPriceField.getValue();
      }

      if (this.offerAmountField != null) {
         this.offerAmountValue = this.offerAmountField.getValue();
      }

      this.offeringListingId = 0L;
      this.marketScrollOffset = 0;
      this.creatingListing = selectedView == 2;
      this.offersInbox = selectedView == 1;
      if (selectedView != 2) {
         this.sendAction(2, 0L, "", 0L, 0);
      }

      this.init(this.width, this.height);
   }

   private int navigationButtonWidth() {
      return (this.panelWidth - 30) / 4;
   }

   private int walletFormWidth() {
      return Math.min(396, this.panelWidth - 24);
   }

   private int walletSelectorY() {
      return this.panelHeight < 300 ? 134 : 139;
   }

   private int walletRecipientWidth() {
      return this.walletFormWidth() * 44 / 100;
   }

   private int walletAmountWidth() {
      return this.walletFormWidth() * 27 / 100;
   }

   private int navigationX(int index) {
      return this.panelX + 12 + index * (this.navigationButtonWidth() + 2);
   }

   private Component navigationLabel(String fullKey, String shortKey) {
      return Component.translatable("screen.runevestiges.economy." + (this.navigationButtonWidth() < 82 ? shortKey : fullKey));
   }

   private boolean compactHeight() {
      return this.panelHeight < 396;
   }

   private int inventoryGridY() {
      if (this.panelHeight < 300) {
         return this.tab == 1 ? 120 : 112;
      } else {
         return this.panelHeight < 360 ? 132 : (this.compactHeight() ? 142 : 146);
      }
   }

   private int inventoryLabelY() {
      if (this.panelHeight < 300) {
         return this.tab == 1 ? 110 : 94;
      } else {
         return this.panelHeight < 360 ? 114 : (this.compactHeight() ? 124 : 128);
      }
   }

   private int inventorySlotSize() {
      return this.panelHeight < 300 ? 14 : 18;
   }

   private int inventorySlotSpacing() {
      return this.panelHeight < 300 ? 16 : 20;
   }

   private int marketControlY() {
      return this.panelHeight < 300 ? 88 : 96;
   }

   private int marketListingsY() {
      return this.marketControlY() + 30;
   }

   private int listingRowHeight() {
      return 38;
   }

   private int visibleMarketRows() {
      int availableHeight = this.panelHeight - this.marketListingsY() - 54;
      return Math.max(1, availableHeight / this.listingRowHeight());
   }

   private void clampMarketScrollOffset(int entryCount) {
      this.marketScrollOffset = Math.clamp((long)this.marketScrollOffset, 0, Math.max(0, entryCount - this.visibleMarketRows()));
   }

   private void changeMarketPage(int page) {
      this.marketScrollOffset = 0;
      this.sendAction(2, 0L, "", 0L, page);
   }

   private int marketPriceControlY() {
      return this.panelHeight < 300 ? this.panelY + this.panelHeight - 30 : this.panelY + Math.min(this.inventoryGridY() + 100, this.panelHeight - 76);
   }

   private int shopSellButtonY() {
      return this.panelHeight < 360 ? this.panelHeight - 30 : Math.min(this.shopDetailsY() + 66, this.panelHeight - 76);
   }

   private int shopDetailsY() {
      if (this.panelHeight < 300) {
         return this.panelHeight - 44;
      } else {
         return this.panelHeight < 360 ? this.panelHeight - 78 : this.inventoryGridY() + 86;
      }
   }

   private int offerFieldY() {
      return this.panelHeight < 300 ? this.panelHeight - 80 : 174;
   }

   private boolean sendAction(int action, long listingId, String text, long amount, int page) {
      int actionTab = action != 1
            && action != 3
            && action != 8
            && action != 4
            && action != 5
            && action != 11
            && action != 12
            && action != 13
            && (action != 2 || this.tab != 1)
         ? (action != 10 && action != 9 && (action != 2 || this.tab != 3) ? 0 : 3)
         : 1;
      int actionView = this.tab == 1 && this.offersInbox ? 1 : 0;
      if (!EconomyClientNetworking.send(action(action, listingId, text, amount, page, actionTab, actionView))) {
         this.localStatus = Component.translatable("message.runevestiges.economy.server_unavailable");
         return false;
      } else {
         return true;
      }
   }

   public void tick() {
      super.tick();
      if (this.listSelectedButton != null && this.tab == 1 && this.listingPriceField != null) {
         this.listingPriceValue = this.listingPriceField.getValue();
         ItemStack selected = this.selectedStack();
         this.listSelectedButton.active = !this.listingRequestPending && selected != null && parseAmount(this.listingPriceValue) > 0L;
      } else if (this.listSelectedButton != null && this.tab == 3) {
         this.listSelectedButton.active = !this.listingRequestPending && this.selectedStack() != null;
      }

      if (this.submitOfferButton != null && this.offerAmountField != null) {
         this.offerAmountValue = this.offerAmountField.getValue();
         this.submitOfferButton.active = !this.offerRequestPending && parseAmount(this.offerAmountValue) > 0L;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.tab == 1
         && this.offeringListingId == 0L
         && !this.creatingListing
         && this.snapshot != null
         && scrollY != 0.0
         && !(mouseY < this.panelY + this.marketListingsY())
         && !(mouseY >= this.panelY + this.panelHeight - 52)) {
         int entryCount = this.offersInbox ? this.snapshot.offers().size() : this.snapshot.listings().size();
         int previousOffset = this.marketScrollOffset;
         int direction = scrollY < 0.0 ? 1 : -1;
         this.marketScrollOffset = Math.clamp((long)(this.marketScrollOffset + direction), 0, Math.max(0, entryCount - this.visibleMarketRows()));
         if (previousOffset != this.marketScrollOffset) {
            this.init(this.width, this.height);
         }

         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   private ItemStack selectedStack() {
      if (Minecraft.getInstance().player != null && this.selectedInventorySlot >= 0) {
         Inventory inventory = Minecraft.getInstance().player.getInventory();
         if (this.selectedInventorySlot >= inventory.getNonEquipmentItems().size()) {
            return null;
         } else {
            ItemStack stack = inventory.getItem(this.selectedInventorySlot);
            return stack.isEmpty() ? null : stack;
         }
      } else {
         return null;
      }
   }

   private int currentPage() {
      return this.snapshot == null ? 0 : this.snapshot.page();
   }

   private static long parseAmount(String rawAmount) {
      try {
         return Long.parseLong(rawAmount);
      } catch (NumberFormatException var2) {
         return 0L;
      }
   }

   private static EconomyPayloads.Action action(int action, long listingId, String text, long amount, int page, int tab) {
      return action(action, listingId, text, amount, page, tab, 0);
   }

   private static EconomyPayloads.Action action(int action, long listingId, String text, long amount, int page, int tab, int view) {
      return new EconomyPayloads.Action(action, listingId, text, amount, page, tab, view);
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics, mouseX, mouseY, partialTick);
      graphics.fill(this.panelX - 3, this.panelY - 3, this.panelX + this.panelWidth + 3, this.panelY + this.panelHeight + 3, -16250352);
      graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, -988406996);
      graphics.fill(this.panelX + 6, this.panelY + 6, this.panelX + this.panelWidth - 6, this.panelY + 28, -14406590);
      graphics.fill(this.panelX + 14, this.panelY + 30, this.panelX + this.panelWidth - 14, this.panelY + 31, -7441343);
      graphics.fill(this.panelX + 14, this.panelY + 89, this.panelX + this.panelWidth - 14, this.panelY + 90, -11906197);
      graphics.fill(this.panelX + 8, this.panelY + 9, this.panelX + 10, this.panelY + 25, -3627428);
      graphics.fill(this.panelX + this.panelWidth - 10, this.panelY + 9, this.panelX + this.panelWidth - 8, this.panelY + 25, -3627428);
      graphics.centeredText(this.font, this.title, this.width / 2, this.panelY + 12, -7776);
      this.drawNavigationAccent(graphics);
      int balanceWidth = Math.min(224, this.panelWidth - 24);
      graphics.fill(
         this.panelX + (this.panelWidth - balanceWidth) / 2,
         this.panelY + 65 - 2,
         this.panelX + (this.panelWidth + balanceWidth) / 2,
         this.panelY + 65 + 19,
         -14670278
      );
      graphics.fill(
         this.panelX + (this.panelWidth - balanceWidth) / 2,
         this.panelY + 65 - 2,
         this.panelX + (this.panelWidth + balanceWidth) / 2,
         this.panelY + 65 - 1,
         -7441343
      );
      if (this.snapshot == null) {
         graphics.centeredText(this.font, Component.translatable("screen.runevestiges.economy.loading"), this.width / 2, this.panelY + 74, -1);
      } else {
         this.drawCenteredFit(
            graphics,
            Component.translatable("screen.runevestiges.economy.balance", new Object[]{this.snapshot.balance()}),
            this.panelX + 12,
            this.panelY + 65 + 5,
            this.panelWidth - 24,
            -10119
         );
         if (this.tab == 0) {
            this.renderWalletContent(graphics);
         } else if (this.tab == 1) {
            this.renderMarketContent(graphics);
         } else if (this.tab == 3) {
            this.renderServerShopContent(graphics);
         } else {
            this.renderHomeContent(graphics);
         }
      }

      Component status = this.localStatus;
      if (status == null && this.snapshot != null && !this.snapshot.message().isEmpty()) {
         status = Component.translatable(this.snapshot.message());
      }

      if (status != null) {
         int statusColor = status.equals(Component.translatable("message.runevestiges.economy.success")) ? -6561604 : -24159;
         graphics.fill(
            this.panelX + 10, this.panelY + this.panelHeight - 47, this.panelX + this.panelWidth - 10, this.panelY + this.panelHeight - 32, -434100678
         );
         this.drawCenteredFit(graphics, status, this.panelX + 12, this.panelY + this.panelHeight - 42, this.panelWidth - 24, statusColor);
      }

      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
   }

   private void renderBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 672143408);
   }

   private void drawNavigationAccent(GuiGraphicsExtractor graphics) {
      int x;
      if (this.tab == 0) {
         x = this.navigationX(1);
      } else if (this.tab == 1) {
         x = this.navigationX(2);
      } else {
         if (this.tab != 3) {
            return;
         }

         x = this.navigationX(3);
      }

      graphics.fill(x + 4, this.panelY + 34 + 20, x + this.navigationButtonWidth() - 4, this.panelY + 34 + 22, -10119);
      if (this.tab == 1) {
         int refreshWidth = this.panelWidth < 360 ? 0 : 84;
         int gap = 4;
         int navigationWidth = this.panelWidth - 24 - refreshWidth - gap * (refreshWidth == 0 ? 2 : 3);
         int tabWidth = navigationWidth / 3;
         int marketTabIndex = this.offeringListingId != 0L ? 0 : (this.creatingListing ? 1 : (this.offersInbox ? 2 : 0));
         int marketTabX = this.panelX + 12 + (tabWidth + gap) * marketTabIndex;
         graphics.fill(marketTabX + 4, this.panelY + this.marketControlY() + 20, marketTabX + tabWidth - 4, this.panelY + this.marketControlY() + 22, -10119);
      }
   }

   private void drawCenteredFit(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth, int color) {
      String clipped = this.font.plainSubstrByWidth(text.getString(), maxWidth);
      graphics.centeredText(this.font, clipped, x + maxWidth / 2, y, color);
   }

   private void drawCenteredWrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth, int color, int lineHeight, int maxLines) {
      List<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();

      for (String word : text.getString().split("\\s+")) {
         String candidate = line.isEmpty() ? word : line + " " + word;
         if (this.font.width(candidate) <= maxWidth) {
            line.setLength(0);
            line.append(candidate);
         } else if (line.isEmpty()) {
            lines.add(this.font.plainSubstrByWidth(word, maxWidth));
         } else {
            lines.add(line.toString());
            line.setLength(0);
            if (this.font.width(word) > maxWidth) {
               lines.add(this.font.plainSubstrByWidth(word, maxWidth));
            } else {
               line.append(word);
            }
         }
      }

      if (!line.isEmpty()) {
         lines.add(line.toString());
      }

      for (int index = 0; index < Math.min(lines.size(), maxLines); index++) {
         String visible = lines.get(index);
         if (index == maxLines - 1 && lines.size() > maxLines) {
            visible = this.font.plainSubstrByWidth(visible, Math.max(1, maxWidth - this.font.width("..."))) + "...";
         }

         graphics.centeredText(this.font, visible, x + maxWidth / 2, y + index * lineHeight, color);
      }
   }

   private void renderHomeContent(GuiGraphicsExtractor graphics) {
      this.drawCenteredFit(
         graphics, Component.translatable("screen.runevestiges.economy.home_heading"), this.panelX + 16, this.panelY + 101, this.panelWidth - 32, -10119
      );
      this.drawCenteredWrapped(
         graphics,
         Component.translatable("screen.runevestiges.economy.home_description"),
         this.panelX + 20,
         this.panelY + 118,
         this.panelWidth - 40,
         -2038545,
         11,
         2
      );
      graphics.fill(this.panelX + this.panelWidth / 2 - 24, this.panelY + 132, this.panelX + this.panelWidth / 2 + 24, this.panelY + 133, -7441343);
   }

   private void renderWalletContent(GuiGraphicsExtractor graphics) {
      int formX = this.panelX + (this.panelWidth - this.walletFormWidth()) / 2;
      int formWidth = this.walletFormWidth();
      int recipientWidth = this.walletRecipientWidth();
      int amountWidth = this.walletAmountWidth();
      graphics.centeredText(this.font, Component.translatable("screen.runevestiges.economy.recipient"), formX + recipientWidth / 2, this.panelY + 94, -2038545);
      graphics.fill(formX, this.panelY + 90, formX + recipientWidth, this.panelY + 91, -7441343);
      graphics.fill(formX + recipientWidth + 8, this.panelY + 90, formX + recipientWidth + 8 + amountWidth, this.panelY + 91, -7441343);
      graphics.centeredText(
         this.font, Component.translatable("screen.runevestiges.economy.amount"), formX + recipientWidth + 8 + amountWidth / 2, this.panelY + 94, -2038545
      );
      int onlineCount = this.snapshot == null ? 0 : this.snapshot.onlinePlayers().size();
      int firstPlayer = this.onlinePlayersPage * this.onlinePlayerPageSize();
      Component onlinePlayersLabel = onlineCount == 0
         ? Component.translatable("screen.runevestiges.economy.online_players_empty")
         : Component.translatable(
            "screen.runevestiges.economy.online_players",
            new Object[]{firstPlayer + 1, Math.min(onlineCount, firstPlayer + this.onlinePlayerPageSize()), onlineCount}
         );
      this.drawCenteredFit(graphics, onlinePlayersLabel, formX, this.panelY + this.walletSelectorY() + 4, Math.max(1, formWidth - 80), -2038545);
      if (this.panelHeight >= 300) {
         int visibleRows = this.onlinePlayerPageSize() / 2;
         this.drawCenteredWrapped(
            graphics,
            Component.translatable("screen.runevestiges.economy.transfer_hint"),
            this.panelX + 12,
            this.panelY + this.walletSelectorY() + 27 + visibleRows * 22,
            this.panelWidth - 24,
            -4273958,
            11,
            2
         );
      }
   }

   private void renderMarketContent(GuiGraphicsExtractor graphics) {
      if (this.offeringListingId != 0L) {
         EconomyPayloads.Listing listing = this.snapshot == null
            ? null
            : this.snapshot.listings().stream().filter(candidate -> candidate.id() == this.offeringListingId).findFirst().orElse(null);
         Component details = listing == null
            ? Component.translatable("screen.runevestiges.economy.offer_listing_missing")
            : Component.translatable(
               "screen.runevestiges.economy.offer_target", new Object[]{listing.item().getHoverName(), listing.sellerName(), listing.price()}
            );
         this.drawCenteredWrapped(
            graphics, details, this.panelX + 16, this.panelY + (this.panelHeight < 300 ? 110 : 126), this.panelWidth - 32, -2038545, 12, 2
         );
         graphics.centeredText(
            this.font, Component.translatable("screen.runevestiges.economy.offer_amount"), this.width / 2, this.panelY + this.offerFieldY() - 14, -2038545
         );
         if (this.panelHeight >= 300) {
            this.drawCenteredFit(
               graphics,
               Component.translatable("screen.runevestiges.economy.offer_funds_not_reserved"),
               this.panelX + 16,
               this.panelY + 270,
               this.panelWidth - 32,
               -4273958
            );
         }
      } else if (this.creatingListing) {
         this.drawCenteredWrapped(
            graphics,
            Component.translatable("screen.runevestiges.economy.select_item"),
            this.panelX + 12,
            this.panelY + this.inventoryLabelY(),
            this.panelWidth - 24,
            -2038545,
            10,
            1
         );
         this.renderInventoryGrid(graphics);
         int controlY = this.marketPriceControlY();
         graphics.centeredText(this.font, Component.translatable("screen.runevestiges.economy.price"), this.panelX + 58, controlY - 12, -2038545);
      } else if (this.offersInbox) {
         this.renderOfferInbox(graphics);
      } else if (this.snapshot != null && !this.snapshot.listings().isEmpty()) {
         int firstRowY = this.panelY + this.marketListingsY();
         int rowHeight = this.listingRowHeight();
         this.clampMarketScrollOffset(this.snapshot.listings().size());
         graphics.enableScissor(this.panelX + 6, firstRowY, this.panelX + this.panelWidth - 6, this.panelY + this.panelHeight - 52);
         int lastIndex = Math.min(this.snapshot.listings().size(), this.marketScrollOffset + this.visibleMarketRows());

         for (int index = this.marketScrollOffset; index < lastIndex; index++) {
            EconomyPayloads.Listing listing = this.snapshot.listings().get(index);
            int rowY = firstRowY + (index - this.marketScrollOffset) * rowHeight;
            graphics.fill(this.panelX + 8, rowY, this.panelX + this.panelWidth - 8, rowY + rowHeight - 1, index % 2 == 0 ? -14077623 : -14472385);
            graphics.item(listing.item(), this.panelX + 12, rowY + (rowHeight - 16) / 2);
            if (listing.item().getCount() > 1) {
               graphics.itemDecorations(this.font, listing.item(), this.panelX + 12, rowY + (rowHeight - 16) / 2);
            }

            int descriptionX = this.panelX + 34;
            int reservedWidth = listing.own() ? 84 : 158;
            int descriptionWidth = Math.max(1, this.panelWidth - 34 - reservedWidth - 6);
            Component itemDescription = Component.translatable(
               "screen.runevestiges.economy.listing_item",
               new Object[]{
                  listing.item().getHoverName(),
                  listing.item().getCount(),
                  listing.own() ? Component.translatable("screen.runevestiges.economy.your_listing") : listing.sellerName()
               }
            );
            graphics.text(this.font, this.font.plainSubstrByWidth(itemDescription.getString(), descriptionWidth), descriptionX, rowY + 5, -921103);
            Component priceDescription = Component.translatable("screen.runevestiges.economy.listing_price", new Object[]{listing.price()});
            graphics.text(this.font, this.font.plainSubstrByWidth(priceDescription.getString(), descriptionWidth), descriptionX, rowY + rowHeight - 13, -10119);
         }

         graphics.disableScissor();
         this.renderMarketScrollHint(graphics, this.snapshot.listings().size());
         this.renderMarketPageNumber(graphics);
      } else {
         this.drawCenteredWrapped(
            graphics,
            Component.translatable("screen.runevestiges.economy.no_listings"),
            this.panelX + 16,
            this.panelY + this.marketListingsY() + 28,
            this.panelWidth - 32,
            -2762006,
            11,
            2
         );
      }
   }

   private void renderOfferInbox(GuiGraphicsExtractor graphics) {
      if (this.snapshot == null) {
         this.drawCenteredFit(
            graphics,
            Component.translatable("screen.runevestiges.economy.loading"),
            this.panelX + 12,
            this.panelY + this.marketListingsY() + 20,
            this.panelWidth - 24,
            -2762006
         );
      } else if (this.snapshot.offers().isEmpty()) {
         this.drawCenteredWrapped(
            graphics,
            Component.translatable("screen.runevestiges.economy.no_offers"),
            this.panelX + 16,
            this.panelY + this.marketListingsY() + 28,
            this.panelWidth - 32,
            -2762006,
            11,
            2
         );
         this.renderMarketPageNumber(graphics);
      } else {
         int firstRowY = this.panelY + this.marketListingsY();
         int rowHeight = this.listingRowHeight();
         this.clampMarketScrollOffset(this.snapshot.offers().size());
         graphics.enableScissor(this.panelX + 6, firstRowY, this.panelX + this.panelWidth - 6, this.panelY + this.panelHeight - 52);
         int lastIndex = Math.min(this.snapshot.offers().size(), this.marketScrollOffset + this.visibleMarketRows());

         for (int index = this.marketScrollOffset; index < lastIndex; index++) {
            EconomyPayloads.Offer offer = this.snapshot.offers().get(index);
            int rowY = firstRowY + (index - this.marketScrollOffset) * rowHeight;
            graphics.fill(this.panelX + 8, rowY, this.panelX + this.panelWidth - 8, rowY + rowHeight - 1, index % 2 == 0 ? -14077623 : -14472385);
            graphics.item(offer.item(), this.panelX + 12, rowY + (rowHeight - 16) / 2);
            int descriptionX = this.panelX + 34;
            int descriptionWidth = Math.max(1, this.panelWidth - 162);
            Component itemDescription = Component.translatable("screen.runevestiges.economy.offer_item", new Object[]{offer.item().getHoverName()});
            graphics.text(this.font, this.font.plainSubstrByWidth(itemDescription.getString(), descriptionWidth), descriptionX, rowY + 5, -921103);
            Component offerDescription = Component.translatable("screen.runevestiges.economy.offer_from", new Object[]{offer.buyerName(), offer.amount()});
            graphics.text(this.font, this.font.plainSubstrByWidth(offerDescription.getString(), descriptionWidth), descriptionX, rowY + rowHeight - 13, -10119);
         }

         graphics.disableScissor();
         this.renderMarketScrollHint(graphics, this.snapshot.offers().size());
         this.renderMarketPageNumber(graphics);
      }
   }

   private void renderMarketScrollHint(GuiGraphicsExtractor graphics, int entryCount) {
      if (entryCount > this.visibleMarketRows()) {
         this.drawCenteredFit(
            graphics,
            Component.translatable(
               "screen.runevestiges.economy.market_scroll",
               new Object[]{this.marketScrollOffset + 1, Math.min(entryCount, this.marketScrollOffset + this.visibleMarketRows()), entryCount}
            ),
            this.panelX + 12,
            this.panelY + this.panelHeight - 20,
            Math.max(1, this.panelWidth / 2 - 100),
            -4273958
         );
      }
   }

   private void renderMarketPageNumber(GuiGraphicsExtractor graphics) {
      if (this.snapshot != null) {
         this.drawCenteredFit(
            graphics,
            Component.translatable("screen.runevestiges.economy.page", new Object[]{this.snapshot.page() + 1, this.snapshot.pageCount()}),
            this.panelX + this.panelWidth / 2 - 40,
            this.panelY + this.panelHeight - 20,
            80,
            -2038545
         );
      }
   }

   private void renderServerShopContent(GuiGraphicsExtractor graphics) {
      this.drawCenteredWrapped(
         graphics,
         Component.translatable("screen.runevestiges.economy.shop_select_item"),
         this.panelX + 12,
         this.panelY + this.inventoryLabelY(),
         this.panelWidth - 24,
         -2038545,
         10,
         1
      );
      this.renderInventoryGrid(graphics);
      ItemStack selected = this.selectedStack();
      Component quote = selected == null
         ? Component.translatable("screen.runevestiges.economy.shop_quote_empty")
         : Component.translatable(
            "screen.runevestiges.economy.shop_quote",
            new Object[]{selected.getHoverName(), selected.getCount(), ShopPricing.unitPrice(selected), ShopPricing.totalPrice(selected)}
         );
      int detailsY = this.shopDetailsY();
      int detailsTop = detailsY - 5;
      int detailsBottom = this.panelHeight < 300 ? this.panelHeight - 34 : (this.panelHeight < 360 ? detailsY + 18 : detailsY + 50);
      graphics.fill(this.panelX + 12, this.panelY + detailsTop, this.panelX + this.panelWidth - 12, this.panelY + detailsBottom, -2010961347);
      graphics.fill(this.panelX + 12, this.panelY + detailsTop, this.panelX + this.panelWidth - 12, this.panelY + detailsTop + 1, -7441343);
      this.drawCenteredFit(graphics, quote, this.panelX + 20, this.panelY + detailsY, this.panelWidth - 40, -10119);
      if (this.panelHeight >= 360) {
         this.drawCenteredWrapped(
            graphics,
            Component.translatable("screen.runevestiges.economy.shop_price_tiers"),
            this.panelX + 16,
            this.panelY + detailsY + 17,
            this.panelWidth - 32,
            -4273958,
            10,
            1
         );
         this.drawCenteredWrapped(
            graphics,
            Component.translatable("screen.runevestiges.economy.shop_price_exceptions"),
            this.panelX + 16,
            this.panelY + detailsY + 30,
            this.panelWidth - 32,
            -4273958,
            10,
            2
         );
      }
   }

   private void renderInventoryGrid(GuiGraphicsExtractor graphics) {
      if (Minecraft.getInstance().player != null) {
         int slotSize = this.inventorySlotSize();
         int slotSpacing = this.inventorySlotSpacing();
         int inventoryX = this.panelX + (this.panelWidth - 9 * slotSpacing) / 2;
         int inventoryY = this.panelY + this.inventoryGridY();
         graphics.fill(inventoryX - 5, inventoryY - 5, inventoryX + 9 * slotSpacing - 2, inventoryY + 4 * slotSpacing - 2, -14341056);
         graphics.fill(inventoryX - 5, inventoryY - 5, inventoryX + 9 * slotSpacing - 2, inventoryY - 4, -7441343);

         for (int slot = 0; slot < 36; slot++) {
            int column = slot % 9;
            int row = slot / 9;
            int x = inventoryX + column * slotSpacing;
            int y = inventoryY + row * slotSpacing;
            ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(slot);
            if (slot == this.selectedInventorySlot) {
               graphics.fill(x - 1, y - 1, x + slotSize + 1, y + slotSize + 1, -10133);
            }

            graphics.fill(x, y, x + slotSize, y + slotSize, -15657435);
            graphics.item(stack, x + 1, y + 1);
            if (!stack.isEmpty() && stack.getCount() > 1) {
               graphics.itemDecorations(this.font, stack, x + 1, y + 1);
            }
         }
      }
   }

   public void onClose() {
      if (current == this) {
         current = null;
      }

      super.onClose();
   }

   public boolean isPauseScreen() {
      return false;
   }

   private final class InventorySlotWidget extends AbstractWidget {
      private final int inventorySlot;

      private InventorySlotWidget(int x, int y, int slotSize, int inventorySlot) {
         Objects.requireNonNull(EconomyScreen.this);
         super(x, y, slotSize, slotSize, Component.translatable("screen.runevestiges.economy.select_item"));
         this.inventorySlot = inventorySlot;
         if (Minecraft.getInstance().player != null) {
            ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(inventorySlot);
            this.active = !stack.isEmpty();
            if (!stack.isEmpty()) {
               this.setTooltip(Tooltip.create(stack.getHoverName()));
            }
         }
      }

      protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      }

      public void onClick(MouseButtonEvent event, boolean doubleClick) {
         if (this.active) {
            EconomyScreen.this.selectedInventorySlot = this.inventorySlot;
         }
      }

      protected void updateWidgetNarration(NarrationElementOutput output) {
         this.defaultButtonNarrationText(output);
      }
   }
}
