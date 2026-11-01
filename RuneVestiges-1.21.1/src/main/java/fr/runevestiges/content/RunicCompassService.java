package fr.runevestiges.content;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;

public final class RunicCompassService {
	private static final int UPDATE_INTERVAL_TICKS = 20;
	private static final int DETECT_RADIUS = 30;
	private static final int DONE_FLASH_TICKS = 30;
	private static final int USE_COOLDOWN_TICKS = 10;
	private static final ResourceKey<StructureSet> RUNIC_TOWER_SET = ResourceKey.create(
		Registries.STRUCTURE_SET,
		ResourceLocation.fromNamespaceAndPath("runevestiges", "runic_towers")
	);
	private static final Map<UUID, Flash> FLASHES = new HashMap<>();
	private static final Map<UUID, Long> LAST_USE = new HashMap<>();
	private static final Set<UUID> TRACKED_PLAYERS = new HashSet<>();

	private RunicCompassService() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(RunicCompassPayloads.Update.TYPE, RunicCompassPayloads.Update.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			sendSnapshot(handler.player, null, 0)
		);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID playerId = handler.player.getUUID();
			FLASHES.remove(playerId);
			LAST_USE.remove(playerId);
			TRACKED_PLAYERS.remove(playerId);
		});
		ServerTickEvents.END_SERVER_TICK.register(RunicCompassService::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("runevestiges")
				.then(Commands.literal("compass")
					.then(Commands.literal("reset")
						.requires(source -> source.hasPermission(2))
						.executes(context -> reset(context.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
							.executes(context -> reset(EntityArgument.getPlayer(context, "player")))))))
		);
	}

	public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}

		ServerLevel serverLevel = serverPlayer.serverLevel();
		long gameTime = serverLevel.getGameTime();
		long previousUse = LAST_USE.getOrDefault(serverPlayer.getUUID(), Long.MIN_VALUE);
		if (previousUse != Long.MIN_VALUE && gameTime - previousUse < USE_COOLDOWN_TICKS) {
			return InteractionResultHolder.sidedSuccess(stack, false);
		}
		LAST_USE.put(serverPlayer.getUUID(), gameTime);

		if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
			serverPlayer.displayClientMessage(Component.translatable("message.runevestiges.compass.not_overworld"), true);
			return InteractionResultHolder.sidedSuccess(stack, false);
		}

		RunicCompassData data = RunicCompassData.get(serverPlayer.server);
		Target target = findNearestTowerInRadius(serverPlayer);
		if (target == null) {
			serverPlayer.displayClientMessage(Component.translatable("message.runevestiges.compass.too_far"), true);
			sendPlayerSnapshot(serverPlayer);
			return InteractionResultHolder.sidedSuccess(stack, false);
		}

		boolean completed = data.toggleCompleted(serverPlayer.getUUID(), target.chunk());
		if (completed) {
			FLASHES.put(serverPlayer.getUUID(), new Flash(target, gameTime + DONE_FLASH_TICKS));
			serverPlayer.displayClientMessage(Component.translatable("message.runevestiges.compass.completed"), true);
			sendSnapshot(serverPlayer, target, 2);
		} else {
			FLASHES.remove(serverPlayer.getUUID());
			serverPlayer.displayClientMessage(Component.translatable("message.runevestiges.compass.reactivated"), true);
			sendPlayerSnapshot(serverPlayer);
		}
		serverLevel.playSound(
			null,
			serverPlayer.blockPosition(),
			SoundEvents.EXPERIENCE_ORB_PICKUP,
			SoundSource.PLAYERS,
			0.8F,
			completed ? 1.35F : 0.9F
		);
		return InteractionResultHolder.sidedSuccess(stack, false);
	}

	private static void tick(net.minecraft.server.MinecraftServer server) {
		long gameTime = server.overworld().getGameTime();
		boolean refresh = gameTime % UPDATE_INTERVAL_TICKS == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID playerId = player.getUUID();
			Flash flash = FLASHES.get(playerId);
			if (flash != null) {
				if (gameTime >= flash.expiresAt()) {
					FLASHES.remove(playerId);
					sendPlayerSnapshot(player);
				}
				continue;
			}
			if (!refresh) {
				continue;
			}
			boolean hasCompass = hasCompass(player);
			if (hasCompass || TRACKED_PLAYERS.contains(playerId)) {
				sendPlayerSnapshot(player);
			}
		}
	}

	private static void sendPlayerSnapshot(ServerPlayer player) {
		if (!hasCompass(player)) {
			TRACKED_PLAYERS.remove(player.getUUID());
			sendSnapshot(player, null, 0);
			return;
		}
		TRACKED_PLAYERS.add(player.getUUID());
		if (!player.serverLevel().dimension().equals(Level.OVERWORLD)) {
			sendSnapshot(player, null, 0);
			return;
		}
		Target target = findNearest(player, RunicCompassData.get(player.server));
		if (target == null) {
			sendSnapshot(player, null, 0);
			return;
		}
		int state = target.distanceSquared() <= DETECT_RADIUS * DETECT_RADIUS ? 1 : 0;
		sendSnapshot(player, target, state);
	}

	private static void sendSnapshot(ServerPlayer player, Target target, int state) {
		int targetX = target == null ? 0 : target.blockX();
		int targetZ = target == null ? 0 : target.blockZ();
		ServerPlayNetworking.send(player, new RunicCompassPayloads.Update(
			targetX,
			targetZ,
			state,
			target != null
		));
	}

	private static boolean hasCompass(ServerPlayer player) {
		return player.getInventory().items.stream().anyMatch(stack -> stack.is(ModContent.RUNIC_COMPASS))
			|| player.getOffhandItem().is(ModContent.RUNIC_COMPASS);
	}

	private static Target findNearestTowerInRadius(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		RandomSpreadStructurePlacement spread = getTowerPlacement(level);
		int regionRadius = (int) Math.ceil(DETECT_RADIUS / (spread.spacing() * 16.0)) + 1;
		ChunkPos playerChunk = player.chunkPosition();
		int playerRegionX = Math.floorDiv(playerChunk.x, spread.spacing());
		int playerRegionZ = Math.floorDiv(playerChunk.z, spread.spacing());
		Target nearest = null;
		for (int offsetX = -regionRadius; offsetX <= regionRadius; offsetX++) {
			for (int offsetZ = -regionRadius; offsetZ <= regionRadius; offsetZ++) {
				ChunkPos towerChunk = spread.getPotentialStructureChunk(
					level.getSeed(),
					playerRegionX + offsetX,
					playerRegionZ + offsetZ
				);
				int blockX = towerChunk.getMiddleBlockX();
				int blockZ = towerChunk.getMiddleBlockZ();
				double dx = blockX - player.getX();
				double dz = blockZ - player.getZ();
				double distanceSquared = dx * dx + dz * dz;
				if (distanceSquared > DETECT_RADIUS * DETECT_RADIUS) {
					continue;
				}
				if (nearest == null || distanceSquared < nearest.distanceSquared()) {
					nearest = new Target(towerChunk, blockX, blockZ, distanceSquared);
				}
			}
		}
		return nearest;
	}

	private static Target findNearest(ServerPlayer player, RunicCompassData data) {
		ServerLevel level = player.serverLevel();
		RandomSpreadStructurePlacement spread = getTowerPlacement(level);

		int spacing = spread.spacing();
		long regionWidth = (long) spacing * 16L;
		long maxOffset = (long) (spacing - spread.separation() - 1) * 16L + 8L;
		ChunkPos playerChunk = player.chunkPosition();
		int playerRegionX = Math.floorDiv(playerChunk.x, spacing);
		int playerRegionZ = Math.floorDiv(playerChunk.z, spacing);
		Target nearest = null;

		for (int radius = 0; ; radius++) {
			for (int offsetX = -radius; offsetX <= radius; offsetX++) {
				for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
					if (radius > 0 && Math.abs(offsetX) != radius && Math.abs(offsetZ) != radius) {
						continue;
					}
					int regionX = playerRegionX + offsetX;
					int regionZ = playerRegionZ + offsetZ;
					ChunkPos towerChunk = spread.getPotentialStructureChunk(level.getSeed(), regionX, regionZ);
					if (data.isCompleted(player.getUUID(), towerChunk)) {
						continue;
					}
					int blockX = towerChunk.getMiddleBlockX();
					int blockZ = towerChunk.getMiddleBlockZ();
					double dx = blockX - player.getX();
					double dz = blockZ - player.getZ();
					double distanceSquared = dx * dx + dz * dz;
					if (nearest == null || distanceSquared < nearest.distanceSquared()) {
						nearest = new Target(towerChunk, blockX, blockZ, distanceSquared);
					}
				}
			}

			if (nearest != null) {
				double nearestUnsearched = nearestUnsearchedDistance(
					player.getX(),
					player.getZ(),
					playerRegionX,
					playerRegionZ,
					radius,
					regionWidth,
					maxOffset
				);
				if (nearest.distanceSquared() <= nearestUnsearched * nearestUnsearched) {
					return nearest;
				}
			}
		}
	}

	private static RandomSpreadStructurePlacement getTowerPlacement(ServerLevel level) {
		StructureSet towerSet = level.registryAccess()
			.lookupOrThrow(Registries.STRUCTURE_SET)
			.getOrThrow(RUNIC_TOWER_SET)
			.value();
		StructurePlacement placement = towerSet.placement();
		if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
			throw new IllegalStateException("The runic tower structure set must use random_spread placement.");
		}
		if (spread.spacing() <= spread.separation() || spread.separation() < 0) {
			throw new IllegalStateException("The runic tower structure set has invalid spacing or separation.");
		}
		return spread;
	}

	private static double nearestUnsearchedDistance(
			double playerX,
			double playerZ,
			int regionX,
			int regionZ,
			int radius,
			long regionWidth,
			long maxOffset
	) {
		long leftRegionX = (long) regionX - radius - 1L;
		long rightRegionX = (long) regionX + radius + 1L;
		long nearRegionZ = (long) regionZ - radius - 1L;
		long farRegionZ = (long) regionZ + radius + 1L;
		double leftDistance = distanceToInterval(
			playerX,
			leftRegionX * regionWidth + 8L,
			leftRegionX * regionWidth + maxOffset
		);
		double rightDistance = distanceToInterval(
			playerX,
			rightRegionX * regionWidth + 8L,
			rightRegionX * regionWidth + maxOffset
		);
		double nearDistance = distanceToInterval(
			playerZ,
			nearRegionZ * regionWidth + 8L,
			nearRegionZ * regionWidth + maxOffset
		);
		double farDistance = distanceToInterval(
			playerZ,
			farRegionZ * regionWidth + 8L,
			farRegionZ * regionWidth + maxOffset
		);
		return Math.min(Math.min(leftDistance, rightDistance), Math.min(nearDistance, farDistance));
	}

	private static double distanceToInterval(double position, long minimum, long maximum) {
		if (position < minimum) {
			return minimum - position;
		}
		if (position > maximum) {
			return position - maximum;
		}
		return 0.0;
	}

	private static int reset(ServerPlayer player) {
		int resetCount = RunicCompassData.get(player.server).reset(player.getUUID());
		FLASHES.remove(player.getUUID());
		player.displayClientMessage(Component.translatable(
			"commands.runevestiges.compass.reset",
			player.getDisplayName()
		), false);
		if (hasCompass(player)) {
			sendPlayerSnapshot(player);
		}
		return resetCount;
	}

	private record Target(ChunkPos chunk, int blockX, int blockZ, double distanceSquared) {
	}

	private record Flash(Target target, long expiresAt) {
	}
}
