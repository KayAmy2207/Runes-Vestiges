package fr.runevestiges.economy;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class EconomyService {
   public static final int WALLET_TAB = 0;
   public static final int MARKET_TAB = 1;
   public static final int HOME_TAB = 2;
   public static final int SERVER_SHOP_TAB = 3;
   public static final int PAGE_SIZE = 5;
   public static final int MAX_LISTINGS = 1000;
   public static final int MAX_LISTINGS_PER_PLAYER = 50;
   public static final int MAX_OFFERS_PER_LISTING = 20;
   public static final int MAX_OFFERS = 5000;
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

   public static EconomyService.Result addFunds(MinecraftServer server, UUID playerId, long amount) {
      if (amount <= 0L) {
         return EconomyService.Result.INVALID_AMOUNT;
      } else {
         EconomyData data = EconomyData.get(server);
         long balance = data.balance(playerId);
         if (Long.MAX_VALUE - balance < amount) {
            return EconomyService.Result.BALANCE_LIMIT;
         } else {
            data.setBalance(playerId, balance + amount);
            return EconomyService.Result.SUCCESS;
         }
      }
   }

   public static boolean grantStarterBonus(MinecraftServer server, UUID playerId) {
      return EconomyData.get(server).grantStarterBonus(playerId, 100L);
   }

   public static EconomyService.Result withdrawFunds(MinecraftServer server, UUID playerId, long amount) {
      if (amount <= 0L) {
         return EconomyService.Result.INVALID_AMOUNT;
      } else {
         EconomyData data = EconomyData.get(server);
         long balance = data.balance(playerId);
         if (balance < amount) {
            return EconomyService.Result.INSUFFICIENT_FUNDS;
         } else {
            data.setBalance(playerId, balance - amount);
            return EconomyService.Result.SUCCESS;
         }
      }
   }

   public static EconomyService.Result transfer(MinecraftServer server, UUID senderId, UUID recipientId, long amount) {
      if (amount <= 0L) {
         return EconomyService.Result.INVALID_AMOUNT;
      } else if (senderId.equals(recipientId)) {
         return EconomyService.Result.SELF_TRANSFER;
      } else {
         EconomyData data = EconomyData.get(server);
         long senderBalance = data.balance(senderId);
         long recipientBalance = data.balance(recipientId);
         if (senderBalance < amount) {
            return EconomyService.Result.INSUFFICIENT_FUNDS;
         } else if (Long.MAX_VALUE - recipientBalance < amount) {
            return EconomyService.Result.BALANCE_LIMIT;
         } else {
            data.setBalances(senderId, senderBalance - amount, recipientId, recipientBalance + amount);
            return EconomyService.Result.SUCCESS;
         }
      }
   }

   public static EconomyService.Result listHeldItem(ServerPlayer seller, long price) {
      if (price <= 0L) {
         return EconomyService.Result.INVALID_AMOUNT;
      } else {
         EconomyData data = EconomyData.get(seller.level().getServer());
         if (data.listingCount() >= 1000) {
            return EconomyService.Result.MARKET_FULL;
         } else if (data.listingCount(seller.getUUID()) >= 50) {
            return EconomyService.Result.PLAYER_LISTING_LIMIT;
         } else {
            ItemStack heldItem = seller.getMainHandItem();
            if (heldItem.isEmpty()) {
               return EconomyService.Result.NO_HELD_ITEM;
            } else {
               data.createListing(seller.getUUID(), seller.getGameProfile().name(), price, seller.level().getGameTime(), heldItem.copy());
               seller.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
               return EconomyService.Result.SUCCESS;
            }
         }
      }
   }

   public static EconomyService.Result listInventorySlot(ServerPlayer seller, int slot, long price) {
      if (price <= 0L) {
         return EconomyService.Result.INVALID_AMOUNT;
      } else {
         Inventory inventory = seller.getInventory();
         if (slot >= 0 && slot < inventory.getNonEquipmentItems().size()) {
            EconomyData data = EconomyData.get(seller.level().getServer());
            if (data.listingCount() >= 1000) {
               return EconomyService.Result.MARKET_FULL;
            } else if (data.listingCount(seller.getUUID()) >= 50) {
               return EconomyService.Result.PLAYER_LISTING_LIMIT;
            } else {
               ItemStack selectedItem = inventory.getItem(slot);
               if (selectedItem.isEmpty()) {
                  return EconomyService.Result.NO_SELECTED_ITEM;
               } else {
                  data.createListing(seller.getUUID(), seller.getGameProfile().name(), price, seller.level().getGameTime(), selectedItem.copy());
                  inventory.removeItemNoUpdate(slot);
                  inventory.setChanged();
                  return EconomyService.Result.SUCCESS;
               }
            }
         } else {
            return EconomyService.Result.INVALID_SLOT;
         }
      }
   }

   public static EconomyService.Result sellToServer(ServerPlayer seller, int slot) {
      if (seller.getAbilities().instabuild) {
         return EconomyService.Result.CREATIVE_SELL_BLOCKED;
      } else {
         Inventory inventory = seller.getInventory();
         if (slot >= 0 && slot < inventory.getNonEquipmentItems().size()) {
            ItemStack selectedItem = inventory.getItem(slot);
            if (selectedItem.isEmpty()) {
               return EconomyService.Result.NO_SELECTED_ITEM;
            } else {
               long payout = ShopPricing.totalPrice(selectedItem);
               EconomyData data = EconomyData.get(seller.level().getServer());
               long balance = data.balance(seller.getUUID());
               if (Long.MAX_VALUE - balance < payout) {
                  return EconomyService.Result.BALANCE_LIMIT;
               } else {
                  ItemStack soldItem = inventory.removeItemNoUpdate(slot);
                  if (soldItem.isEmpty()) {
                     return EconomyService.Result.NO_SELECTED_ITEM;
                  } else if (!data.creditBalance(seller.getUUID(), payout)) {
                     inventory.setItem(slot, soldItem);
                     inventory.setChanged();
                     return EconomyService.Result.BALANCE_LIMIT;
                  } else {
                     inventory.setChanged();
                     seller.sendSystemMessage(
                        Component.translatable(
                           "message.runevestiges.economy.sold_to_server", new Object[]{soldItem.getHoverName(), soldItem.getCount(), payout}
                        )
                     );
                     return EconomyService.Result.SUCCESS;
                  }
               }
            }
         } else {
            return EconomyService.Result.INVALID_SLOT;
         }
      }
   }

   public static EconomyService.Result purchase(ServerPlayer buyer, long listingId) {
      EconomyData data = EconomyData.get(buyer.level().getServer());
      MarketListing listing = data.listing(listingId);
      if (listing == null) {
         return EconomyService.Result.LISTING_UNAVAILABLE;
      } else if (listing.sellerId().equals(buyer.getUUID())) {
         return EconomyService.Result.OWN_LISTING;
      } else {
         long buyerBalance = data.balance(buyer.getUUID());
         long sellerBalance = data.balance(listing.sellerId());
         if (buyerBalance < listing.price()) {
            return EconomyService.Result.INSUFFICIENT_FUNDS;
         } else if (Long.MAX_VALUE - sellerBalance < listing.price()) {
            return EconomyService.Result.BALANCE_LIMIT;
         } else {
            ItemStack item = listing.item();
            if (!canFit(buyer.getInventory(), item)) {
               return EconomyService.Result.INVENTORY_FULL;
            } else if (!buyer.getInventory().add(item)) {
               return EconomyService.Result.INVENTORY_FULL;
            } else {
               data.setBalances(buyer.getUUID(), buyerBalance - listing.price(), listing.sellerId(), sellerBalance + listing.price());
               if (!data.removeListing(listingId)) {
                  throw new IllegalStateException("Marketplace listing disappeared during an atomic purchase.");
               } else {
                  return EconomyService.Result.SUCCESS;
               }
            }
         }
      }
   }

   public static EconomyService.Result cancelListing(ServerPlayer seller, long listingId) {
      EconomyData data = EconomyData.get(seller.level().getServer());
      MarketListing listing = data.listing(listingId);
      if (listing == null) {
         return EconomyService.Result.LISTING_UNAVAILABLE;
      } else if (!listing.sellerId().equals(seller.getUUID())) {
         return EconomyService.Result.NOT_LISTING_OWNER;
      } else if (!data.removeListing(listingId)) {
         throw new IllegalStateException("Marketplace listing disappeared during cancellation.");
      } else {
         seller.getInventory().placeItemBackInInventory(listing.item(), Prediction.SERVER_ONLY);
         return EconomyService.Result.SUCCESS;
      }
   }

   public static EconomyService.Result createOffer(ServerPlayer buyer, long listingId, long amount) {
      if (amount <= 0L) {
         return EconomyService.Result.INVALID_AMOUNT;
      } else {
         EconomyData data = EconomyData.get(buyer.level().getServer());
         MarketListing listing = data.listing(listingId);
         if (listing == null) {
            return EconomyService.Result.LISTING_UNAVAILABLE;
         } else if (listing.sellerId().equals(buyer.getUUID())) {
            return EconomyService.Result.OWN_LISTING;
         } else if (data.balance(buyer.getUUID()) < amount) {
            return EconomyService.Result.INSUFFICIENT_FUNDS;
         } else if (data.offer(listingId, buyer.getUUID()) != null) {
            return EconomyService.Result.OFFER_ALREADY_SENT;
         } else if (data.offerCount(listingId) >= 20) {
            return EconomyService.Result.OFFERS_FULL;
         } else if (data.offers().size() >= 5000) {
            return EconomyService.Result.OFFERS_FULL;
         } else {
            data.createOffer(listingId, buyer.getUUID(), buyer.getGameProfile().name(), amount, buyer.level().getGameTime());
            ServerPlayer seller = buyer.level().getServer().getPlayerList().getPlayer(listing.sellerId());
            if (seller != null) {
               seller.sendSystemMessage(
                  Component.translatable(
                     "message.runevestiges.economy.new_offer", new Object[]{buyer.getGameProfile().name(), listing.item().getHoverName(), amount}
                  )
               );
            }

            return EconomyService.Result.SUCCESS;
         }
      }
   }

   public static EconomyService.Result acceptOffer(ServerPlayer seller, long offerId) {
      EconomyData data = EconomyData.get(seller.level().getServer());
      MarketOffer offer = data.offer(offerId);
      if (offer == null) {
         return EconomyService.Result.OFFER_UNAVAILABLE;
      } else {
         MarketListing listing = data.listing(offer.listingId());
         if (listing == null) {
            data.removeOffer(offerId);
            return EconomyService.Result.LISTING_UNAVAILABLE;
         } else if (!listing.sellerId().equals(seller.getUUID())) {
            return EconomyService.Result.NOT_LISTING_OWNER;
         } else if (listing.sellerId().equals(offer.buyerId())) {
            return EconomyService.Result.OWN_LISTING;
         } else {
            ServerPlayer buyer = seller.level().getServer().getPlayerList().getPlayer(offer.buyerId());
            if (buyer == null) {
               return EconomyService.Result.OFFER_BUYER_OFFLINE;
            } else {
               long buyerBalance = data.balance(buyer.getUUID());
               long sellerBalance = data.balance(seller.getUUID());
               if (buyerBalance < offer.amount()) {
                  return EconomyService.Result.INSUFFICIENT_FUNDS;
               } else if (Long.MAX_VALUE - sellerBalance < offer.amount()) {
                  return EconomyService.Result.BALANCE_LIMIT;
               } else {
                  ItemStack item = listing.item();
                  if (!canFit(buyer.getInventory(), item)) {
                     return EconomyService.Result.INVENTORY_FULL;
                  } else if (!buyer.getInventory().add(item)) {
                     return EconomyService.Result.INVENTORY_FULL;
                  } else {
                     data.setBalances(buyer.getUUID(), buyerBalance - offer.amount(), seller.getUUID(), sellerBalance + offer.amount());
                     if (!data.removeListing(listing.id())) {
                        throw new IllegalStateException("Marketplace listing disappeared during offer acceptance.");
                     } else {
                        return EconomyService.Result.SUCCESS;
                     }
                  }
               }
            }
         }
      }
   }

   public static EconomyService.Result declineOffer(ServerPlayer seller, long offerId) {
      EconomyData data = EconomyData.get(seller.level().getServer());
      MarketOffer offer = data.offer(offerId);
      if (offer == null) {
         return EconomyService.Result.OFFER_UNAVAILABLE;
      } else {
         MarketListing listing = data.listing(offer.listingId());
         if (listing == null) {
            data.removeOffer(offerId);
            return EconomyService.Result.LISTING_UNAVAILABLE;
         } else if (!listing.sellerId().equals(seller.getUUID())) {
            return EconomyService.Result.NOT_LISTING_OWNER;
         } else {
            return data.removeOffer(offerId) ? EconomyService.Result.SUCCESS : EconomyService.Result.OFFER_UNAVAILABLE;
         }
      }
   }

   public static EconomyPayloads.Snapshot snapshot(ServerPlayer player, int tab, int requestedPage, int requestedView, String message) {
      EconomyData data = EconomyData.get(player.level().getServer());
      List<String> onlinePlayers = player.level()
         .getServer()
         .getPlayerList()
         .getPlayers()
         .stream()
         .filter(onlinePlayer -> !onlinePlayer.getUUID().equals(player.getUUID()))
         .map(onlinePlayer -> onlinePlayer.getGameProfile().name())
         .sorted(String.CASE_INSENSITIVE_ORDER)
         .limit(100L)
         .toList();
      int view = requestedView == 1 ? 1 : 0;
      if (tab != 1) {
         view = 0;
      }

      if (view == 1) {
         List<MarketOffer> offers = data.offers().stream().filter(offer -> {
            MarketListing listing = data.listing(offer.listingId());
            return listing != null && listing.sellerId().equals(player.getUUID());
         }).sorted(Comparator.comparingLong(MarketOffer::id).reversed()).toList();
         int pageCount = Math.max(1, (offers.size() + 5 - 1) / 5);
         int page = Math.clamp((long)requestedPage, 0, pageCount - 1);
         List<EconomyPayloads.Offer> pageOffers = offers.stream()
            .skip(page * 5L)
            .limit(5L)
            .map(offer -> new EconomyPayloads.Offer(offer.id(), offer.buyerName(), offer.amount(), data.listing(offer.listingId()).item()))
            .toList();
         return new EconomyPayloads.Snapshot(data.balance(player.getUUID()), page, pageCount, tab, view, message, List.of(), pageOffers, onlinePlayers);
      } else {
         List<MarketListing> listings = data.listings().stream().sorted(Comparator.comparingLong(MarketListing::id).reversed()).toList();
         int pageCount = Math.max(1, (listings.size() + 5 - 1) / 5);
         int page = Math.clamp((long)requestedPage, 0, pageCount - 1);
         List<EconomyPayloads.Listing> pageListings = listings.stream()
            .skip(page * 5L)
            .limit(5L)
            .map(
               listing -> new EconomyPayloads.Listing(
                  listing.id(), listing.sellerName(), listing.price(), listing.sellerId().equals(player.getUUID()), listing.item()
               )
            )
            .toList();
         return new EconomyPayloads.Snapshot(data.balance(player.getUUID()), page, pageCount, tab, view, message, pageListings, List.of(), onlinePlayers);
      }
   }

   private static boolean canFit(Inventory inventory, ItemStack offered) {
      int remaining = offered.getCount();
      int maxStackSize = Math.min(inventory.getMaxStackSize(), offered.getMaxStackSize());

      for (ItemStack existing : inventory.getNonEquipmentItems()) {
         if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, offered)) {
            remaining -= Math.max(0, maxStackSize - existing.getCount());
            if (remaining <= 0) {
               return true;
            }
         }
      }

      for (ItemStack existingx : inventory.getNonEquipmentItems()) {
         if (existingx.isEmpty()) {
            remaining -= maxStackSize;
            if (remaining <= 0) {
               return true;
            }
         }
      }

      return false;
   }

   public static enum Result {
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

      private Result(String translationKey) {
         this.translationKey = translationKey;
      }

      public String translationKey() {
         return this.translationKey;
      }
   }
}
