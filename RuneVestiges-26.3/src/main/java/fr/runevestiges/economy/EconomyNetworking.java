package fr.runevestiges.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;

public final class EconomyNetworking {
   private EconomyNetworking() {
   }

   public static void register() {
      PayloadTypeRegistry.serverboundPlay().register(EconomyPayloads.Action.TYPE, EconomyPayloads.Action.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(EconomyPayloads.Snapshot.TYPE, EconomyPayloads.Snapshot.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(EconomyPayloads.BalanceUpdate.TYPE, EconomyPayloads.BalanceUpdate.CODEC);
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
         if (EconomyService.grantStarterBonus(server, handler.player.getUUID())) {
            handler.player.sendSystemMessage(Component.translatable("message.runevestiges.economy.starter_bonus", new Object[]{100L}));
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(
         EconomyPayloads.Action.TYPE, (payload, context) -> context.server().execute(() -> handle(context.player(), payload))
      );
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("economy")
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("balance")
                                 .executes(
                                    context -> showBalance(
                                       (CommandSourceStack)context.getSource(), ((CommandSourceStack)context.getSource()).getPlayerOrException()
                                    )
                                 ))
                              .then(
                                 ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player())
                                       .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)))
                                    .executes(context -> showBalance((CommandSourceStack)context.getSource(), EntityArgument.getPlayer(context, "player")))
                              )
                        ))
                     .then(
                        ((LiteralArgumentBuilder)Commands.literal("give")
                              .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)))
                           .then(
                              Commands.argument("player", EntityArgument.player())
                                 .then(
                                    Commands.argument("amount", LongArgumentType.longArg(1L))
                                       .executes(
                                          context -> changeBalance(
                                             (CommandSourceStack)context.getSource(),
                                             EntityArgument.getPlayer(context, "player"),
                                             LongArgumentType.getLong(context, "amount"),
                                             true
                                          )
                                       )
                                 )
                           )
                     ))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("take").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)))
                        .then(
                           Commands.argument("player", EntityArgument.player())
                              .then(
                                 Commands.argument("amount", LongArgumentType.longArg(1L))
                                    .executes(
                                       context -> changeBalance(
                                          (CommandSourceStack)context.getSource(),
                                          EntityArgument.getPlayer(context, "player"),
                                          LongArgumentType.getLong(context, "amount"),
                                          false
                                       )
                                    )
                              )
                        )
                  )
            )
         );
   }

   private static void handle(ServerPlayer player, EconomyPayloads.Action action) {
      int tab = switch (action.tab()) {
         case 1 -> 1;
         case 3 -> 3;
         default -> 0;
      };
      int view = tab == 1 && action.view() == 1 ? 1 : 0;
      int page = Math.max(0, action.page());
      String message = "";
      switch (action.action()) {
         case 0:
            tab = 0;
            view = 0;
            break;
         case 1:
            tab = 1;
            view = 0;
         case 2:
            break;
         case 3:
            tab = 1;
            view = 0;
            message = EconomyService.listHeldItem(player, action.amount()).translationKey();
            break;
         case 4:
            tab = 1;
            view = 0;
            message = EconomyService.purchase(player, action.listingId()).translationKey();
            break;
         case 5:
            tab = 1;
            view = 0;
            message = EconomyService.cancelListing(player, action.listingId()).translationKey();
            break;
         case 6:
            tab = 0;
            view = 0;
            EconomyService.Result result = transfer(player, action.text(), action.amount());
            message = result.translationKey();
            if (result == EconomyService.Result.SUCCESS) {
               ServerPlayer recipient = player.level().getServer().getPlayerList().getPlayerByName(action.text().trim());
               if (recipient != null) {
                  recipient.sendSystemMessage(
                     Component.translatable("message.runevestiges.economy.transfer_received", new Object[]{player.getGameProfile().name(), action.amount()})
                  );
                  ServerPlayNetworking.send(
                     recipient, new EconomyPayloads.BalanceUpdate(EconomyService.wallet(player.level().getServer(), recipient.getUUID()).balance())
                  );
               }
            }
            break;
         case 7:
            tab = 2;
            view = 0;
            break;
         case 8:
            tab = 1;
            view = 0;
            if (action.listingId() >= -2147483648L && action.listingId() <= 2147483647L) {
               message = EconomyService.listInventorySlot(player, (int)action.listingId(), action.amount()).translationKey();
            } else {
               message = EconomyService.Result.INVALID_SLOT.translationKey();
            }
            break;
         case 9:
            tab = 3;
            view = 0;
            if (action.listingId() >= -2147483648L && action.listingId() <= 2147483647L) {
               message = EconomyService.sellToServer(player, (int)action.listingId()).translationKey();
            } else {
               message = EconomyService.Result.INVALID_SLOT.translationKey();
            }
            break;
         case 10:
            tab = 3;
            view = 0;
            break;
         case 11:
            tab = 1;
            view = 0;
            message = EconomyService.createOffer(player, action.listingId(), action.amount()).translationKey();
            break;
         case 12:
            tab = 1;
            view = 1;
            message = EconomyService.acceptOffer(player, action.listingId()).translationKey();
            break;
         case 13:
            tab = 1;
            view = 1;
            message = EconomyService.declineOffer(player, action.listingId()).translationKey();
            break;
         default:
            message = EconomyService.Result.INVALID_ACTION.translationKey();
      }

      ServerPlayNetworking.send(player, EconomyService.snapshot(player, tab, page, view, message));
   }

   private static EconomyService.Result transfer(ServerPlayer sender, String targetName, long amount) {
      String normalizedName = targetName.trim();
      if (normalizedName.isEmpty()) {
         return EconomyService.Result.INVALID_RECIPIENT;
      } else {
         Player onlinePlayer = sender.level().getServer().getPlayerList().getPlayerByName(normalizedName);
         UUID recipientId;
         if (onlinePlayer != null) {
            recipientId = onlinePlayer.getUUID();
         } else {
            NameAndId cachedProfile = (NameAndId)sender.level().getServer().services().nameToIdCache().get(normalizedName).orElse(null);
            if (cachedProfile == null) {
               return EconomyService.Result.INVALID_RECIPIENT;
            }

            recipientId = cachedProfile.id();
         }

         return EconomyService.transfer(sender.level().getServer(), sender.getUUID(), recipientId, amount);
      }
   }

   private static int showBalance(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException {
      long balance = EconomyData.get(source.getServer()).balance(player.getUUID());
      source.sendSuccess(() -> Component.translatable("message.runevestiges.economy.balance", new Object[]{player.getGameProfile().name(), balance}), false);
      return 1;
   }

   private static int changeBalance(CommandSourceStack source, ServerPlayer player, long amount, boolean credit) {
      EconomyService.Result result = credit
         ? EconomyService.addFunds(source.getServer(), player.getUUID(), amount)
         : EconomyService.withdrawFunds(source.getServer(), player.getUUID(), amount);
      if (result != EconomyService.Result.SUCCESS) {
         source.sendFailure(Component.translatable(result.translationKey()));
         return 0;
      } else {
         long balance = EconomyData.get(source.getServer()).balance(player.getUUID());
         String translationKey = credit ? "message.runevestiges.economy.admin_give" : "message.runevestiges.economy.admin_take";
         source.sendSuccess(() -> Component.translatable(translationKey, new Object[]{amount, player.getGameProfile().name(), balance}), true);
         return 1;
      }
   }
}
