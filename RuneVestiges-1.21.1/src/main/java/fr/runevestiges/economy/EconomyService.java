package fr.runevestiges.economy;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class EconomyService {
	public static final int WALLET_TAB = 0;
	public static final int MARKET_TAB = 1;
	public static final int HOME_TAB = 2;
	public static final int SERVER_SHOP_TAB = 3;
	public static final int PAGE_SIZE = 5;
	public static final int MAX_LISTINGS = 1_000;
	public static final int MAX_LISTINGS_PER_PLAYER = 50;
	public static final int MAX_OFFERS_PER_LISTING = 20;
	public static final int MAX_OFFERS = 5_000;
	public static final long STARTER_BONUS = 100L;
	public static final int MARKET_VIEW_BROWSE = 0;
	public static final int MARKET_VIEW_OFFERS = 1;

	public static final int OPEN_WALLET = 0;
	public static final int OPEN_MARKET = 1;
	public static final int REFRESH = 2;
	public static final int SELL_HELD_ITEM = 3;
	public static final int BUY_LISTING = 4;
	public static final int CANCEL_LISTING = 5;
	public static final int TRANSFER = 6;
	public static final int OPEN_HOME = 7;
	public static final int SELL_INVENTORY_SLOT = 8;
	public static final int SELL_TO_SERVER = 9;
	public static final int OPEN_SERVER_SHOP = 10;
	public static final int CREATE_OFFER = 11;
	public static final int ACCEPT_OFFER = 12;
	public static final int DECLINE_OFFER = 13;

	private EconomyService() {
	}

	public static Wallet wallet(MinecraftServer server, UUID playerId) {
		return EconomyData.get(server).wallet(playerId);
	}

	public static Result addFunds(MinecraftServer server, UUID playerId, long amount) {
		if (amount <= 0L) {
			return Result.INVALID_AMOUNT;
		}
		EconomyData data = EconomyData.get(server);
		long balance = data.balance(playerId);
		if (Long.MAX_VALUE - balance < amount) {
			return Result.BALANCE_LIMIT;
		}
		data.setBalance(playerId, balance + amount);
		return Result.SUCCESS;
	}

	public static boolean grantStarterBonus(MinecraftServer server, UUID playerId) {
		return EconomyData.get(server).grantStarterBonus(playerId, STARTER_BONUS);
	}

	public static Result withdrawFunds(MinecraftServer server, UUID playerId, long amount) {
		if (amount <= 0L) {
			return Result.INVALID_AMOUNT;
		}
		EconomyData data = EconomyData.get(server);
		long balance = data.balance(playerId);
		if (balance < amount) {
			return Result.INSUFFICIENT_FUNDS;
		}
		data.setBalance(playerId, balance - amount);
		return Result.SUCCESS;
	}

	public static Result transfer(
		MinecraftServer server,
		UUID senderId,
		UUID recipientId,
		long amount
	) {
		if (amount <= 0L) {
			return Result.INVALID_AMOUNT;
		}
		if (senderId.equals(recipientId)) {
			return Result.SELF_TRANSFER;
		}

		EconomyData data = EconomyData.get(server);
		long senderBalance = data.balance(senderId);
		long recipientBalance = data.balance(recipientId);
		if (senderBalance < amount) {
			return Result.INSUFFICIENT_FUNDS;
		}
		if (Long.MAX_VALUE - recipientBalance < amount) {
			return Result.BALANCE_LIMIT;
		}

		data.setBalances(
			senderId,
			senderBalance - amount,
			recipientId,
			recipientBalance + amount
		);
		return Result.SUCCESS;
	}

	public static Result listHeldItem(ServerPlayer seller, long price) {
		if (price <= 0L) {
			return Result.INVALID_AMOUNT;
		}

		EconomyData data = EconomyData.get(seller.server);
		if (data.listingCount() >= MAX_LISTINGS) {
			return Result.MARKET_FULL;
		}
		if (data.listingCount(seller.getUUID()) >= MAX_LISTINGS_PER_PLAYER) {
			return Result.PLAYER_LISTING_LIMIT;
		}

		ItemStack heldItem = seller.getMainHandItem();
		if (heldItem.isEmpty()) {
			return Result.NO_HELD_ITEM;
		}

		data.createListing(
			seller.getUUID(),
			seller.getGameProfile().getName(),
			price,
			seller.level().getGameTime(),
			heldItem.copy()
		);
		seller.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		return Result.SUCCESS;
	}

	public static Result listInventorySlot(ServerPlayer seller, int slot, long price) {
		if (price <= 0L) {
			return Result.INVALID_AMOUNT;
		}

		Inventory inventory = seller.getInventory();
		if (slot < 0 || slot >= inventory.items.size()) {
			return Result.INVALID_SLOT;
		}

		EconomyData data = EconomyData.get(seller.server);
		if (data.listingCount() >= MAX_LISTINGS) {
			return Result.MARKET_FULL;
		}
		if (data.listingCount(seller.getUUID()) >= MAX_LISTINGS_PER_PLAYER) {
			return Result.PLAYER_LISTING_LIMIT;
		}

		ItemStack selectedItem = inventory.getItem(slot);
		if (selectedItem.isEmpty()) {
			return Result.NO_SELECTED_ITEM;
		}

		data.createListing(
			seller.getUUID(),
			seller.getGameProfile().getName(),
			price,
			seller.level().getGameTime(),
			selectedItem.copy()
		);
		inventory.removeItemNoUpdate(slot);
		inventory.setChanged();
		return Result.SUCCESS;
	}

	public static Result sellToServer(ServerPlayer seller, int slot) {
		if (seller.getAbilities().instabuild) {
			return Result.CREATIVE_SELL_BLOCKED;
		}

		Inventory inventory = seller.getInventory();
		if (slot < 0 || slot >= inventory.items.size()) {
			return Result.INVALID_SLOT;
		}

		ItemStack selectedItem = inventory.getItem(slot);
		if (selectedItem.isEmpty()) {
			return Result.NO_SELECTED_ITEM;
		}

		long payout = ShopPricing.totalPrice(selectedItem);
		EconomyData data = EconomyData.get(seller.server);
		long balance = data.balance(seller.getUUID());
		if (Long.MAX_VALUE - balance < payout) {
			return Result.BALANCE_LIMIT;
		}

		ItemStack soldItem = inventory.removeItemNoUpdate(slot);
		if (soldItem.isEmpty()) {
			return Result.NO_SELECTED_ITEM;
		}
		if (!data.creditBalance(seller.getUUID(), payout)) {
			inventory.setItem(slot, soldItem);
			inventory.setChanged();
			return Result.BALANCE_LIMIT;
		}
		inventory.setChanged();
		seller.sendSystemMessage(Component.translatable(
			"message.runevestiges.economy.sold_to_server",
			soldItem.getHoverName(),
			soldItem.getCount(),
			payout
		));
		return Result.SUCCESS;
	}

	public static Result purchase(ServerPlayer buyer, long listingId) {
		EconomyData data = EconomyData.get(buyer.server);
		MarketListing listing = data.listing(listingId);
		if (listing == null) {
			return Result.LISTING_UNAVAILABLE;
		}
		if (listing.sellerId().equals(buyer.getUUID())) {
			return Result.OWN_LISTING;
		}

		long buyerBalance = data.balance(buyer.getUUID());
		long sellerBalance = data.balance(listing.sellerId());
		if (buyerBalance < listing.price()) {
			return Result.INSUFFICIENT_FUNDS;
		}
		if (Long.MAX_VALUE - sellerBalance < listing.price()) {
			return Result.BALANCE_LIMIT;
		}

		ItemStack item = listing.item();
		if (!canFit(buyer.getInventory(), item)) {
			return Result.INVENTORY_FULL;
		}

		if (!buyer.getInventory().add(item)) {
			return Result.INVENTORY_FULL;
		}

		data.setBalances(
			buyer.getUUID(),
			buyerBalance - listing.price(),
			listing.sellerId(),
			sellerBalance + listing.price()
		);
		if (!data.removeListing(listingId)) {
			throw new IllegalStateException("Marketplace listing disappeared during an atomic purchase.");
		}
		return Result.SUCCESS;
	}

	public static Result cancelListing(ServerPlayer seller, long listingId) {
		EconomyData data = EconomyData.get(seller.server);
		MarketListing listing = data.listing(listingId);
		if (listing == null) {
			return Result.LISTING_UNAVAILABLE;
		}
		if (!listing.sellerId().equals(seller.getUUID())) {
			return Result.NOT_LISTING_OWNER;
		}

		if (!data.removeListing(listingId)) {
			throw new IllegalStateException("Marketplace listing disappeared during cancellation.");
		}
		seller.getInventory().placeItemBackInInventory(listing.item());
		return Result.SUCCESS;
	}

	public static Result createOffer(ServerPlayer buyer, long listingId, long amount) {
		if (amount <= 0L) {
			return Result.INVALID_AMOUNT;
		}
		EconomyData data = EconomyData.get(buyer.server);
		MarketListing listing = data.listing(listingId);
		if (listing == null) {
			return Result.LISTING_UNAVAILABLE;
		}
		if (listing.sellerId().equals(buyer.getUUID())) {
			return Result.OWN_LISTING;
		}
		if (data.balance(buyer.getUUID()) < amount) {
			return Result.INSUFFICIENT_FUNDS;
		}
		if (data.offer(listingId, buyer.getUUID()) != null) {
			return Result.OFFER_ALREADY_SENT;
		}
		if (data.offerCount(listingId) >= MAX_OFFERS_PER_LISTING) {
			return Result.OFFERS_FULL;
		}
		if (data.offers().size() >= MAX_OFFERS) {
			return Result.OFFERS_FULL;
		}

		data.createOffer(
			listingId,
			buyer.getUUID(),
			buyer.getGameProfile().getName(),
			amount,
			buyer.level().getGameTime()
		);
		ServerPlayer seller = buyer.server.getPlayerList().getPlayer(listing.sellerId());
		if (seller != null) {
			seller.sendSystemMessage(Component.translatable(
				"message.runevestiges.economy.new_offer",
				buyer.getGameProfile().getName(),
				listing.item().getHoverName(),
				amount
			));
		}
		return Result.SUCCESS;
	}

	public static Result acceptOffer(ServerPlayer seller, long offerId) {
		EconomyData data = EconomyData.get(seller.server);
		MarketOffer offer = data.offer(offerId);
		if (offer == null) {
			return Result.OFFER_UNAVAILABLE;
		}
		MarketListing listing = data.listing(offer.listingId());
		if (listing == null) {
			data.removeOffer(offerId);
			return Result.LISTING_UNAVAILABLE;
		}
		if (!listing.sellerId().equals(seller.getUUID())) {
			return Result.NOT_LISTING_OWNER;
		}
		if (listing.sellerId().equals(offer.buyerId())) {
			return Result.OWN_LISTING;
		}

		ServerPlayer buyer = seller.server.getPlayerList().getPlayer(offer.buyerId());
		if (buyer == null) {
			return Result.OFFER_BUYER_OFFLINE;
		}
		long buyerBalance = data.balance(buyer.getUUID());
		long sellerBalance = data.balance(seller.getUUID());
		if (buyerBalance < offer.amount()) {
			return Result.INSUFFICIENT_FUNDS;
		}
		if (Long.MAX_VALUE - sellerBalance < offer.amount()) {
			return Result.BALANCE_LIMIT;
		}

		ItemStack item = listing.item();
		if (!canFit(buyer.getInventory(), item)) {
			return Result.INVENTORY_FULL;
		}
		if (!buyer.getInventory().add(item)) {
			return Result.INVENTORY_FULL;
		}

		data.setBalances(
			buyer.getUUID(),
			buyerBalance - offer.amount(),
			seller.getUUID(),
			sellerBalance + offer.amount()
		);
		if (!data.removeListing(listing.id())) {
			throw new IllegalStateException("Marketplace listing disappeared during offer acceptance.");
		}
		return Result.SUCCESS;
	}

	public static Result declineOffer(ServerPlayer seller, long offerId) {
		EconomyData data = EconomyData.get(seller.server);
		MarketOffer offer = data.offer(offerId);
		if (offer == null) {
			return Result.OFFER_UNAVAILABLE;
		}
		MarketListing listing = data.listing(offer.listingId());
		if (listing == null) {
			data.removeOffer(offerId);
			return Result.LISTING_UNAVAILABLE;
		}
		if (!listing.sellerId().equals(seller.getUUID())) {
			return Result.NOT_LISTING_OWNER;
		}
		return data.removeOffer(offerId) ? Result.SUCCESS : Result.OFFER_UNAVAILABLE;
	}

	public static EconomyPayloads.Snapshot snapshot(
		ServerPlayer player,
		int tab,
		int requestedPage,
		int requestedView,
		String message
	) {
		EconomyData data = EconomyData.get(player.server);
		List<String> onlinePlayers = player.server.getPlayerList().getPlayers().stream()
			.filter(onlinePlayer -> !onlinePlayer.getUUID().equals(player.getUUID()))
			.map(onlinePlayer -> onlinePlayer.getGameProfile().getName())
			.sorted(String.CASE_INSENSITIVE_ORDER)
			.limit(100)
			.toList();
		int view = requestedView == MARKET_VIEW_OFFERS ? MARKET_VIEW_OFFERS : MARKET_VIEW_BROWSE;
		if (tab != MARKET_TAB) {
			view = MARKET_VIEW_BROWSE;
		}
		if (view == MARKET_VIEW_OFFERS) {
			List<MarketOffer> offers = data.offers().stream()
				.filter(offer -> {
					MarketListing listing = data.listing(offer.listingId());
					return listing != null && listing.sellerId().equals(player.getUUID());
				})
				.sorted(Comparator.comparingLong(MarketOffer::id).reversed())
				.toList();
			int pageCount = Math.max(1, (offers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
			int page = Math.clamp(requestedPage, 0, pageCount - 1);
			List<EconomyPayloads.Offer> pageOffers = offers.stream()
				.skip((long) page * PAGE_SIZE)
				.limit(PAGE_SIZE)
				.map(offer -> new EconomyPayloads.Offer(
					offer.id(),
					offer.buyerName(),
					offer.amount(),
					data.listing(offer.listingId()).item()
				))
				.toList();
			return new EconomyPayloads.Snapshot(
				data.balance(player.getUUID()),
				page,
				pageCount,
				tab,
				view,
				message,
				List.of(),
				pageOffers,
				onlinePlayers
			);
		}

		List<MarketListing> listings = data.listings().stream()
			.sorted(Comparator.comparingLong(MarketListing::id).reversed())
			.toList();
		int pageCount = Math.max(1, (listings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int page = Math.clamp(requestedPage, 0, pageCount - 1);
		List<EconomyPayloads.Listing> pageListings = listings.stream()
			.skip((long) page * PAGE_SIZE)
			.limit(PAGE_SIZE)
			.map(listing -> new EconomyPayloads.Listing(
				listing.id(),
				listing.sellerName(),
				listing.price(),
				listing.sellerId().equals(player.getUUID()),
				listing.item()
			))
			.toList();
		return new EconomyPayloads.Snapshot(
			data.balance(player.getUUID()),
			page,
			pageCount,
			tab,
			view,
			message,
			pageListings,
			List.of(),
			onlinePlayers
		);
	}

	private static boolean canFit(Inventory inventory, ItemStack offered) {
		int remaining = offered.getCount();
		int maxStackSize = Math.min(inventory.getMaxStackSize(), offered.getMaxStackSize());
		for (ItemStack existing : inventory.items) {
			if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, offered)) {
				continue;
			}
			remaining -= Math.max(0, maxStackSize - existing.getCount());
			if (remaining <= 0) {
				return true;
			}
		}

		for (ItemStack existing : inventory.items) {
			if (!existing.isEmpty()) {
				continue;
			}
			remaining -= maxStackSize;
			if (remaining <= 0) {
				return true;
			}
		}
		return false;
	}

	public enum Result {
		SUCCESS("message.runevestiges.economy.success"),
		INVALID_AMOUNT("message.runevestiges.economy.invalid_amount"),
		INSUFFICIENT_FUNDS("message.runevestiges.economy.insufficient_funds"),
		BALANCE_LIMIT("message.runevestiges.economy.balance_limit"),
		SELF_TRANSFER("message.runevestiges.economy.self_transfer"),
		INVALID_RECIPIENT("message.runevestiges.economy.invalid_recipient"),
		NO_HELD_ITEM("message.runevestiges.economy.no_held_item"),
		MARKET_FULL("message.runevestiges.economy.market_full"),
		PLAYER_LISTING_LIMIT("message.runevestiges.economy.player_listing_limit"),
		LISTING_UNAVAILABLE("message.runevestiges.economy.listing_unavailable"),
		OWN_LISTING("message.runevestiges.economy.own_listing"),
		NOT_LISTING_OWNER("message.runevestiges.economy.not_listing_owner"),
		INVENTORY_FULL("message.runevestiges.economy.inventory_full"),
		INVALID_ACTION("message.runevestiges.economy.invalid_action"),
		INVALID_SLOT("message.runevestiges.economy.invalid_slot"),
		NO_SELECTED_ITEM("message.runevestiges.economy.no_selected_item"),
		CREATIVE_SELL_BLOCKED("message.runevestiges.economy.creative_sell_blocked"),
		OFFER_ALREADY_SENT("message.runevestiges.economy.offer_already_sent"),
		OFFERS_FULL("message.runevestiges.economy.offers_full"),
		OFFER_UNAVAILABLE("message.runevestiges.economy.offer_unavailable"),
		OFFER_BUYER_OFFLINE("message.runevestiges.economy.offer_buyer_offline");

		private final String translationKey;

		Result(String translationKey) {
			this.translationKey = translationKey;
		}

		public String translationKey() {
			return translationKey;
		}
	}
}
