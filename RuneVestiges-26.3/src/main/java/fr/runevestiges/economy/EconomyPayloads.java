package fr.runevestiges.economy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class EconomyPayloads {
   private static final int MAX_PAGE_LISTINGS = 5;
   private static final int MAX_STATUS_LENGTH = 256;
   private static final int MAX_PLAYER_NAME_LENGTH = 64;
   private static final int MAX_ONLINE_PLAYERS = 100;

   private EconomyPayloads() {
   }

   public record Action(int action, long listingId, String text, long amount, int page, int tab, int view) implements CustomPacketPayload {
      public static final Type<EconomyPayloads.Action> TYPE = new Type(Identifier.fromNamespaceAndPath("runevestiges", "economy_action"));
      public static final StreamCodec<RegistryFriendlyByteBuf, EconomyPayloads.Action> CODEC = StreamCodec.of(
         (buffer, payload) -> {
            buffer.writeVarInt(payload.action());
            buffer.writeVarLong(payload.listingId());
            buffer.writeUtf(payload.text(), 64);
            buffer.writeVarLong(payload.amount());
            buffer.writeVarInt(payload.page());
            buffer.writeVarInt(payload.tab());
            buffer.writeVarInt(payload.view());
         },
         buffer -> new EconomyPayloads.Action(
            buffer.readVarInt(), buffer.readVarLong(), buffer.readUtf(64), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()
         )
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record BalanceUpdate(long balance) implements CustomPacketPayload {
      public static final Type<EconomyPayloads.BalanceUpdate> TYPE = new Type(Identifier.fromNamespaceAndPath("runevestiges", "economy_balance"));
      public static final StreamCodec<RegistryFriendlyByteBuf, EconomyPayloads.BalanceUpdate> CODEC = StreamCodec.of(
         (buffer, payload) -> buffer.writeVarLong(payload.balance()), buffer -> new EconomyPayloads.BalanceUpdate(buffer.readVarLong())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record Listing(long id, String sellerName, long price, boolean own, ItemStack item) {
      public Listing(long id, String sellerName, long price, boolean own, ItemStack item) {
         item = item.copy();
         this.id = id;
         this.sellerName = sellerName;
         this.price = price;
         this.own = own;
         this.item = item;
      }

      public ItemStack item() {
         return this.item.copy();
      }

      private static void encode(RegistryFriendlyByteBuf buffer, EconomyPayloads.Listing listing) {
         buffer.writeVarLong(listing.id());
         buffer.writeUtf(listing.sellerName(), 64);
         buffer.writeVarLong(listing.price());
         buffer.writeBoolean(listing.own());
         ItemStack.STREAM_CODEC.encode(buffer, listing.item());
      }

      private static EconomyPayloads.Listing decode(RegistryFriendlyByteBuf buffer) {
         return new EconomyPayloads.Listing(
            buffer.readVarLong(), buffer.readUtf(64), buffer.readVarLong(), buffer.readBoolean(), (ItemStack)ItemStack.STREAM_CODEC.decode(buffer)
         );
      }
   }

   public record Offer(long id, String buyerName, long amount, ItemStack item) {
      public Offer(long id, String buyerName, long amount, ItemStack item) {
         item = item.copy();
         this.id = id;
         this.buyerName = buyerName;
         this.amount = amount;
         this.item = item;
      }

      public ItemStack item() {
         return this.item.copy();
      }

      private static void encode(RegistryFriendlyByteBuf buffer, EconomyPayloads.Offer offer) {
         buffer.writeVarLong(offer.id());
         buffer.writeUtf(offer.buyerName(), 64);
         buffer.writeVarLong(offer.amount());
         ItemStack.STREAM_CODEC.encode(buffer, offer.item());
      }

      private static EconomyPayloads.Offer decode(RegistryFriendlyByteBuf buffer) {
         return new EconomyPayloads.Offer(buffer.readVarLong(), buffer.readUtf(64), buffer.readVarLong(), (ItemStack)ItemStack.STREAM_CODEC.decode(buffer));
      }
   }

   public record Snapshot(
      long balance,
      int page,
      int pageCount,
      int tab,
      int view,
      String message,
      List<EconomyPayloads.Listing> listings,
      List<EconomyPayloads.Offer> offers,
      List<String> onlinePlayers
   ) implements CustomPacketPayload {
      public static final Type<EconomyPayloads.Snapshot> TYPE = new Type(Identifier.fromNamespaceAndPath("runevestiges", "economy_snapshot"));
      public static final StreamCodec<RegistryFriendlyByteBuf, EconomyPayloads.Snapshot> CODEC = StreamCodec.of((buffer, payload) -> {
         buffer.writeVarLong(payload.balance());
         buffer.writeVarInt(payload.page());
         buffer.writeVarInt(payload.pageCount());
         buffer.writeVarInt(payload.tab());
         buffer.writeVarInt(payload.view());
         buffer.writeUtf(payload.message(), 256);
         buffer.writeVarInt(payload.listings().size());

         for (EconomyPayloads.Listing listing : payload.listings()) {
            EconomyPayloads.Listing.encode(buffer, listing);
         }

         buffer.writeVarInt(payload.offers().size());

         for (EconomyPayloads.Offer offer : payload.offers()) {
            EconomyPayloads.Offer.encode(buffer, offer);
         }

         buffer.writeVarInt(payload.onlinePlayers().size());

         for (String playerName : payload.onlinePlayers()) {
            buffer.writeUtf(playerName, 64);
         }
      }, buffer -> {
         long balance = buffer.readVarLong();
         int page = buffer.readVarInt();
         int pageCount = buffer.readVarInt();
         int tab = buffer.readVarInt();
         int view = buffer.readVarInt();
         String message = buffer.readUtf(256);
         int count = buffer.readVarInt();
         if (count >= 0 && count <= 5) {
            List<EconomyPayloads.Listing> listings = new ArrayList<>(count);

            for (int index = 0; index < count; index++) {
               listings.add(EconomyPayloads.Listing.decode(buffer));
            }

            int offerCount = buffer.readVarInt();
            if (offerCount >= 0 && offerCount <= 5) {
               List<EconomyPayloads.Offer> offers = new ArrayList<>(offerCount);

               for (int index = 0; index < offerCount; index++) {
                  offers.add(EconomyPayloads.Offer.decode(buffer));
               }

               int onlinePlayerCount = buffer.readVarInt();
               if (onlinePlayerCount >= 0 && onlinePlayerCount <= 100) {
                  List<String> onlinePlayers = new ArrayList<>(onlinePlayerCount);

                  for (int index = 0; index < onlinePlayerCount; index++) {
                     onlinePlayers.add(buffer.readUtf(64));
                  }

                  return new EconomyPayloads.Snapshot(balance, page, pageCount, tab, view, message, listings, offers, onlinePlayers);
               } else {
                  throw new IllegalArgumentException("Invalid online player count: " + onlinePlayerCount);
               }
            } else {
               throw new IllegalArgumentException("Invalid marketplace offer page size: " + offerCount);
            }
         } else {
            throw new IllegalArgumentException("Invalid marketplace page size: " + count);
         }
      });

      public Snapshot(
         long balance,
         int page,
         int pageCount,
         int tab,
         int view,
         String message,
         List<EconomyPayloads.Listing> listings,
         List<EconomyPayloads.Offer> offers,
         List<String> onlinePlayers
      ) {
         listings = List.copyOf(listings);
         offers = List.copyOf(offers);
         onlinePlayers = List.copyOf(onlinePlayers);
         this.balance = balance;
         this.page = page;
         this.pageCount = pageCount;
         this.tab = tab;
         this.view = view;
         this.message = message;
         this.listings = listings;
         this.offers = offers;
         this.onlinePlayers = onlinePlayers;
      }

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
