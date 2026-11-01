package fr.runevestiges.economy;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;

public final class EconomyData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "runevestiges_economy";
   private static final Codec<MarketListing> LISTING_CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(MarketListing::id),
            UUIDUtil.STRING_CODEC.fieldOf("seller").forGetter(MarketListing::sellerId),
            Codec.STRING.fieldOf("sellerName").forGetter(MarketListing::sellerName),
            Codec.LONG.fieldOf("price").forGetter(MarketListing::price),
            Codec.LONG.fieldOf("createdAt").forGetter(MarketListing::createdAt),
            ItemStack.CODEC.fieldOf("item").forGetter(MarketListing::item)
         )
         .apply(instance, MarketListing::new)
   );
   private static final Codec<MarketOffer> OFFER_CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(MarketOffer::id),
            Codec.LONG.fieldOf("listingId").forGetter(MarketOffer::listingId),
            UUIDUtil.STRING_CODEC.fieldOf("buyer").forGetter(MarketOffer::buyerId),
            Codec.STRING.fieldOf("buyerName").forGetter(MarketOffer::buyerName),
            Codec.LONG.fieldOf("amount").forGetter(MarketOffer::amount),
            Codec.LONG.fieldOf("createdAt").forGetter(MarketOffer::createdAt)
         )
         .apply(instance, MarketOffer::new)
   );
   private static final Codec<EconomyData.SerializedEconomy> SERIALIZED_CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("balances", Map.of()).forGetter(EconomyData.SerializedEconomy::balances),
            Codec.list(LISTING_CODEC).optionalFieldOf("listings", List.of()).forGetter(EconomyData.SerializedEconomy::listings),
            Codec.list(OFFER_CODEC).optionalFieldOf("offers", List.of()).forGetter(EconomyData.SerializedEconomy::offers),
            UUIDUtil.CODEC_SET.optionalFieldOf("starterBonusRecipients", Set.of()).forGetter(EconomyData.SerializedEconomy::starterBonusRecipients),
            Codec.LONG.optionalFieldOf("nextListingId", 1L).forGetter(EconomyData.SerializedEconomy::nextListingId),
            Codec.LONG.optionalFieldOf("nextOfferId", 1L).forGetter(EconomyData.SerializedEconomy::nextOfferId)
         )
         .apply(instance, EconomyData.SerializedEconomy::new)
   );
   private static final Codec<EconomyData> DATA_CODEC = SERIALIZED_CODEC.xmap(EconomyData::load, EconomyData::save);
   private static final SavedDataType<EconomyData> TYPE = new SavedDataType(
      Identifier.fromNamespaceAndPath("runevestiges", "runevestiges_economy"), EconomyData::new, DATA_CODEC, DataFixTypes.LEVEL
   );
   private final Map<UUID, Long> balances = new LinkedHashMap<>();
   private final Map<Long, MarketListing> listings = new LinkedHashMap<>();
   private final Map<Long, MarketOffer> offers = new LinkedHashMap<>();
   private final Set<UUID> starterBonusRecipients = new HashSet<>();
   private long nextListingId = 1L;
   private long nextOfferId = 1L;

   private EconomyData() {
   }

   public static EconomyData get(MinecraftServer server) {
      return (EconomyData)server.overworld().getDataStorage().computeIfAbsent(TYPE);
   }

   public long balance(UUID playerId) {
      return this.balances.getOrDefault(playerId, 0L);
   }

   public Wallet wallet(UUID playerId) {
      return new Wallet(playerId, this.balance(playerId));
   }

   public List<MarketListing> listings() {
      return List.copyOf(this.listings.values());
   }

   public MarketListing listing(long listingId) {
      return this.listings.get(listingId);
   }

   public List<MarketOffer> offers() {
      return List.copyOf(this.offers.values());
   }

   public MarketOffer offer(long offerId) {
      return this.offers.get(offerId);
   }

   public MarketOffer offer(long listingId, UUID buyerId) {
      return this.offers.values().stream().filter(offer -> offer.listingId() == listingId && offer.buyerId().equals(buyerId)).findFirst().orElse(null);
   }

   public int offerCount(long listingId) {
      return (int)this.offers.values().stream().filter(offer -> offer.listingId() == listingId).count();
   }

   public long createOffer(long listingId, UUID buyerId, String buyerName, long amount, long createdAt) {
      if (this.nextOfferId <= 0L) {
         throw new IllegalStateException("Marketplace offer ID space is exhausted.");
      } else {
         long id = this.nextOfferId++;
         this.offers.put(id, new MarketOffer(id, listingId, buyerId, buyerName, amount, createdAt));
         this.setDirty();
         return id;
      }
   }

   public boolean removeOffer(long offerId) {
      if (this.offers.remove(offerId) == null) {
         return false;
      } else {
         this.setDirty();
         return true;
      }
   }

   public int listingCount(UUID sellerId) {
      return (int)this.listings.values().stream().filter(listing -> listing.sellerId().equals(sellerId)).count();
   }

   public int listingCount() {
      return this.listings.size();
   }

   public long createListing(UUID sellerId, String sellerName, long price, long createdAt, ItemStack item) {
      long id = this.nextListingId++;
      this.listings.put(id, new MarketListing(id, sellerId, sellerName, price, createdAt, item));
      this.setDirty();
      return id;
   }

   public boolean removeListing(long listingId) {
      if (this.listings.remove(listingId) == null) {
         return false;
      } else {
         this.offers.values().removeIf(offer -> offer.listingId() == listingId);
         this.setDirty();
         return true;
      }
   }

   public void setBalance(UUID playerId, long balance) {
      if (balance < 0L) {
         throw new IllegalArgumentException("A wallet balance cannot be negative.");
      } else {
         this.writeBalance(playerId, balance);
         this.setDirty();
      }
   }

   public void setBalances(UUID firstPlayerId, long firstBalance, UUID secondPlayerId, long secondBalance) {
      if (firstPlayerId.equals(secondPlayerId)) {
         throw new IllegalArgumentException("A wallet transaction requires two different players.");
      } else if (firstBalance >= 0L && secondBalance >= 0L) {
         this.writeBalance(firstPlayerId, firstBalance);
         this.writeBalance(secondPlayerId, secondBalance);
         this.setDirty();
      } else {
         throw new IllegalArgumentException("Wallet balances cannot be negative.");
      }
   }

   public boolean creditBalance(UUID playerId, long amount) {
      long currentBalance = this.balance(playerId);
      if (amount > 0L && Long.MAX_VALUE - currentBalance >= amount) {
         this.writeBalance(playerId, currentBalance + amount);
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   private void writeBalance(UUID playerId, long balance) {
      if (balance == 0L) {
         this.balances.remove(playerId);
      } else {
         this.balances.put(playerId, balance);
      }
   }

   public boolean grantStarterBonus(UUID playerId, long amount) {
      if (this.starterBonusRecipients.contains(playerId)) {
         return false;
      } else if (amount > 0L && Long.MAX_VALUE - this.balance(playerId) >= amount) {
         this.balances.put(playerId, this.balance(playerId) + amount);
         this.starterBonusRecipients.add(playerId);
         this.setDirty();
         return true;
      } else {
         LOGGER.error("Could not grant starter balance to {} because the amount is invalid or exceeds the wallet limit", playerId);
         return false;
      }
   }

   private EconomyData.SerializedEconomy save() {
      return new EconomyData.SerializedEconomy(
         Map.copyOf(this.balances),
         List.copyOf(this.listings.values()),
         List.copyOf(this.offers.values()),
         Set.copyOf(this.starterBonusRecipients),
         this.nextListingId,
         this.nextOfferId
      );
   }

   private static EconomyData load(EconomyData.SerializedEconomy saved) {
      EconomyData data = new EconomyData();
      saved.balances().forEach((playerId, balance) -> {
         if (balance >= 0L) {
            data.balances.put(playerId, balance);
         } else {
            LOGGER.warn("Ignoring negative saved wallet balance for {}", playerId);
         }
      });
      data.starterBonusRecipients.addAll(saved.starterBonusRecipients());

      for (MarketListing listing : saved.listings()) {
         if (listing.id() > 0L && listing.price() > 0L && !listing.item().isEmpty()) {
            if (data.listings.putIfAbsent(listing.id(), listing) != null) {
               LOGGER.warn("Ignoring duplicate marketplace listing ID {}", listing.id());
            } else {
               data.nextListingId = Math.max(data.nextListingId, listing.id() + 1L);
            }
         } else {
            LOGGER.warn("Ignoring invalid saved marketplace listing {}", listing.id());
         }
      }

      data.nextListingId = Math.max(data.nextListingId, saved.nextListingId());

      for (MarketOffer offer : saved.offers()) {
         MarketListing listingx = data.listings.get(offer.listingId());
         if (offer.id() > 0L && offer.listingId() > 0L && offer.amount() > 0L && listingx != null && !listingx.sellerId().equals(offer.buyerId())) {
            if (data.offers.putIfAbsent(offer.id(), offer) != null) {
               LOGGER.warn("Ignoring duplicate marketplace offer ID {}", offer.id());
            } else {
               data.nextOfferId = Math.max(data.nextOfferId, offer.id() + 1L);
            }
         } else {
            LOGGER.warn("Ignoring invalid saved marketplace offer {}", offer.id());
         }
      }

      data.nextOfferId = Math.max(data.nextOfferId, saved.nextOfferId());
      return data;
   }

   private record SerializedEconomy(
      Map<UUID, Long> balances, List<MarketListing> listings, List<MarketOffer> offers, Set<UUID> starterBonusRecipients, long nextListingId, long nextOfferId
   ) {
   }
}
