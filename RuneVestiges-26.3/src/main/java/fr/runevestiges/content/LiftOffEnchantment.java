package fr.runevestiges.content;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public final class LiftOffEnchantment {
   private static final ResourceKey<Enchantment> LIFT_OFF = ResourceKey.create(
      Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("runevestiges", "lift_off")
   );
   private static final Map<UUID, Long> LAST_LAUNCH = new ConcurrentHashMap<>();
   private static final Map<UUID, Integer> PENDING_FLIGHT_STARTS = new ConcurrentHashMap<>();
   private static final long SERVER_COOLDOWN_MILLIS = 1200L;
   private static final int GROUND_LAUNCH_TIMEOUT_TICKS = 20;

   private LiftOffEnchantment() {
   }

   public static void register() {
      PayloadTypeRegistry.serverboundPlay().register(LiftOffEnchantment.LiftOffRequest.TYPE, LiftOffEnchantment.LiftOffRequest.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(
         LiftOffEnchantment.LiftOffRequest.TYPE, (payload, context) -> context.server().execute(() -> launch(context.player()))
      );
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer ticksLeft = PENDING_FLIGHT_STARTS.get(player.getUUID());
            if (ticksLeft != null) {
               if (!player.isAlive() || player.isFallFlying()) {
                  PENDING_FLIGHT_STARTS.remove(player.getUUID());
               } else if (!player.onGround()) {
                  PENDING_FLIGHT_STARTS.remove(player.getUUID());
                  player.startFallFlying();
               } else if (ticksLeft <= 1) {
                  PENDING_FLIGHT_STARTS.remove(player.getUUID());
               } else {
                  PENDING_FLIGHT_STARTS.put(player.getUUID(), ticksLeft - 1);
               }
            }
         }
      });
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
         PENDING_FLIGHT_STARTS.remove(handler.player.getUUID());
         LAST_LAUNCH.remove(handler.player.getUUID());
      });
   }

   private static void launch(ServerPlayer player) {
      if (player.isAlive() && !player.isFallFlying() && !player.isPassenger()) {
         ItemStack elytra = player.getItemBySlot(EquipmentSlot.CHEST);
         if (elytra.is(Items.ELYTRA)) {
            Registry<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            int level = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(LIFT_OFF), elytra);
            if (level >= 1) {
               long now = System.currentTimeMillis();
               long lastLaunch = LAST_LAUNCH.getOrDefault(player.getUUID(), 0L);
               if (now - lastLaunch >= 1200L) {
                  LAST_LAUNCH.put(player.getUUID(), now);
                  Vec3 velocity = player.getDeltaMovement();
                  float yaw = (float)Math.toRadians(player.getYRot());
                  double forwardBoost = 0.5 + level * 0.1;
                  player.setDeltaMovement(velocity.x - Math.sin(yaw) * forwardBoost, 4.0 + level * 0.5, velocity.z + Math.cos(yaw) * forwardBoost);
                  player.syncVelocity = true;
                  player.connection.send(new ClientboundSetEntityMotionPacket(player));
                  player.resetFallDistance();
                  PENDING_FLIGHT_STARTS.put(player.getUUID(), 20);
               }
            }
         }
      }
   }

   public record LiftOffRequest() implements CustomPacketPayload {
      public static final Type<LiftOffEnchantment.LiftOffRequest> TYPE = new Type(Identifier.fromNamespaceAndPath("runevestiges", "lift_off"));
      public static final StreamCodec<RegistryFriendlyByteBuf, LiftOffEnchantment.LiftOffRequest> CODEC = StreamCodec.unit(
         new LiftOffEnchantment.LiftOffRequest()
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
