package fr.runevestiges.client;

import fr.runevestiges.economy.EconomyPayloads;
import fr.runevestiges.economy.EconomyService;
import fr.runevestiges.economy.ShopPricing;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class EconomyScreen extends Screen {
	private static final Pattern POSITIVE_INTEGER_INPUT = Pattern.compile("\\d{0,19}");
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
		minecraft.setScreen(screen);
		int action = switch (tab) {
			case EconomyService.MARKET_TAB -> EconomyService.OPEN_MARKET;
			case EconomyService.SERVER_SHOP_TAB -> EconomyService.OPEN_SERVER_SHOP;
			case EconomyService.HOME_TAB -> EconomyService.OPEN_HOME;
			default -> EconomyService.OPEN_WALLET;
		};
		if (!EconomyClientNetworking.send(action(action, 0L, "", 0L, 0, tab))) {
			screen.localStatus = Component.translatable("message.runevestiges.economy.server_unavailable");
		}
	}

	public static void receive(EconomyPayloads.Snapshot snapshot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (current == null || minecraft.screen != current) {
			return;
		}
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
				if (current.tab == EconomyService.MARKET_TAB) {
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
		current.offersInbox = snapshot.tab() == EconomyService.MARKET_TAB
			&& snapshot.view() == EconomyService.MARKET_VIEW_OFFERS;
		current.tab = snapshot.tab();
		current.snapshot = snapshot;
		current.onlinePlayersPage = Math.min(current.onlinePlayersPage, current.onlinePlayerPageCount() - 1);
		current.localStatus = null;
		current.init(minecraft, current.width, current.height);
	}

	public static void updateBalance(long balance) {
		if (current == null || current.snapshot == null || balance < 0L) {
			return;
		}
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

	@Override
	protected void init() {
		recipientField = null;
		transferAmountField = null;
		listingPriceField = null;
		offerAmountField = null;
		listSelectedButton = null;
		submitOfferButton = null;
		panelWidth = Math.max(1, Math.min(PANEL_WIDTH, width - 12));
		panelHeight = Math.max(1, Math.min(PANEL_HEIGHT, height - 12));
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		if (tab != EconomyService.HOME_TAB) {
			addRenderableWidget(Button.builder(
				navigationLabel("home_tab", "home_short"),
				button -> changeTab(EconomyService.HOME_TAB)
			).bounds(navigationX(0), panelY + NAVIGATION_Y, navigationButtonWidth(), 20).build());
			addRenderableWidget(Button.builder(
				navigationLabel("wallet_tab", "wallet_short"),
				button -> changeTab(EconomyService.WALLET_TAB)
			).bounds(navigationX(1), panelY + NAVIGATION_Y, navigationButtonWidth(), 20).build());
			addRenderableWidget(Button.builder(
				navigationLabel("market_tab", "market_short"),
				button -> changeTab(EconomyService.MARKET_TAB)
			).bounds(navigationX(2), panelY + NAVIGATION_Y, navigationButtonWidth(), 20).build());
			addRenderableWidget(Button.builder(
				navigationLabel("server_shop_tab", "server_shop_short"),
				button -> changeTab(EconomyService.SERVER_SHOP_TAB)
			).bounds(navigationX(3), panelY + NAVIGATION_Y, navigationButtonWidth(), 20).build());
		}

		if (tab == EconomyService.HOME_TAB) {
			initHomeWidgets();
		} else if (tab == EconomyService.WALLET_TAB) {
			initWalletWidgets();
		} else if (tab == EconomyService.MARKET_TAB) {
			initMarketWidgets();
		} else {
			initServerShopWidgets();
		}
	}

	private void initHomeWidgets() {
		int buttonWidth = Math.min(200, panelWidth - 32);
		int buttonX = panelX + (panelWidth - buttonWidth) / 2;
		int buttonHeight = Math.min(28, Math.max(20, (panelHeight - 172) / 3));
		int buttonGap = 12;
		int firstButtonY = panelY + Math.min(
			140,
			panelHeight - buttonHeight * 3 - buttonGap * 2 - 18
		);
		addRenderableWidget(Button.builder(
			Component.translatable("screen.runevestiges.economy.wallet_tab"),
			button -> changeTab(EconomyService.WALLET_TAB)
		).bounds(buttonX, firstButtonY, buttonWidth, buttonHeight).build());
		addRenderableWidget(Button.builder(
			Component.translatable("screen.runevestiges.economy.market_tab"),
			button -> changeTab(EconomyService.MARKET_TAB)
		).bounds(buttonX, firstButtonY + buttonHeight + buttonGap, buttonWidth, buttonHeight).build());
		addRenderableWidget(Button.builder(
			Component.translatable("screen.runevestiges.economy.server_shop_tab"),
			button -> changeTab(EconomyService.SERVER_SHOP_TAB)
		).bounds(buttonX, firstButtonY + (buttonHeight + buttonGap) * 2, buttonWidth, buttonHeight).build());
	}

	private void initWalletWidgets() {
		int fieldY = panelY + 108;
		int formWidth = walletFormWidth();
		int recipientWidth = walletRecipientWidth();
		int amountWidth = walletAmountWidth();
		int amountX = recipientWidth + 8;
		int transferX = amountX + amountWidth + 8;
		int formX = panelX + (panelWidth - formWidth) / 2;
		recipientField = new EditBox(
			font,
			formX,
			fieldY,
			recipientWidth,
			20,
			Component.translatable("screen.runevestiges.economy.recipient")
		);
		recipientField.setMaxLength(16);
		recipientField.setValue(recipientValue);
		recipientField.setHint(Component.translatable("screen.runevestiges.economy.recipient_hint"));
		addRenderableWidget(recipientField);

		transferAmountField = new EditBox(
			font,
			formX + amountX,
			fieldY,
			amountWidth,
			20,
			Component.translatable("screen.runevestiges.economy.amount")
		);
		transferAmountField.setMaxLength(19);
		transferAmountField.setFilter(value -> POSITIVE_INTEGER_INPUT.matcher(value).matches());
		transferAmountField.setValue(transferAmountValue);
		transferAmountField.setHint(Component.translatable("screen.runevestiges.economy.amount_hint"));
		addRenderableWidget(transferAmountField);

		addRenderableWidget(Button.builder(
			Component.translatable("screen.runevestiges.economy.transfer"),
			button -> {
				recipientValue = recipientField.getValue();
				transferAmountValue = transferAmountField.getValue();
				sendAction(EconomyService.TRANSFER, 0L, recipientValue, parseAmount(transferAmountValue), 0);
			}
		).bounds(formX + transferX, fieldY, formWidth - transferX, 20).build());

		initOnlinePlayerList(formX, formWidth);
	}

	private void initOnlinePlayerList(int formX, int formWidth) {
		List<String> onlinePlayers = snapshot == null ? List.of() : snapshot.onlinePlayers();
		int selectorY = panelY + walletSelectorY();
		int pageCount = onlinePlayerPageCount();
		onlinePlayersPage = Math.clamp(onlinePlayersPage, 0, pageCount - 1);
		int pageSize = onlinePlayerPageSize();
		if (onlinePlayersPage > 0) {
			addRenderableWidget(Button.builder(
				Component.literal("<"),
				button -> changeOnlinePlayersPage(onlinePlayersPage - 1)
			).bounds(formX + formWidth - 48, selectorY, 20, 18).build());
		}
		if (onlinePlayersPage < pageCount - 1) {
			addRenderableWidget(Button.builder(
				Component.literal(">"),
				button -> changeOnlinePlayersPage(onlinePlayersPage + 1)
			).bounds(formX + formWidth - 24, selectorY, 20, 18).build());
		}
		addRenderableWidget(Button.builder(
			Component.literal("\u21bb"),
			button -> sendAction(EconomyService.OPEN_WALLET, 0L, "", 0L, 0)
		).bounds(formX + formWidth - 72, selectorY, 20, 18)
			.tooltip(net.minecraft.client.gui.components.Tooltip.create(
				Component.translatable("screen.runevestiges.economy.online_player_refresh")
			))
			.build());

		int firstPlayerIndex = onlinePlayersPage * pageSize;
		int visiblePlayers = Math.min(pageSize, onlinePlayers.size() - firstPlayerIndex);
		int columnWidth = (formWidth - 4) / 2;
		int rowY = selectorY + 23;
		for (int index = 0; index < visiblePlayers; index++) {
			int playerIndex = firstPlayerIndex + index;
			String playerName = onlinePlayers.get(playerIndex);
			int column = index % 2;
			int row = index / 2;
			addRenderableWidget(Button.builder(
				Component.literal(playerName),
				button -> {
					recipientValue = playerName;
					if (recipientField != null) {
						recipientField.setValue(playerName);
					}
				}
			).bounds(
				formX + column * (columnWidth + 4),
				rowY + row * 22,
				columnWidth,
				20
			).build());
		}
	}

	private int onlinePlayerPageSize() {
		int visibleRows = Math.max(1, Math.min(3, (panelHeight - walletSelectorY() - 92) / 22));
		return visibleRows * 2;
	}

	private int onlinePlayerPageCount() {
		int playerCount = snapshot == null ? 0 : snapshot.onlinePlayers().size();
		return Math.max(1, (playerCount + onlinePlayerPageSize() - 1) / onlinePlayerPageSize());
	}

	private void changeOnlinePlayersPage(int page) {
		onlinePlayersPage = page;
		init(Minecraft.getInstance(), width, height);
	}


	private void initMarketWidgets() {
		initMarketNavigationWidgets();
		if (offeringListingId != 0L) {
			initOfferWidgets();
			return;
		}
		if (creatingListing) {
			initMarketListingWidgets();
			return;
		}

		if (offersInbox) {
			initOfferInboxWidgets();
			return;
		}

		int firstRowY = panelY + marketListingsY();
		int rowHeight = listingRowHeight();
		if (snapshot != null) {
			clampMarketScrollOffset(snapshot.listings().size());
			int lastIndex = Math.min(
				snapshot.listings().size(),
				marketScrollOffset + visibleMarketRows()
			);
			for (int index = marketScrollOffset; index < lastIndex; index++) {
				EconomyPayloads.Listing listing = snapshot.listings().get(index);
				int rowY = firstRowY + (index - marketScrollOffset) * rowHeight;
				int buttonHeight = Math.min(20, rowHeight - 2);
				int buttonY = rowY + (rowHeight - buttonHeight) / 2;
				if (listing.own()) {
					addListingActionButton(
						listing,
						"screen.runevestiges.economy.cancel_listing",
						EconomyService.CANCEL_LISTING,
						panelX + panelWidth - 78,
						buttonY,
						66,
						buttonHeight
					);
				} else {
					addListingActionButton(
						listing,
						"screen.runevestiges.economy.make_offer",
						EconomyService.CREATE_OFFER,
						panelX + panelWidth - 158,
						buttonY,
						76,
						buttonHeight
					);
					addListingActionButton(
						listing,
						"screen.runevestiges.economy.buy_listing",
						EconomyService.BUY_LISTING,
						panelX + panelWidth - 78,
						buttonY,
						66,
						buttonHeight
					);
				}
			}
		}

		addRenderableWidget(Button.builder(
			Component.literal("<"),
			button -> changeMarketPage(Math.max(0, currentPage() - 1))
		).bounds(panelX + panelWidth / 2 - 72, panelY + panelHeight - 26, 28, 18).build());
		addRenderableWidget(Button.builder(
			Component.literal(">"),
			button -> changeMarketPage(currentPage() + 1)
		).bounds(panelX + panelWidth / 2 + 44, panelY + panelHeight - 26, 28, 18).build());
	}

	private void initMarketNavigationWidgets() {
		int refreshWidth = panelWidth < 360 ? 0 : 84;
		int gap = 4;
		int navigationWidth = panelWidth - 24 - refreshWidth - gap * (refreshWidth == 0 ? 2 : 3);
		int tabWidth = navigationWidth / 3;
		int controlY = marketControlY();
		addRenderableWidget(Button.builder(
			Component.translatable("screen.runevestiges.economy.browse_tab"),
			button -> setMarketView(EconomyService.MARKET_VIEW_BROWSE)
		).bounds(panelX + 12, panelY + controlY, tabWidth, 20).build());
		addRenderableWidget(Button.builder(
			Component.translatable("screen.runevestiges.economy.create_listing_tab"),
			button -> setMarketView(MARKET_VIEW_CREATE)
		).bounds(panelX + 12 + tabWidth + gap, panelY + controlY, tabWidth, 20).build());
		addRenderableWidget(Button.builder(
			Component.translatable("screen.runevestiges.economy.offers_tab"),
			button -> setMarketView(EconomyService.MARKET_VIEW_OFFERS)
		).bounds(panelX + 12 + (tabWidth + gap) * 2, panelY + controlY, tabWidth, 20).build());
		if (refreshWidth > 0 && offeringListingId == 0L && !creatingListing) {
			addRenderableWidget(Button.builder(
				Component.translatable("screen.runevestiges.economy.refresh"),
				button -> sendAction(EconomyService.REFRESH, 0L, "", 0L, currentPage())
			).bounds(panelX + panelWidth - 12 - refreshWidth, panelY + controlY, refreshWidth, 20).build());
		}
	}

	private void addListingActionButton(
		EconomyPayloads.Listing listing,
		String labelKey,
		int action,
		int x,
		int y,
		int buttonWidth,
		int buttonHeight
	) {
		addRenderableWidget(Button.builder(
			Component.translatable(labelKey),
			button -> {
				if (action == EconomyService.CREATE_OFFER) {
					offeringListingId = listing.id();
					offerAmountValue = "";
					init(Minecraft.getInstance(), width, height);
				} else {
					marketScrollOffset = 0;
					sendAction(action, listing.id(), "", 0L, currentPage());
				}
			}
		).bounds(x, y, buttonWidth, buttonHeight).build());
	}

	private void initOfferInboxWidgets() {
		int firstRowY = panelY + marketListingsY();
		int rowHeight = listingRowHeight();
		if (snapshot != null) {
			clampMarketScrollOffset(snapshot.offers().size());
			int lastIndex = Math.min(snapshot.offers().size(), marketScrollOffset + visibleMarketRows());
			for (int index = marketScrollOffset; index < lastIndex; index++) {
				EconomyPayloads.Offer offer = snapshot.offers().get(index);
				int rowY = firstRowY + (index - marketScrollOffset) * rowHeight;
				int buttonHeight = Math.min(20, rowHeight - 2);
				addRenderableWidget(Button.builder(
					Component.translatable("screen.runevestiges.economy.accept_offer"),
					button -> {
						marketScrollOffset = 0;
						sendAction(EconomyService.ACCEPT_OFFER, offer.id(), "", 0L, currentPage());
					}
				).bounds(panelX + panelWidth - 120, rowY + (rowHeight - buttonHeight) / 2, 58, buttonHeight).build());
				addRenderableWidget(Button.builder(
					Component.translatable("screen.runevestiges.economy.decline_offer"),
					button -> {
						marketScrollOffset = 0;
						sendAction(EconomyService.DECLINE_OFFER, offer.id(), "", 0L, currentPage());
					}
				).bounds(panelX + panelWidth - 58, rowY + (rowHeight - buttonHeight) / 2, 54, buttonHeight).build());
			}
		}
		addMarketPaginationWidgets();
	}

	private void initOfferWidgets() {
		int fieldY = offerFieldY();
		offerAmountField = new EditBox(
			font,
			panelX + (panelWidth - 120) / 2,
			panelY + fieldY,
			120,
			20,
			Component.translatable("screen.runevestiges.economy.offer_amount")
		);
		offerAmountField.setMaxLength(19);
		offerAmountField.setFilter(value -> POSITIVE_INTEGER_INPUT.matcher(value).matches());
		offerAmountField.setValue(offerAmountValue);
		offerAmountField.setHint(Component.translatable("screen.runevestiges.economy.amount_hint"));
		addRenderableWidget(offerAmountField);
		submitOfferButton = Button.builder(
			Component.translatable("screen.runevestiges.economy.send_offer"),
			button -> {
				offerAmountValue = offerAmountField.getValue();
				offerRequestPending = sendAction(
					EconomyService.CREATE_OFFER,
					offeringListingId,
					"",
					parseAmount(offerAmountValue),
					currentPage()
				);
			}
		).bounds(
			panelX + (panelWidth - 160) / 2,
			panelY + fieldY + 26,
			160,
			22
		).build();
		submitOfferButton.active = false;
		addRenderableWidget(submitOfferButton);
		addRenderableWidget(Button.builder(
			Component.translatable("screen.runevestiges.economy.back_to_listings"),
			button -> setMarketView(EconomyService.MARKET_VIEW_BROWSE)
		).bounds(
			panelX + (panelWidth - 160) / 2,
			panelY + fieldY + 52,
			160,
			20
		).build());
	}

	private void addMarketPaginationWidgets() {
		addRenderableWidget(Button.builder(
			Component.literal("<"),
			button -> changeMarketPage(Math.max(0, currentPage() - 1))
		).bounds(panelX + panelWidth / 2 - 72, panelY + panelHeight - 26, 28, 18).build());
		addRenderableWidget(Button.builder(
			Component.literal(">"),
			button -> changeMarketPage(currentPage() + 1)
		).bounds(panelX + panelWidth / 2 + 44, panelY + panelHeight - 26, 28, 18).build());
	}

	private void initMarketListingWidgets() {
		listingPriceField = new EditBox(
			font,
			panelX + 12,
			marketPriceControlY(),
			92,
			20,
			Component.translatable("screen.runevestiges.economy.price")
		);
		listingPriceField.setMaxLength(19);
		listingPriceField.setFilter(value -> POSITIVE_INTEGER_INPUT.matcher(value).matches());
		listingPriceField.setValue(listingPriceValue);
		listingPriceField.setHint(Component.translatable("screen.runevestiges.economy.amount_hint"));
		addRenderableWidget(listingPriceField);

		listSelectedButton = Button.builder(
			Component.translatable("screen.runevestiges.economy.sell_selected_item"),
			button -> {
				listingPriceValue = listingPriceField.getValue();
				listingRequestPending = sendAction(
					EconomyService.SELL_INVENTORY_SLOT,
					selectedInventorySlot,
					"",
					parseAmount(listingPriceValue),
					0
				);
			}
		).bounds(panelX + 112, marketPriceControlY(), panelWidth - 124, 20).build();
		listSelectedButton.active = false;
		addRenderableWidget(listSelectedButton);
		initInventoryGridWidgets();
	}

	private void initServerShopWidgets() {
		listSelectedButton = Button.builder(
			Component.translatable("screen.runevestiges.economy.sell_to_server"),
			button -> {
				listingRequestPending = sendAction(
					EconomyService.SELL_TO_SERVER,
					selectedInventorySlot,
					"",
					0L,
					0
				);
			}
		).bounds(panelX + 12, panelY + shopSellButtonY(), panelWidth - 24, 20).build();
		listSelectedButton.active = false;
		addRenderableWidget(listSelectedButton);
		initInventoryGridWidgets();
	}

	private void initInventoryGridWidgets() {
		int slotSize = inventorySlotSize();
		int slotSpacing = inventorySlotSpacing();
		int inventoryX = panelX + (panelWidth - INVENTORY_COLUMNS * slotSpacing) / 2;
		int inventoryY = panelY + inventoryGridY();
		for (int slot = 0; slot < INVENTORY_SLOT_COUNT; slot++) {
			int inventorySlot = slot;
			int column = slot % INVENTORY_COLUMNS;
			int row = slot / INVENTORY_COLUMNS;
			addRenderableWidget(new InventorySlotWidget(
				inventoryX + column * slotSpacing,
				inventoryY + row * slotSpacing,
				slotSize,
				inventorySlot
			));
		}
	}

	private void changeTab(int selectedTab) {
		if (selectedTab == EconomyService.HOME_TAB) {
			open(EconomyService.HOME_TAB);
			return;
		}
		open(selectedTab);
	}

	private void setMarketView(int selectedView) {
		if (listingPriceField != null) {
			listingPriceValue = listingPriceField.getValue();
		}
		if (offerAmountField != null) {
			offerAmountValue = offerAmountField.getValue();
		}
		offeringListingId = 0L;
		marketScrollOffset = 0;
		creatingListing = selectedView == MARKET_VIEW_CREATE;
		offersInbox = selectedView == EconomyService.MARKET_VIEW_OFFERS;
		if (selectedView != MARKET_VIEW_CREATE) {
			sendAction(EconomyService.REFRESH, 0L, "", 0L, 0);
		}
		init(Minecraft.getInstance(), width, height);
	}

	private int navigationButtonWidth() {
		return (panelWidth - 30) / 4;
	}

	private int walletFormWidth() {
		return Math.min(396, panelWidth - 24);
	}

	private int walletSelectorY() {
		return panelHeight < 300 ? 134 : 139;
	}

	private int walletRecipientWidth() {
		return walletFormWidth() * 44 / 100;
	}

	private int walletAmountWidth() {
		return walletFormWidth() * 27 / 100;
	}

	private int navigationX(int index) {
		return panelX + 12 + index * (navigationButtonWidth() + 2);
	}

	private Component navigationLabel(String fullKey, String shortKey) {
		return Component.translatable(
			"screen.runevestiges.economy."
				+ (navigationButtonWidth() < 82 ? shortKey : fullKey)
		);
	}

	private boolean compactHeight() {
		return panelHeight < PANEL_HEIGHT - 4;
	}

	private int inventoryGridY() {
		if (panelHeight < 300) {
			return tab == EconomyService.MARKET_TAB ? 120 : 112;
		}
		return panelHeight < 360 ? 132 : compactHeight() ? 142 : INVENTORY_GRID_Y;
	}

	private int inventoryLabelY() {
		if (panelHeight < 300) {
			return tab == EconomyService.MARKET_TAB ? 110 : 94;
		}
		return panelHeight < 360 ? 114 : compactHeight() ? 124 : INVENTORY_LABEL_Y;
	}

	private int inventorySlotSize() {
		return panelHeight < 300 ? 14 : INVENTORY_SLOT_SIZE;
	}

	private int inventorySlotSpacing() {
		return panelHeight < 300 ? 16 : INVENTORY_SLOT_SPACING;
	}

	private int marketControlY() {
		return panelHeight < 300 ? 88 : MARKET_CONTROL_Y;
	}

	private int marketListingsY() {
		return marketControlY() + 30;
	}

	private int listingRowHeight() {
		return MARKET_ROW_HEIGHT;
	}

	private int visibleMarketRows() {
		int availableHeight = panelHeight - marketListingsY() - 54;
		return Math.max(1, availableHeight / listingRowHeight());
	}

	private void clampMarketScrollOffset(int entryCount) {
		marketScrollOffset = Math.clamp(marketScrollOffset, 0, Math.max(0, entryCount - visibleMarketRows()));
	}

	private void changeMarketPage(int page) {
		marketScrollOffset = 0;
		sendAction(EconomyService.REFRESH, 0L, "", 0L, page);
	}

	private int marketPriceControlY() {
		if (panelHeight < 300) {
			return panelY + panelHeight - 30;
		}
		return panelY + Math.min(inventoryGridY() + 100, panelHeight - 76);
	}

	private int shopSellButtonY() {
		if (panelHeight < 360) {
			return panelHeight - 30;
		}
		return Math.min(shopDetailsY() + 66, panelHeight - 76);
	}

	private int shopDetailsY() {
		if (panelHeight < 300) {
			return panelHeight - 44;
		}
		if (panelHeight < 360) {
			return panelHeight - 78;
		}
		return inventoryGridY() + 86;
	}

	private int offerFieldY() {
		return panelHeight < 300 ? panelHeight - 80 : 174;
	}

	private boolean sendAction(int action, long listingId, String text, long amount, int page) {
		int actionTab = action == EconomyService.OPEN_MARKET
				|| action == EconomyService.SELL_HELD_ITEM
				|| action == EconomyService.SELL_INVENTORY_SLOT
				|| action == EconomyService.BUY_LISTING
				|| action == EconomyService.CANCEL_LISTING
				|| action == EconomyService.CREATE_OFFER
				|| action == EconomyService.ACCEPT_OFFER
				|| action == EconomyService.DECLINE_OFFER
				|| (action == EconomyService.REFRESH && tab == EconomyService.MARKET_TAB)
			? EconomyService.MARKET_TAB
			: action == EconomyService.OPEN_SERVER_SHOP
					|| action == EconomyService.SELL_TO_SERVER
					|| (action == EconomyService.REFRESH && tab == EconomyService.SERVER_SHOP_TAB)
				? EconomyService.SERVER_SHOP_TAB
				: EconomyService.WALLET_TAB;
		int actionView = tab == EconomyService.MARKET_TAB && offersInbox
			? EconomyService.MARKET_VIEW_OFFERS
			: EconomyService.MARKET_VIEW_BROWSE;
		if (!EconomyClientNetworking.send(action(action, listingId, text, amount, page, actionTab, actionView))) {
			localStatus = Component.translatable("message.runevestiges.economy.server_unavailable");
			return false;
		}
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (listSelectedButton != null
			&& tab == EconomyService.MARKET_TAB
			&& listingPriceField != null) {
			listingPriceValue = listingPriceField.getValue();
			ItemStack selected = selectedStack();
			listSelectedButton.active = !listingRequestPending
				&& selected != null
				&& parseAmount(listingPriceValue) > 0L;
		} else if (listSelectedButton != null && tab == EconomyService.SERVER_SHOP_TAB) {
			listSelectedButton.active = !listingRequestPending && selectedStack() != null;
		}
		if (submitOfferButton != null && offerAmountField != null) {
			offerAmountValue = offerAmountField.getValue();
			submitOfferButton.active = !offerRequestPending && parseAmount(offerAmountValue) > 0L;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab != EconomyService.MARKET_TAB
			|| offeringListingId != 0L
			|| creatingListing
			|| snapshot == null
			|| scrollY == 0.0
			|| mouseY < panelY + marketListingsY()
			|| mouseY >= panelY + panelHeight - 52) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		int entryCount = offersInbox ? snapshot.offers().size() : snapshot.listings().size();
		int previousOffset = marketScrollOffset;
		int direction = scrollY < 0.0 ? 1 : -1;
		marketScrollOffset = Math.clamp(
			marketScrollOffset + direction,
			0,
			Math.max(0, entryCount - visibleMarketRows())
		);
		if (previousOffset != marketScrollOffset) {
			init(Minecraft.getInstance(), width, height);
		}
		return true;
	}

	private ItemStack selectedStack() {
		if (Minecraft.getInstance().player == null || selectedInventorySlot < 0) {
			return null;
		}
		var inventory = Minecraft.getInstance().player.getInventory();
		if (selectedInventorySlot >= inventory.items.size()) {
			return null;
		}
		ItemStack stack = inventory.getItem(selectedInventorySlot);
		return stack.isEmpty() ? null : stack;
	}

	private int currentPage() {
		return snapshot == null ? 0 : snapshot.page();
	}

	private static long parseAmount(String rawAmount) {
		try {
			return Long.parseLong(rawAmount);
		} catch (NumberFormatException exception) {
			return 0L;
		}
	}

	private static EconomyPayloads.Action action(
		int action,
		long listingId,
		String text,
		long amount,
		int page,
		int tab
	) {
		return action(action, listingId, text, amount, page, tab, EconomyService.MARKET_VIEW_BROWSE);
	}

	private static EconomyPayloads.Action action(
		int action,
		long listingId,
		String text,
		long amount,
		int page,
		int tab,
		int view
	) {
		return new EconomyPayloads.Action(action, listingId, text, amount, page, tab, view);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(panelX - 3, panelY - 3, panelX + panelWidth + 3, panelY + panelHeight + 3, 0xff080a10);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xc5161b2c);
		graphics.fill(panelX + 6, panelY + 6, panelX + panelWidth - 6, panelY + 28, 0xff242c42);
		graphics.fill(panelX + 14, panelY + 30, panelX + panelWidth - 14, panelY + 31, 0xff8e7441);
		graphics.fill(panelX + 14, panelY + 89, panelX + panelWidth - 14, panelY + 90, 0xff4a536b);
		graphics.fill(panelX + 8, panelY + 9, panelX + 10, panelY + 25, 0xffc8a65c);
		graphics.fill(panelX + panelWidth - 10, panelY + 9, panelX + panelWidth - 8, panelY + 25, 0xffc8a65c);
		graphics.drawCenteredString(font, title, width / 2, panelY + 12, 0xffffe1a0);
		drawNavigationAccent(graphics);
		int balanceWidth = Math.min(224, panelWidth - 24);
		graphics.fill(
			panelX + (panelWidth - balanceWidth) / 2,
			panelY + BALANCE_Y - 2,
			panelX + (panelWidth + balanceWidth) / 2,
			panelY + BALANCE_Y + 19,
			0xff20263a
		);
		graphics.fill(
			panelX + (panelWidth - balanceWidth) / 2,
			panelY + BALANCE_Y - 2,
			panelX + (panelWidth + balanceWidth) / 2,
			panelY + BALANCE_Y - 1,
			0xff8e7441
		);
		if (snapshot == null) {
			graphics.drawCenteredString(
				font,
				Component.translatable("screen.runevestiges.economy.loading"),
				width / 2,
				panelY + 74,
				0xffffffff
			);
		} else {
			drawCenteredFit(
				graphics,
				Component.translatable("screen.runevestiges.economy.balance", snapshot.balance()),
				panelX + 12,
				panelY + BALANCE_Y + 5,
				panelWidth - 24,
				0xffffd879
			);
			if (tab == EconomyService.WALLET_TAB) {
				renderWalletContent(graphics);
			} else if (tab == EconomyService.MARKET_TAB) {
				renderMarketContent(graphics);
			} else if (tab == EconomyService.SERVER_SHOP_TAB) {
				renderServerShopContent(graphics);
			} else {
				renderHomeContent(graphics);
			}
		}

		Component status = localStatus;
		if (status == null && snapshot != null && !snapshot.message().isEmpty()) {
			status = Component.translatable(snapshot.message());
		}
		if (status != null) {
			int statusColor = status.equals(Component.translatable("message.runevestiges.economy.success"))
				? 0xff9be0bc
				: 0xffffa1a1;
			graphics.fill(
				panelX + 10,
				panelY + panelHeight - 47,
				panelX + panelWidth - 10,
				panelY + panelHeight - 32,
				0xe620263a
			);
			drawCenteredFit(
				graphics,
				status,
				panelX + 12,
				panelY + panelHeight - 42,
				panelWidth - 24,
				statusColor
			);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x28101830);
	}

	private void drawNavigationAccent(GuiGraphics graphics) {
		int x;
		if (tab == EconomyService.WALLET_TAB) {
			x = navigationX(1);
		} else if (tab == EconomyService.MARKET_TAB) {
			x = navigationX(2);
		} else if (tab == EconomyService.SERVER_SHOP_TAB) {
			x = navigationX(3);
		} else {
			return;
		}
		graphics.fill(
			x + 4,
			panelY + NAVIGATION_Y + 20,
			x + navigationButtonWidth() - 4,
			panelY + NAVIGATION_Y + 22,
			0xffffd879
		);
		if (tab == EconomyService.MARKET_TAB) {
			int refreshWidth = panelWidth < 360 ? 0 : 84;
			int gap = 4;
			int navigationWidth = panelWidth - 24 - refreshWidth - gap * (refreshWidth == 0 ? 2 : 3);
			int tabWidth = navigationWidth / 3;
			int marketTabIndex = offeringListingId != 0L ? 0 : creatingListing ? 1 : offersInbox ? 2 : 0;
			int marketTabX = panelX + 12 + (tabWidth + gap) * marketTabIndex;
			graphics.fill(
				marketTabX + 4,
				panelY + marketControlY() + 20,
				marketTabX + tabWidth - 4,
				panelY + marketControlY() + 22,
				0xffffd879
			);
		}
	}

	private void drawCenteredFit(
		GuiGraphics graphics,
		Component text,
		int x,
		int y,
		int maxWidth,
		int color
	) {
		String clipped = font.plainSubstrByWidth(text.getString(), maxWidth);
		graphics.drawCenteredString(font, clipped, x + maxWidth / 2, y, color);
	}

	private void drawCenteredWrapped(
		GuiGraphics graphics,
		Component text,
		int x,
		int y,
		int maxWidth,
		int color,
		int lineHeight,
		int maxLines
	) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.getString().split("\\s+")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (font.width(candidate) <= maxWidth) {
				line.setLength(0);
				line.append(candidate);
			} else if (line.isEmpty()) {
				lines.add(font.plainSubstrByWidth(word, maxWidth));
			} else {
				lines.add(line.toString());
				line.setLength(0);
				if (font.width(word) > maxWidth) {
					lines.add(font.plainSubstrByWidth(word, maxWidth));
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
				visible = font.plainSubstrByWidth(visible, Math.max(1, maxWidth - font.width("..."))) + "...";
			}
			graphics.drawCenteredString(font, visible, x + maxWidth / 2, y + index * lineHeight, color);
		}
	}

	private void renderHomeContent(GuiGraphics graphics) {
		drawCenteredFit(
			graphics,
			Component.translatable("screen.runevestiges.economy.home_heading"),
			panelX + 16,
			panelY + 101,
			panelWidth - 32,
			0xffffd879
		);
		drawCenteredWrapped(
			graphics,
			Component.translatable("screen.runevestiges.economy.home_description"),
			panelX + 20,
			panelY + 118,
			panelWidth - 40,
			0xffe0e4ef,
			11,
			2
		);
		graphics.fill(
			panelX + panelWidth / 2 - 24,
			panelY + 132,
			panelX + panelWidth / 2 + 24,
			panelY + 133,
			0xff8e7441
		);
	}

	private void renderWalletContent(GuiGraphics graphics) {
		int formX = panelX + (panelWidth - walletFormWidth()) / 2;
		int formWidth = walletFormWidth();
		int recipientWidth = walletRecipientWidth();
		int amountWidth = walletAmountWidth();
		graphics.drawCenteredString(
			font,
			Component.translatable("screen.runevestiges.economy.recipient"),
			formX + recipientWidth / 2,
			panelY + 94,
			0xffe0e4ef
		);
		graphics.fill(
			formX,
			panelY + 90,
			formX + recipientWidth,
			panelY + 91,
			0xff8e7441
		);
		graphics.fill(
			formX + recipientWidth + 8,
			panelY + 90,
			formX + recipientWidth + 8 + amountWidth,
			panelY + 91,
			0xff8e7441
		);
		graphics.drawCenteredString(
			font,
			Component.translatable("screen.runevestiges.economy.amount"),
			formX + recipientWidth + 8 + amountWidth / 2,
			panelY + 94,
			0xffe0e4ef
		);
		int onlineCount = snapshot == null ? 0 : snapshot.onlinePlayers().size();
		int firstPlayer = onlinePlayersPage * onlinePlayerPageSize();
		Component onlinePlayersLabel = onlineCount == 0
			? Component.translatable("screen.runevestiges.economy.online_players_empty")
			: Component.translatable(
				"screen.runevestiges.economy.online_players",
				firstPlayer + 1,
				Math.min(onlineCount, firstPlayer + onlinePlayerPageSize()),
				onlineCount
			);
		drawCenteredFit(
			graphics,
			onlinePlayersLabel,
			formX,
			panelY + walletSelectorY() + 4,
			Math.max(1, formWidth - 80),
			0xffe0e4ef
		);
		if (panelHeight >= 300) {
			int visibleRows = onlinePlayerPageSize() / 2;
			drawCenteredWrapped(
				graphics,
				Component.translatable("screen.runevestiges.economy.transfer_hint"),
				panelX + 12,
				panelY + walletSelectorY() + 27 + visibleRows * 22,
				panelWidth - 24,
				0xffbec8da,
				11,
				2
			);
		}
	}

	private void renderMarketContent(GuiGraphics graphics) {
		if (offeringListingId != 0L) {
			EconomyPayloads.Listing listing = snapshot == null
				? null
				: snapshot.listings().stream()
					.filter(candidate -> candidate.id() == offeringListingId)
					.findFirst()
					.orElse(null);
			Component details = listing == null
				? Component.translatable("screen.runevestiges.economy.offer_listing_missing")
				: Component.translatable(
					"screen.runevestiges.economy.offer_target",
					listing.item().getHoverName(),
					listing.sellerName(),
					listing.price()
				);
			drawCenteredWrapped(
				graphics,
				details,
				panelX + 16,
				panelY + (panelHeight < 300 ? 110 : 126),
				panelWidth - 32,
				0xffe0e4ef,
				12,
				2
			);
			graphics.drawCenteredString(
				font,
				Component.translatable("screen.runevestiges.economy.offer_amount"),
				width / 2,
				panelY + offerFieldY() - 14,
				0xffe0e4ef
			);
			if (panelHeight >= 300) {
				drawCenteredFit(
					graphics,
					Component.translatable("screen.runevestiges.economy.offer_funds_not_reserved"),
					panelX + 16,
					panelY + 270,
					panelWidth - 32,
					0xffbec8da
				);
			}
			return;
		}

		if (creatingListing) {
			drawCenteredWrapped(
				graphics,
				Component.translatable("screen.runevestiges.economy.select_item"),
				panelX + 12,
				panelY + inventoryLabelY(),
				panelWidth - 24,
				0xffe0e4ef,
				10,
				1
			);
			renderInventoryGrid(graphics);
			int controlY = marketPriceControlY();
			graphics.drawCenteredString(
				font,
				Component.translatable("screen.runevestiges.economy.price"),
				panelX + 58,
				controlY - 12,
				0xffe0e4ef
			);
			return;
		}

		if (offersInbox) {
			renderOfferInbox(graphics);
			return;
		}

		if (snapshot == null || snapshot.listings().isEmpty()) {
			drawCenteredWrapped(
				graphics,
				Component.translatable("screen.runevestiges.economy.no_listings"),
				panelX + 16,
				panelY + marketListingsY() + 28,
				panelWidth - 32,
				0xffd5daea,
				11,
				2
			);
			return;
		}

		int firstRowY = panelY + marketListingsY();
		int rowHeight = listingRowHeight();
		clampMarketScrollOffset(snapshot.listings().size());
		graphics.enableScissor(
			panelX + 6,
			firstRowY,
			panelX + panelWidth - 6,
			panelY + panelHeight - 52
		);
		int lastIndex = Math.min(snapshot.listings().size(), marketScrollOffset + visibleMarketRows());
		for (int index = marketScrollOffset; index < lastIndex; index++) {
			EconomyPayloads.Listing listing = snapshot.listings().get(index);
			int rowY = firstRowY + (index - marketScrollOffset) * rowHeight;
			graphics.fill(
				panelX + 8,
				rowY,
				panelX + panelWidth - 8,
				rowY + rowHeight - 1,
				index % 2 == 0 ? 0xff293149 : 0xff232b3f
			);
			graphics.renderItem(listing.item(), panelX + 12, rowY + (rowHeight - 16) / 2);
			if (listing.item().getCount() > 1) {
				graphics.renderItemDecorations(font, listing.item(), panelX + 12, rowY + (rowHeight - 16) / 2);
			}
			int descriptionX = panelX + 34;
			int reservedWidth = listing.own() ? 84 : 158;
			int descriptionWidth = Math.max(1, panelWidth - 34 - reservedWidth - 6);
			Component itemDescription = Component.translatable(
				"screen.runevestiges.economy.listing_item",
				listing.item().getHoverName(),
				listing.item().getCount(),
				listing.own()
					? Component.translatable("screen.runevestiges.economy.your_listing")
					: listing.sellerName()
			);
			graphics.drawString(
				font,
				font.plainSubstrByWidth(itemDescription.getString(), descriptionWidth),
				descriptionX,
				rowY + 5,
				0xfff1f1f1
			);
			Component priceDescription = Component.translatable(
				"screen.runevestiges.economy.listing_price",
				listing.price()
			);
			graphics.drawString(
				font,
				font.plainSubstrByWidth(priceDescription.getString(), descriptionWidth),
				descriptionX,
				rowY + rowHeight - 13,
				0xffffd879
			);
		}
		graphics.disableScissor();

		renderMarketScrollHint(graphics, snapshot.listings().size());
		renderMarketPageNumber(graphics);
	}

	private void renderOfferInbox(GuiGraphics graphics) {
		if (snapshot == null) {
			drawCenteredFit(
				graphics,
				Component.translatable("screen.runevestiges.economy.loading"),
				panelX + 12,
				panelY + marketListingsY() + 20,
				panelWidth - 24,
				0xffd5daea
			);
			return;
		}
		if (snapshot.offers().isEmpty()) {
			drawCenteredWrapped(
				graphics,
				Component.translatable("screen.runevestiges.economy.no_offers"),
				panelX + 16,
				panelY + marketListingsY() + 28,
				panelWidth - 32,
				0xffd5daea,
				11,
				2
			);
			renderMarketPageNumber(graphics);
			return;
		}

		int firstRowY = panelY + marketListingsY();
		int rowHeight = listingRowHeight();
		clampMarketScrollOffset(snapshot.offers().size());
		graphics.enableScissor(
			panelX + 6,
			firstRowY,
			panelX + panelWidth - 6,
			panelY + panelHeight - 52
		);
		int lastIndex = Math.min(snapshot.offers().size(), marketScrollOffset + visibleMarketRows());
		for (int index = marketScrollOffset; index < lastIndex; index++) {
			EconomyPayloads.Offer offer = snapshot.offers().get(index);
			int rowY = firstRowY + (index - marketScrollOffset) * rowHeight;
			graphics.fill(
				panelX + 8,
				rowY,
				panelX + panelWidth - 8,
				rowY + rowHeight - 1,
				index % 2 == 0 ? 0xff293149 : 0xff232b3f
			);
			graphics.renderItem(offer.item(), panelX + 12, rowY + (rowHeight - 16) / 2);
			int descriptionX = panelX + 34;
			int descriptionWidth = Math.max(1, panelWidth - 162);
			Component itemDescription = Component.translatable(
				"screen.runevestiges.economy.offer_item",
				offer.item().getHoverName()
			);
			graphics.drawString(
				font,
				font.plainSubstrByWidth(itemDescription.getString(), descriptionWidth),
				descriptionX,
				rowY + 5,
				0xfff1f1f1
			);
			Component offerDescription = Component.translatable(
				"screen.runevestiges.economy.offer_from",
				offer.buyerName(),
				offer.amount()
			);
			graphics.drawString(
				font,
				font.plainSubstrByWidth(offerDescription.getString(), descriptionWidth),
				descriptionX,
				rowY + rowHeight - 13,
				0xffffd879
			);
		}
		graphics.disableScissor();
		renderMarketScrollHint(graphics, snapshot.offers().size());
		renderMarketPageNumber(graphics);
	}

	private void renderMarketScrollHint(GuiGraphics graphics, int entryCount) {
		if (entryCount <= visibleMarketRows()) {
			return;
		}
		drawCenteredFit(
			graphics,
			Component.translatable(
				"screen.runevestiges.economy.market_scroll",
				marketScrollOffset + 1,
				Math.min(entryCount, marketScrollOffset + visibleMarketRows()),
				entryCount
			),
			panelX + 12,
			panelY + panelHeight - 20,
			Math.max(1, panelWidth / 2 - 100),
			0xffbec8da
		);
	}

	private void renderMarketPageNumber(GuiGraphics graphics) {
		if (snapshot == null) {
			return;
		}
		drawCenteredFit(
			graphics,
			Component.translatable(
				"screen.runevestiges.economy.page",
				snapshot.page() + 1,
				snapshot.pageCount()
			),
			panelX + panelWidth / 2 - 40,
			panelY + panelHeight - 20,
			80,
			0xffe0e4ef
		);
	}

	private void renderServerShopContent(GuiGraphics graphics) {
		drawCenteredWrapped(
			graphics,
			Component.translatable("screen.runevestiges.economy.shop_select_item"),
			panelX + 12,
			panelY + inventoryLabelY(),
			panelWidth - 24,
			0xffe0e4ef,
			10,
			1
		);
		renderInventoryGrid(graphics);
		ItemStack selected = selectedStack();
		Component quote = selected == null
			? Component.translatable("screen.runevestiges.economy.shop_quote_empty")
			: Component.translatable(
				"screen.runevestiges.economy.shop_quote",
				selected.getHoverName(),
				selected.getCount(),
				ShopPricing.unitPrice(selected),
				ShopPricing.totalPrice(selected)
			);
		int detailsY = shopDetailsY();
		int detailsTop = detailsY - 5;
		int detailsBottom = panelHeight < 300 ? panelHeight - 34
			: panelHeight < 360 ? detailsY + 18 : detailsY + 50;
		graphics.fill(
			panelX + 12,
			panelY + detailsTop,
			panelX + panelWidth - 12,
			panelY + detailsBottom,
			0x88232a3d
		);
		graphics.fill(
			panelX + 12,
			panelY + detailsTop,
			panelX + panelWidth - 12,
			panelY + detailsTop + 1,
			0xff8e7441
		);
		drawCenteredFit(graphics, quote, panelX + 20, panelY + detailsY, panelWidth - 40, 0xffffd879);
		if (panelHeight < 360) {
			return;
		}
		drawCenteredWrapped(
			graphics,
			Component.translatable("screen.runevestiges.economy.shop_price_tiers"),
			panelX + 16,
			panelY + detailsY + 17,
			panelWidth - 32,
			0xffbec8da,
			10,
			1
		);
		drawCenteredWrapped(
			graphics,
			Component.translatable("screen.runevestiges.economy.shop_price_exceptions"),
			panelX + 16,
			panelY + detailsY + 30,
			panelWidth - 32,
			0xffbec8da,
			10,
			2
		);
	}

	private void renderInventoryGrid(GuiGraphics graphics) {
		if (Minecraft.getInstance().player == null) {
			return;
		}

		int slotSize = inventorySlotSize();
		int slotSpacing = inventorySlotSpacing();
		int inventoryX = panelX + (panelWidth - INVENTORY_COLUMNS * slotSpacing) / 2;
		int inventoryY = panelY + inventoryGridY();
		graphics.fill(
			inventoryX - 5,
			inventoryY - 5,
			inventoryX + INVENTORY_COLUMNS * slotSpacing - 2,
			inventoryY + 4 * slotSpacing - 2,
			0xff252c40
		);
		graphics.fill(
			inventoryX - 5,
			inventoryY - 5,
			inventoryX + INVENTORY_COLUMNS * slotSpacing - 2,
			inventoryY - 4,
			0xff8e7441
		);
		for (int slot = 0; slot < INVENTORY_SLOT_COUNT; slot++) {
			int column = slot % INVENTORY_COLUMNS;
			int row = slot / INVENTORY_COLUMNS;
			int x = inventoryX + column * slotSpacing;
			int y = inventoryY + row * slotSpacing;
			ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(slot);
			if (slot == selectedInventorySlot) {
				graphics.fill(
					x - 1,
					y - 1,
					x + slotSize + 1,
					y + slotSize + 1,
					0xffffd86b
				);
			}
			graphics.fill(x, y, x + slotSize, y + slotSize, 0xff111625);
			graphics.renderItem(stack, x + 1, y + 1);
			if (!stack.isEmpty() && stack.getCount() > 1) {
				graphics.renderItemDecorations(font, stack, x + 1, y + 1);
			}
		}
	}

	private final class InventorySlotWidget extends AbstractWidget {
		private final int inventorySlot;

		private InventorySlotWidget(int x, int y, int slotSize, int inventorySlot) {
			super(
				x,
				y,
				slotSize,
				slotSize,
				Component.translatable("screen.runevestiges.economy.select_item")
			);
			this.inventorySlot = inventorySlot;
			if (Minecraft.getInstance().player != null) {
				ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(inventorySlot);
				active = !stack.isEmpty();
				if (!stack.isEmpty()) {
					setTooltip(net.minecraft.client.gui.components.Tooltip.create(stack.getHoverName()));
				}
			}
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			if (active) {
				selectedInventorySlot = inventorySlot;
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	@Override
	public void onClose() {
		if (current == this) {
			current = null;
		}
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
