package fr.runevestiges.client;

import fr.runevestiges.economy.EconomyPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;

public final class EconomyClientNetworking {
   private static final Category ECONOMY_CATEGORY = Category.register(Identifier.fromNamespaceAndPath("runevestiges", "economy"));
   private static final KeyMapping OPEN_ECONOMY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.runevestiges.open_economy", 12, ECONOMY_CATEGORY));

   private EconomyClientNetworking() {
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(
         EconomyPayloads.Snapshot.TYPE, (payload, context) -> context.client().execute(() -> EconomyScreen.receive(payload))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         EconomyPayloads.BalanceUpdate.TYPE, (payload, context) -> context.client().execute(() -> EconomyScreen.updateBalance(payload.balance()))
      );
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (OPEN_ECONOMY.consumeClick() && client.player != null && client.canInterruptScreen()) {
            EconomyScreen.open(2);
         }
      });
   }

   public static boolean send(EconomyPayloads.Action action) {
      if (!ClientPlayNetworking.canSend(EconomyPayloads.Action.TYPE)) {
         return false;
      } else {
         ClientPlayNetworking.send(action);
         return true;
      }
   }
}
