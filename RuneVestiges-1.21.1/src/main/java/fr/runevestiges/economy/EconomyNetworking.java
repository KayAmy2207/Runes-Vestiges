package fr.runevestiges.economy;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.LongArgumentType;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class EconomyNetworking {
	private EconomyNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(EconomyPayloads.Action.TYPE, EconomyPayloads.Action.CODEC);
		PayloadTypeRegistry.playS2C().register(EconomyPayloads.Snapshot.TYPE, EconomyPayloads.Snapshot.CODEC);
		PayloadTypeRegistry.playS2C().register(
			EconomyPayloads.BalanceUpdate.TYPE,
			EconomyPayloads.BalanceUpdate.CODEC
		);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (EconomyService.grantStarterBonus(server, handler.player.getUUID())) {
				handler.player.sendSystemMessage(Component.translatable(
					"message.runevestiges.economy.starter_bonus",
					EconomyService.STARTER_BONUS
				));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(
			EconomyPayloads.Action.TYPE,
			(payload, context) -> context.server().execute(() -> handle(context.player(), payload))
		);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("economy")
				.then(Commands.literal("balance")
					.executes(context -> showBalance(context.getSource(), context.getSource().getPlayerOrException()))
					.then(Commands.argument("player", EntityArgument.player())
						.requires(source -> source.hasPermission(2))
						.executes(context -> showBalance(
							context.getSource(),
							EntityArgument.getPlayer(context, "player")
						))))
				.then(Commands.literal("give")
					.requires(source -> source.hasPermission(2))
					.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("amount", LongArgumentType.longArg(1L))
							.executes(context -> changeBalance(
								context.getSource(),
								EntityArgument.getPlayer(context, "player"),
								LongArgumentType.getLong(context, "amount"),
								true
							)))))
				.then(Commands.literal("take")
					.requires(source -> source.hasPermission(2))
					.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("amount", LongArgumentType.longArg(1L))
							.executes(context -> changeBalance(
								context.getSource(),
								EntityArgument.getPlayer(context, "player"),
								LongArgumentType.getLong(context, "amount"),
								false
							)))))
			)
		);
	}

	private static void handle(ServerPlayer player, EconomyPayloads.Action action) {
		int tab = switch (action.tab()) {
			case EconomyService.MARKET_TAB -> EconomyService.MARKET_TAB;
			case EconomyService.SERVER_SHOP_TAB -> EconomyService.SERVER_SHOP_TAB;
			default -> EconomyService.WALLET_TAB;
		};
		int view = tab == EconomyService.MARKET_TAB
				&& action.view() == EconomyService.MARKET_VIEW_OFFERS
			? EconomyService.MARKET_VIEW_OFFERS
			: EconomyService.MARKET_VIEW_BROWSE;
		int page = Math.max(0, action.page());
		String message = "";

		switch (action.action()) {
			case EconomyService.OPEN_HOME -> {
				tab = EconomyService.HOME_TAB;
				view = EconomyService.MARKET_VIEW_BROWSE;
			}
			case EconomyService.OPEN_WALLET -> {
				tab = EconomyService.WALLET_TAB;
				view = EconomyService.MARKET_VIEW_BROWSE;
			}
			case EconomyService.OPEN_MARKET -> {
				tab = EconomyService.MARKET_TAB;
				view = EconomyService.MARKET_VIEW_BROWSE;
			}
			case EconomyService.OPEN_SERVER_SHOP -> {
				tab = EconomyService.SERVER_SHOP_TAB;
				view = EconomyService.MARKET_VIEW_BROWSE;
			}
			case EconomyService.REFRESH -> {
			}
			case EconomyService.SELL_HELD_ITEM -> {
				tab = EconomyService.MARKET_TAB;
				view = EconomyService.MARKET_VIEW_BROWSE;
				message = EconomyService.listHeldItem(player, action.amount()).translationKey();
			}
			case EconomyService.SELL_INVENTORY_SLOT -> {
				tab = EconomyService.MARKET_TAB;
				view = EconomyService.MARKET_VIEW_BROWSE;
				if (action.listingId() < Integer.MIN_VALUE || action.listingId() > Integer.MAX_VALUE) {
					message = EconomyService.Result.INVALID_SLOT.translationKey();
				} else {
					message = EconomyService.listInventorySlot(
						player,
						(int) action.listingId(),
						action.amount()
					).translationKey();
				}
			}
			case EconomyService.SELL_TO_SERVER -> {
				tab = EconomyService.SERVER_SHOP_TAB;
				view = EconomyService.MARKET_VIEW_BROWSE;
				if (action.listingId() < Integer.MIN_VALUE || action.listingId() > Integer.MAX_VALUE) {
					message = EconomyService.Result.INVALID_SLOT.translationKey();
				} else {
					message = EconomyService.sellToServer(player, (int) action.listingId()).translationKey();
				}
			}
			case EconomyService.BUY_LISTING -> {
				tab = EconomyService.MARKET_TAB;
				view = EconomyService.MARKET_VIEW_BROWSE;
				message = EconomyService.purchase(player, action.listingId()).translationKey();
			}
			case EconomyService.CANCEL_LISTING -> {
				tab = EconomyService.MARKET_TAB;
				view = EconomyService.MARKET_VIEW_BROWSE;
				message = EconomyService.cancelListing(player, action.listingId()).translationKey();
			}
			case EconomyService.CREATE_OFFER -> {
				tab = EconomyService.MARKET_TAB;
				view = EconomyService.MARKET_VIEW_BROWSE;
				message = EconomyService.createOffer(player, action.listingId(), action.amount()).translationKey();
			}
			case EconomyService.ACCEPT_OFFER -> {
				tab = EconomyService.MARKET_TAB;
				view = EconomyService.MARKET_VIEW_OFFERS;
				message = EconomyService.acceptOffer(player, action.listingId()).translationKey();
			}
			case EconomyService.DECLINE_OFFER -> {
				tab = EconomyService.MARKET_TAB;
				view = EconomyService.MARKET_VIEW_OFFERS;
				message = EconomyService.declineOffer(player, action.listingId()).translationKey();
			}
			case EconomyService.TRANSFER -> {
				tab = EconomyService.WALLET_TAB;
				view = EconomyService.MARKET_VIEW_BROWSE;
				EconomyService.Result result = transfer(player, action.text(), action.amount());
				message = result.translationKey();
				if (result == EconomyService.Result.SUCCESS) {
					ServerPlayer recipient = player.server.getPlayerList().getPlayerByName(action.text().trim());
					if (recipient != null) {
						recipient.sendSystemMessage(Component.translatable(
							"message.runevestiges.economy.transfer_received",
							player.getGameProfile().getName(),
							action.amount()
						));
						ServerPlayNetworking.send(
							recipient,
							new EconomyPayloads.BalanceUpdate(EconomyService.wallet(
								player.server,
								recipient.getUUID()
							).balance())
						);
					}
				}
			}
			default -> message = EconomyService.Result.INVALID_ACTION.translationKey();
		}

		ServerPlayNetworking.send(player, EconomyService.snapshot(player, tab, page, view, message));
	}

	private static EconomyService.Result transfer(ServerPlayer sender, String targetName, long amount) {
		String normalizedName = targetName.trim();
		if (normalizedName.isEmpty()) {
			return EconomyService.Result.INVALID_RECIPIENT;
		}

		Player onlinePlayer = sender.server.getPlayerList().getPlayerByName(normalizedName);
		UUID recipientId;
		if (onlinePlayer != null) {
			recipientId = onlinePlayer.getUUID();
		} else {
			GameProfile cachedProfile = sender.server.getProfileCache()
				.get(normalizedName)
				.orElse(null);
			if (cachedProfile == null) {
				return EconomyService.Result.INVALID_RECIPIENT;
			}
			recipientId = cachedProfile.getId();
		}
		return EconomyService.transfer(sender.server, sender.getUUID(), recipientId, amount);
	}

	private static int showBalance(
		net.minecraft.commands.CommandSourceStack source,
		ServerPlayer player
	) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		long balance = EconomyData.get(source.getServer()).balance(player.getUUID());
		source.sendSuccess(
			() -> Component.translatable("message.runevestiges.economy.balance", player.getGameProfile().getName(), balance),
			false
		);
		return 1;
	}

	private static int changeBalance(
		net.minecraft.commands.CommandSourceStack source,
		ServerPlayer player,
		long amount,
		boolean credit
	) {
		EconomyService.Result result = credit
			? EconomyService.addFunds(source.getServer(), player.getUUID(), amount)
			: EconomyService.withdrawFunds(source.getServer(), player.getUUID(), amount);
		if (result != EconomyService.Result.SUCCESS) {
			source.sendFailure(Component.translatable(result.translationKey()));
			return 0;
		}

		long balance = EconomyData.get(source.getServer()).balance(player.getUUID());
		String translationKey = credit
			? "message.runevestiges.economy.admin_give"
			: "message.runevestiges.economy.admin_take";
		source.sendSuccess(() -> Component.translatable(
			translationKey,
			amount,
			player.getGameProfile().getName(),
			balance
		), true);
		return 1;
	}
}
