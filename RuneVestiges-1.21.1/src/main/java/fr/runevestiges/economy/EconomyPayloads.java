package fr.runevestiges.economy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class EconomyPayloads {
	private static final int MAX_PAGE_LISTINGS = 5;
	private static final int MAX_STATUS_LENGTH = 256;
	private static final int MAX_PLAYER_NAME_LENGTH = 64;
	private static final int MAX_ONLINE_PLAYERS = 100;

	private EconomyPayloads() {
	}

	public record Action(
		int action,
		long listingId,
		String text,
		long amount,
		int page,
		int tab,
		int view
	) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath("runevestiges", "economy_action")
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
			(buffer, payload) -> {
				buffer.writeVarInt(payload.action());
				buffer.writeVarLong(payload.listingId());
				buffer.writeUtf(payload.text(), MAX_PLAYER_NAME_LENGTH);
				buffer.writeVarLong(payload.amount());
				buffer.writeVarInt(payload.page());
				buffer.writeVarInt(payload.tab());
				buffer.writeVarInt(payload.view());
			},
			buffer -> new Action(
				buffer.readVarInt(),
				buffer.readVarLong(),
				buffer.readUtf(MAX_PLAYER_NAME_LENGTH),
				buffer.readVarLong(),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readVarInt()
			)
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Listing(long id, String sellerName, long price, boolean own, ItemStack item) {
		public Listing {
			item = item.copy();
		}

		@Override
		public ItemStack item() {
			return item.copy();
		}

		private static void encode(RegistryFriendlyByteBuf buffer, Listing listing) {
			buffer.writeVarLong(listing.id());
			buffer.writeUtf(listing.sellerName(), MAX_PLAYER_NAME_LENGTH);
			buffer.writeVarLong(listing.price());
			buffer.writeBoolean(listing.own());
			ItemStack.STREAM_CODEC.encode(buffer, listing.item());
		}

		private static Listing decode(RegistryFriendlyByteBuf buffer) {
			return new Listing(
				buffer.readVarLong(),
				buffer.readUtf(MAX_PLAYER_NAME_LENGTH),
				buffer.readVarLong(),
				buffer.readBoolean(),
				ItemStack.STREAM_CODEC.decode(buffer)
			);
		}
	}

	public record Offer(long id, String buyerName, long amount, ItemStack item) {
		public Offer {
			item = item.copy();
		}

		@Override
		public ItemStack item() {
			return item.copy();
		}

		private static void encode(RegistryFriendlyByteBuf buffer, Offer offer) {
			buffer.writeVarLong(offer.id());
			buffer.writeUtf(offer.buyerName(), MAX_PLAYER_NAME_LENGTH);
			buffer.writeVarLong(offer.amount());
			ItemStack.STREAM_CODEC.encode(buffer, offer.item());
		}

		private static Offer decode(RegistryFriendlyByteBuf buffer) {
			return new Offer(
				buffer.readVarLong(),
				buffer.readUtf(MAX_PLAYER_NAME_LENGTH),
				buffer.readVarLong(),
				ItemStack.STREAM_CODEC.decode(buffer)
			);
		}
	}

	public record Snapshot(
		long balance,
		int page,
		int pageCount,
		int tab,
		int view,
		String message,
		List<Listing> listings,
		List<Offer> offers,
		List<String> onlinePlayers
	) implements CustomPacketPayload {
		public static final Type<Snapshot> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath("runevestiges", "economy_snapshot")
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
			(buffer, payload) -> {
				buffer.writeVarLong(payload.balance());
				buffer.writeVarInt(payload.page());
				buffer.writeVarInt(payload.pageCount());
				buffer.writeVarInt(payload.tab());
				buffer.writeVarInt(payload.view());
				buffer.writeUtf(payload.message(), MAX_STATUS_LENGTH);
				buffer.writeVarInt(payload.listings().size());
				for (Listing listing : payload.listings()) {
					Listing.encode(buffer, listing);
				}
				buffer.writeVarInt(payload.offers().size());
				for (Offer offer : payload.offers()) {
					Offer.encode(buffer, offer);
				}
				buffer.writeVarInt(payload.onlinePlayers().size());
				for (String playerName : payload.onlinePlayers()) {
					buffer.writeUtf(playerName, MAX_PLAYER_NAME_LENGTH);
				}
			},
			buffer -> {
				long balance = buffer.readVarLong();
				int page = buffer.readVarInt();
				int pageCount = buffer.readVarInt();
				int tab = buffer.readVarInt();
				int view = buffer.readVarInt();
				String message = buffer.readUtf(MAX_STATUS_LENGTH);
				int count = buffer.readVarInt();
				if (count < 0 || count > MAX_PAGE_LISTINGS) {
					throw new IllegalArgumentException("Invalid marketplace page size: " + count);
				}

				List<Listing> listings = new ArrayList<>(count);
				for (int index = 0; index < count; index++) {
					listings.add(Listing.decode(buffer));
				}
				int offerCount = buffer.readVarInt();
				if (offerCount < 0 || offerCount > MAX_PAGE_LISTINGS) {
					throw new IllegalArgumentException("Invalid marketplace offer page size: " + offerCount);
				}
				List<Offer> offers = new ArrayList<>(offerCount);
				for (int index = 0; index < offerCount; index++) {
					offers.add(Offer.decode(buffer));
				}
				int onlinePlayerCount = buffer.readVarInt();
				if (onlinePlayerCount < 0 || onlinePlayerCount > MAX_ONLINE_PLAYERS) {
					throw new IllegalArgumentException("Invalid online player count: " + onlinePlayerCount);
				}
				List<String> onlinePlayers = new ArrayList<>(onlinePlayerCount);
				for (int index = 0; index < onlinePlayerCount; index++) {
					onlinePlayers.add(buffer.readUtf(MAX_PLAYER_NAME_LENGTH));
				}
				return new Snapshot(balance, page, pageCount, tab, view, message, listings, offers, onlinePlayers);
			}
		);

		public Snapshot {
			listings = List.copyOf(listings);
			offers = List.copyOf(offers);
			onlinePlayers = List.copyOf(onlinePlayers);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record BalanceUpdate(long balance) implements CustomPacketPayload {
		public static final Type<BalanceUpdate> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath("runevestiges", "economy_balance")
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, BalanceUpdate> CODEC = StreamCodec.of(
			(buffer, payload) -> buffer.writeVarLong(payload.balance()),
			buffer -> new BalanceUpdate(buffer.readVarLong())
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
