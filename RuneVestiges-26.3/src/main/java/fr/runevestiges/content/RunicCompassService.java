package fr.runevestiges.content;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

public final class RunicCompassService {
   private static final int UPDATE_INTERVAL_TICKS = 20;
   private static final int DETECT_RADIUS = 30;
   private static final int DONE_FLASH_TICKS = 30;
   private static final int USE_COOLDOWN_TICKS = 10;
   private static final ResourceKey<StructureSet> RUNIC_TOWER_SET = ResourceKey.create(
      Registries.STRUCTURE_SET, Identifier.fromNamespaceAndPath("runevestiges", "runic_towers")
   );
   private static final TagKey<Biome> RUNIC_TOWER_BIOMES = TagKey.create(
      Registries.BIOME, Identifier.fromNamespaceAndPath("runevestiges", "runic_tower_biomes")
   );
   private static final Map<UUID, RunicCompassService.Flash> FLASHES = new HashMap<>();
   private static final Map<UUID, Long> LAST_USE = new HashMap<>();
   private static final Set<UUID> TRACKED_PLAYERS = new HashSet<>();

   private RunicCompassService() {
   }

   public static void register() {
      PayloadTypeRegistry.clientboundPlay().register(RunicCompassPayloads.Update.TYPE, RunicCompassPayloads.Update.CODEC);
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> sendSnapshot(handler.player, null, 0));
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
         UUID playerId = handler.player.getUUID();
         FLASHES.remove(playerId);
         LAST_USE.remove(playerId);
         TRACKED_PLAYERS.remove(playerId);
      });
      ServerTickEvents.END_SERVER_TICK.register(RunicCompassService::tick);
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)Commands.literal("runevestiges")
                  .then(
                     Commands.literal("compass")
                        .then(
                           ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("reset")
                                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)))
                                 .executes(context -> reset(((CommandSourceStack)context.getSource()).getPlayerOrException())))
                              .then(
                                 Commands.argument("player", EntityArgument.player()).executes(context -> reset(EntityArgument.getPlayer(context, "player")))
                              )
                        )
                  )
            )
         );
   }

   public static InteractionResult use(Level level, Player player, InteractionHand hand) {
      if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
         ServerLevel serverLevel = serverPlayer.level();
         long gameTime = serverLevel.getGameTime();
         long previousUse = LAST_USE.getOrDefault(serverPlayer.getUUID(), Long.MIN_VALUE);
         if (previousUse != Long.MIN_VALUE && gameTime - previousUse < 10L) {
            return InteractionResult.CONSUME;
         } else {
            LAST_USE.put(serverPlayer.getUUID(), gameTime);
            if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
               serverPlayer.sendOverlayMessage(Component.translatable("message.runevestiges.compass.not_overworld"));
               return InteractionResult.CONSUME;
            } else {
               RunicCompassData data = RunicCompassData.get(serverPlayer.level().getServer());
               RunicCompassService.Target target = findNearestTowerInRadius(serverPlayer);
               if (target == null) {
                  serverPlayer.sendOverlayMessage(Component.translatable("message.runevestiges.compass.too_far"));
                  sendPlayerSnapshot(serverPlayer);
                  return InteractionResult.CONSUME;
               } else {
                  boolean completed = data.toggleCompleted(serverPlayer.getUUID(), target.chunk());
                  if (completed) {
                     FLASHES.put(serverPlayer.getUUID(), new RunicCompassService.Flash(target, gameTime + 30L));
                     serverPlayer.sendOverlayMessage(Component.translatable("message.runevestiges.compass.completed"));
                     sendSnapshot(serverPlayer, target, 2);
                  } else {
                     FLASHES.remove(serverPlayer.getUUID());
                     serverPlayer.sendOverlayMessage(Component.translatable("message.runevestiges.compass.reactivated"));
                     sendPlayerSnapshot(serverPlayer);
                  }

                  serverLevel.playSound(
                     null, serverPlayer.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, completed ? 1.35F : 0.9F
                  );
                  return InteractionResult.CONSUME;
               }
            }
         }
      } else {
         return InteractionResult.SUCCESS;
      }
   }

   private static void tick(MinecraftServer server) {
      long gameTime = server.overworld().getGameTime();
      boolean refresh = gameTime % 20L == 0L;

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         UUID playerId = player.getUUID();
         RunicCompassService.Flash flash = FLASHES.get(playerId);
         if (flash != null) {
            if (gameTime >= flash.expiresAt()) {
               FLASHES.remove(playerId);
               sendPlayerSnapshot(player);
            }
         } else if (refresh) {
            boolean hasCompass = hasCompass(player);
            if (hasCompass || TRACKED_PLAYERS.contains(playerId)) {
               sendPlayerSnapshot(player);
            }
         }
      }
   }

   private static void sendPlayerSnapshot(ServerPlayer player) {
      if (!hasCompass(player)) {
         TRACKED_PLAYERS.remove(player.getUUID());
         sendSnapshot(player, null, 0);
      } else {
         TRACKED_PLAYERS.add(player.getUUID());
         if (!player.level().dimension().equals(Level.OVERWORLD)) {
            sendSnapshot(player, null, 0);
         } else {
            RunicCompassService.Target target = findNearest(player, RunicCompassData.get(player.level().getServer()));
            if (target == null) {
               sendSnapshot(player, null, 0);
            } else {
               int state = target.distanceSquared() <= 900.0 ? 1 : 0;
               sendSnapshot(player, target, state);
            }
         }
      }
   }

   private static void sendSnapshot(ServerPlayer player, RunicCompassService.Target target, int state) {
      int targetX = target == null ? 0 : target.blockX();
      int targetZ = target == null ? 0 : target.blockZ();
      ServerPlayNetworking.send(player, new RunicCompassPayloads.Update(targetX, targetZ, state, target != null));
   }

   private static boolean hasCompass(ServerPlayer player) {
      for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
         if (player.getInventory().getItem(slot).is(ModContent.RUNIC_COMPASS)) {
            return true;
         }
      }

      return player.getOffhandItem().is(ModContent.RUNIC_COMPASS);
   }

   private static RunicCompassService.Target findNearestTowerInRadius(ServerPlayer player) {
      ServerLevel level = player.level();
      RandomSpreadStructurePlacement spread = getTowerPlacement(level);
      BiomeResolver biomeResolver = level.getChunkSource().getGenerator().getBiomeSource().createUncachedResolver(level.getChunkSource().randomState());
      int regionRadius = (int)Math.ceil(30.0 / (spread.spacing() * 16.0)) + 1;
      ChunkPos playerChunk = player.chunkPosition();
      int playerRegionX = Math.floorDiv(playerChunk.x(), spread.spacing());
      int playerRegionZ = Math.floorDiv(playerChunk.z(), spread.spacing());
      RunicCompassService.Target nearest = null;

      for (int offsetX = -regionRadius; offsetX <= regionRadius; offsetX++) {
         for (int offsetZ = -regionRadius; offsetZ <= regionRadius; offsetZ++) {
            ChunkPos towerChunk = spread.getPotentialStructureChunk(level.getSeed(), playerRegionX + offsetX, playerRegionZ + offsetZ);
            if (isValidTowerBiome(towerChunk, biomeResolver)) {
               int blockX = towerChunk.getMiddleBlockX();
               int blockZ = towerChunk.getMiddleBlockZ();
               double dx = blockX - player.getX();
               double dz = blockZ - player.getZ();
               double distanceSquared = dx * dx + dz * dz;
               if (!(distanceSquared > 900.0) && (nearest == null || distanceSquared < nearest.distanceSquared())) {
                  nearest = new RunicCompassService.Target(towerChunk, blockX, blockZ, distanceSquared);
               }
            }
         }
      }

      return nearest;
   }

   private static RunicCompassService.Target findNearest(ServerPlayer player, RunicCompassData data) {
      ServerLevel level = player.level();
      RandomSpreadStructurePlacement spread = getTowerPlacement(level);
      BiomeResolver biomeResolver = level.getChunkSource().getGenerator().getBiomeSource().createUncachedResolver(level.getChunkSource().randomState());
      int spacing = spread.spacing();
      long regionWidth = spacing * 16L;
      long maxOffset = (spacing - spread.separation() - 1) * 16L + 8L;
      ChunkPos playerChunk = player.chunkPosition();
      int playerRegionX = Math.floorDiv(playerChunk.x(), spacing);
      int playerRegionZ = Math.floorDiv(playerChunk.z(), spacing);
      RunicCompassService.Target nearest = null;
      int radius = 0;

      while (true) {
         for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
               if (radius <= 0 || Math.abs(offsetX) == radius || Math.abs(offsetZ) == radius) {
                  int regionX = playerRegionX + offsetX;
                  int regionZ = playerRegionZ + offsetZ;
                  ChunkPos towerChunk = spread.getPotentialStructureChunk(level.getSeed(), regionX, regionZ);
                  if (!data.isCompleted(player.getUUID(), towerChunk) && isValidTowerBiome(towerChunk, biomeResolver)) {
                     int blockX = towerChunk.getMiddleBlockX();
                     int blockZ = towerChunk.getMiddleBlockZ();
                     double dx = blockX - player.getX();
                     double dz = blockZ - player.getZ();
                     double distanceSquared = dx * dx + dz * dz;
                     if (nearest == null || distanceSquared < nearest.distanceSquared()) {
                        nearest = new RunicCompassService.Target(towerChunk, blockX, blockZ, distanceSquared);
                     }
                  }
               }
            }
         }

         if (nearest != null) {
            double nearestUnsearched = nearestUnsearchedDistance(player.getX(), player.getZ(), playerRegionX, playerRegionZ, radius, regionWidth, maxOffset);
            if (nearest.distanceSquared() <= nearestUnsearched * nearestUnsearched) {
               return nearest;
            }
         }

         radius++;
      }
   }

   private static boolean isValidTowerBiome(ChunkPos towerChunk, BiomeResolver biomeResolver) {
      return biomeResolver.getNoiseBiome(
            QuartPos.fromBlock(towerChunk.getMiddleBlockX()), QuartPos.fromBlock(0), QuartPos.fromBlock(towerChunk.getMiddleBlockZ())
         )
         .is(RUNIC_TOWER_BIOMES);
   }

   private static RandomSpreadStructurePlacement getTowerPlacement(ServerLevel level) {
      StructureSet towerSet = (StructureSet)level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(RUNIC_TOWER_SET).value();
      if (towerSet.placement() instanceof RandomSpreadStructurePlacement spread) {
         if (spread.spacing() > spread.separation() && spread.separation() >= 0) {
            return spread;
         } else {
            throw new IllegalStateException("The runic tower structure set has invalid spacing or separation.");
         }
      } else {
         throw new IllegalStateException("The runic tower structure set must use random_spread placement.");
      }
   }

   private static double nearestUnsearchedDistance(double playerX, double playerZ, int regionX, int regionZ, int radius, long regionWidth, long maxOffset) {
      long leftRegionX = (long)regionX - radius - 1L;
      long rightRegionX = (long)regionX + radius + 1L;
      long nearRegionZ = (long)regionZ - radius - 1L;
      long farRegionZ = (long)regionZ + radius + 1L;
      double leftDistance = distanceToInterval(playerX, leftRegionX * regionWidth + 8L, leftRegionX * regionWidth + maxOffset);
      double rightDistance = distanceToInterval(playerX, rightRegionX * regionWidth + 8L, rightRegionX * regionWidth + maxOffset);
      double nearDistance = distanceToInterval(playerZ, nearRegionZ * regionWidth + 8L, nearRegionZ * regionWidth + maxOffset);
      double farDistance = distanceToInterval(playerZ, farRegionZ * regionWidth + 8L, farRegionZ * regionWidth + maxOffset);
      return Math.min(Math.min(leftDistance, rightDistance), Math.min(nearDistance, farDistance));
   }

   private static double distanceToInterval(double position, long minimum, long maximum) {
      if (position < minimum) {
         return minimum - position;
      } else {
         return position > maximum ? position - maximum : 0.0;
      }
   }

   private static int reset(ServerPlayer player) {
      int resetCount = RunicCompassData.get(player.level().getServer()).reset(player.getUUID());
      FLASHES.remove(player.getUUID());
      player.sendSystemMessage(Component.translatable("commands.runevestiges.compass.reset", new Object[]{player.getDisplayName()}));
      if (hasCompass(player)) {
         sendPlayerSnapshot(player);
      }

      return resetCount;
   }

   private record Flash(RunicCompassService.Target target, long expiresAt) {
   }

   private record Target(ChunkPos chunk, int blockX, int blockZ, double distanceSquared) {
   }
}
