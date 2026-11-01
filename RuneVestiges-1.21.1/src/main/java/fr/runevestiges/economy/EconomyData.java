package fr.runevestiges.economy;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public final class EconomyData extends SavedData {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String DATA_NAME = "runevestiges_economy";
	private static final Factory<EconomyData> FACTORY = new Factory<>(
		EconomyData::new,
		EconomyData::load,
		DataFixTypes.LEVEL
	);

	private final Map<UUID, Long> balances = new LinkedHashMap<>();
	private final Map<Long, MarketListing> listings = new LinkedHashMap<>();
	private final Map<Long, MarketOffer> offers = new LinkedHashMap<>();
	private final Set<UUID> starterBonusRecipients = new HashSet<>();
	private long nextListingId = 1;
	private long nextOfferId = 1;

	private EconomyData() {
	}

	public static EconomyData get(net.minecraft.server.MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
	}

	public long balance(UUID playerId) {
		return balances.getOrDefault(playerId, 0L);
	}

	public Wallet wallet(UUID playerId) {
		return new Wallet(playerId, balance(playerId));
	}

	public List<MarketListing> listings() {
		return List.copyOf(listings.values());
	}

	public MarketListing listing(long listingId) {
		return listings.get(listingId);
	}

	public List<MarketOffer> offers() {
		return List.copyOf(offers.values());
	}

	public MarketOffer offer(long offerId) {
		return offers.get(offerId);
	}

	public MarketOffer offer(long listingId, UUID buyerId) {
		return offers.values().stream()
			.filter(offer -> offer.listingId() == listingId && offer.buyerId().equals(buyerId))
			.findFirst()
			.orElse(null);
	}

	public int offerCount(long listingId) {
		return (int) offers.values().stream().filter(offer -> offer.listingId() == listingId).count();
	}

	public long createOffer(
		long listingId,
		UUID buyerId,
		String buyerName,
		long amount,
		long createdAt
	) {
		if (nextOfferId <= 0L) {
			throw new IllegalStateException("Marketplace offer ID space is exhausted.");
		}
		long id = nextOfferId++;
		offers.put(id, new MarketOffer(id, listingId, buyerId, buyerName, amount, createdAt));
		setDirty();
		return id;
	}

	public boolean removeOffer(long offerId) {
		if (offers.remove(offerId) == null) {
			return false;
		}
		setDirty();
		return true;
	}

	public int listingCount(UUID sellerId) {
		return (int) listings.values().stream().filter(listing -> listing.sellerId().equals(sellerId)).count();
	}

	public int listingCount() {
		return listings.size();
	}

	public long createListing(UUID sellerId, String sellerName, long price, long createdAt, ItemStack item) {
		long id = nextListingId++;
		listings.put(id, new MarketListing(id, sellerId, sellerName, price, createdAt, item));
		setDirty();
		return id;
	}

	public boolean removeListing(long listingId) {
		if (listings.remove(listingId) == null) {
			return false;
		}
		offers.values().removeIf(offer -> offer.listingId() == listingId);
		setDirty();
		return true;
	}

	public void setBalance(UUID playerId, long balance) {
		if (balance < 0L) {
			throw new IllegalArgumentException("A wallet balance cannot be negative.");
		}
		writeBalance(playerId, balance);
		setDirty();
	}

	public void setBalances(UUID firstPlayerId, long firstBalance, UUID secondPlayerId, long secondBalance) {
		if (firstPlayerId.equals(secondPlayerId)) {
			throw new IllegalArgumentException("A wallet transaction requires two different players.");
		}
		if (firstBalance < 0L || secondBalance < 0L) {
			throw new IllegalArgumentException("Wallet balances cannot be negative.");
		}
		writeBalance(firstPlayerId, firstBalance);
		writeBalance(secondPlayerId, secondBalance);
		setDirty();
	}

	public boolean creditBalance(UUID playerId, long amount) {
		long currentBalance = balance(playerId);
		if (amount <= 0L || Long.MAX_VALUE - currentBalance < amount) {
			return false;
		}
		writeBalance(playerId, currentBalance + amount);
		setDirty();
		return true;
	}

	private void writeBalance(UUID playerId, long balance) {
		if (balance == 0L) {
			balances.remove(playerId);
		} else {
			balances.put(playerId, balance);
		}
	}

	public boolean grantStarterBonus(UUID playerId, long amount) {
		if (starterBonusRecipients.contains(playerId)) {
			return false;
		}
		if (amount <= 0L || Long.MAX_VALUE - balance(playerId) < amount) {
			LOGGER.error("Could not grant starter balance to {} because the amount is invalid or exceeds the wallet limit", playerId);
			return false;
		}

		balances.put(playerId, balance(playerId) + amount);
		starterBonusRecipients.add(playerId);
		setDirty();
		return true;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag savedBalances = new CompoundTag();
		balances.forEach((playerId, balance) -> savedBalances.putLong(playerId.toString(), balance));
		tag.put("balances", savedBalances);

		CompoundTag savedStarterBonusRecipients = new CompoundTag();
		starterBonusRecipients.forEach(playerId -> savedStarterBonusRecipients.putBoolean(playerId.toString(), true));
		tag.put("starterBonusRecipients", savedStarterBonusRecipients);

		ListTag savedListings = new ListTag();
		for (MarketListing listing : listings.values()) {
			CompoundTag savedListing = new CompoundTag();
			savedListing.putLong("id", listing.id());
			savedListing.putUUID("seller", listing.sellerId());
			savedListing.putString("sellerName", listing.sellerName());
			savedListing.putLong("price", listing.price());
			savedListing.putLong("createdAt", listing.createdAt());
			savedListing.put("item", listing.item().save(registries));
			savedListings.add(savedListing);
		}
		tag.put("listings", savedListings);
		tag.putLong("nextListingId", nextListingId);

		ListTag savedOffers = new ListTag();
		for (MarketOffer offer : offers.values()) {
			CompoundTag savedOffer = new CompoundTag();
			savedOffer.putLong("id", offer.id());
			savedOffer.putLong("listingId", offer.listingId());
			savedOffer.putUUID("buyer", offer.buyerId());
			savedOffer.putString("buyerName", offer.buyerName());
			savedOffer.putLong("amount", offer.amount());
			savedOffer.putLong("createdAt", offer.createdAt());
			savedOffers.add(savedOffer);
		}
		tag.put("offers", savedOffers);
		tag.putLong("nextOfferId", nextOfferId);
		return tag;
	}

	private static EconomyData load(CompoundTag tag, HolderLookup.Provider registries) {
		EconomyData data = new EconomyData();
		CompoundTag savedBalances = tag.getCompound("balances");
		for (String rawId : savedBalances.getAllKeys()) {
			try {
				long balance = savedBalances.getLong(rawId);
				UUID playerId = UUID.fromString(rawId);
				if (balance >= 0L) {
					data.balances.put(playerId, balance);
				} else {
					LOGGER.warn("Ignoring negative saved wallet balance for {}", rawId);
				}
			} catch (IllegalArgumentException exception) {
				LOGGER.warn("Ignoring invalid player UUID in saved wallet data: {}", rawId, exception);
			}
		}

		CompoundTag savedStarterBonusRecipients = tag.getCompound("starterBonusRecipients");
		for (String rawId : savedStarterBonusRecipients.getAllKeys()) {
			try {
				data.starterBonusRecipients.add(UUID.fromString(rawId));
			} catch (IllegalArgumentException exception) {
				LOGGER.warn("Ignoring invalid player UUID in starter bonus data: {}", rawId, exception);
			}
		}

		ListTag savedListings = tag.getList("listings", Tag.TAG_COMPOUND);
		for (int index = 0; index < savedListings.size(); index++) {
			CompoundTag savedListing = savedListings.getCompound(index);
			long id = savedListing.getLong("id");
			long price = savedListing.getLong("price");
			if (!savedListing.hasUUID("seller") || id <= 0L || price <= 0L || !savedListing.contains("item")) {
				LOGGER.warn("Ignoring invalid saved marketplace listing at index {}", index);
				continue;
			}

			ItemStack item = ItemStack.parse(registries, savedListing.get("item")).orElse(ItemStack.EMPTY);
			if (item.isEmpty()) {
				LOGGER.warn("Ignoring marketplace listing {} because its saved item is invalid", id);
				continue;
			}

			MarketListing listing = new MarketListing(
				id,
				savedListing.getUUID("seller"),
				savedListing.getString("sellerName"),
				price,
				savedListing.getLong("createdAt"),
				item
			);
			if (data.listings.putIfAbsent(id, listing) != null) {
				LOGGER.warn("Ignoring duplicate marketplace listing ID {}", id);
			}
			data.nextListingId = Math.max(data.nextListingId, id + 1L);
		}

		data.nextListingId = Math.max(data.nextListingId, tag.getLong("nextListingId"));

		ListTag savedOffers = tag.getList("offers", Tag.TAG_COMPOUND);
		for (int index = 0; index < savedOffers.size(); index++) {
			CompoundTag savedOffer = savedOffers.getCompound(index);
			long id = savedOffer.getLong("id");
			long listingId = savedOffer.getLong("listingId");
			long amount = savedOffer.getLong("amount");
			if (!savedOffer.hasUUID("buyer")
				|| id <= 0L
				|| listingId <= 0L
				|| amount <= 0L
				|| !data.listings.containsKey(listingId)
				|| data.listings.get(listingId).sellerId().equals(savedOffer.getUUID("buyer"))) {
				LOGGER.warn("Ignoring invalid saved marketplace offer at index {}", index);
				continue;
			}

			MarketOffer offer = new MarketOffer(
				id,
				listingId,
				savedOffer.getUUID("buyer"),
				savedOffer.getString("buyerName"),
				amount,
				savedOffer.getLong("createdAt")
			);
			if (data.offers.putIfAbsent(id, offer) != null) {
				LOGGER.warn("Ignoring duplicate marketplace offer ID {}", id);
			}
			data.nextOfferId = Math.max(data.nextOfferId, id + 1L);
		}
		data.nextOfferId = Math.max(data.nextOfferId, tag.getLong("nextOfferId"));
		return data;
	}
}
