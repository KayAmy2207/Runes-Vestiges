package fr.runevestiges.content;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public final class RunicCompassData extends SavedData {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String DATA_NAME = "runevestiges_compass";
	private static final Factory<RunicCompassData> FACTORY = new Factory<>(
		RunicCompassData::new,
		RunicCompassData::load,
		DataFixTypes.LEVEL
	);

	private final Map<UUID, Set<Long>> completedTowers = new HashMap<>();

	private RunicCompassData() {
	}

	public static RunicCompassData get(net.minecraft.server.MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
	}

	public boolean isCompleted(UUID playerId, ChunkPos towerChunk) {
		return completedTowers.getOrDefault(playerId, Set.of()).contains(key(towerChunk));
	}

	public boolean toggleCompleted(UUID playerId, ChunkPos towerChunk) {
		Set<Long> towers = completedTowers.computeIfAbsent(playerId, ignored -> new HashSet<>());
		boolean completed;
		if (towers.remove(key(towerChunk))) {
			completed = false;
			if (towers.isEmpty()) {
				completedTowers.remove(playerId);
			}
		} else {
			towers.add(key(towerChunk));
			completed = true;
		}
		setDirty();
		return completed;
	}

	public int reset(UUID playerId) {
		Set<Long> completed = completedTowers.remove(playerId);
		if (completed == null) {
			return 0;
		}
		setDirty();
		return completed.size();
	}

	private static long key(ChunkPos chunk) {
		return ((long) chunk.x << 32) | (chunk.z & 0xffffffffL);
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag players = new CompoundTag();
		completedTowers.forEach((playerId, towers) -> {
			CompoundTag savedTowers = new CompoundTag();
			for (long tower : towers) {
				savedTowers.putBoolean(Long.toString(tower), true);
			}
			players.put(playerId.toString(), savedTowers);
		});
		tag.put("players", players);
		return tag;
	}

	private static RunicCompassData load(CompoundTag tag, HolderLookup.Provider registries) {
		RunicCompassData data = new RunicCompassData();
		CompoundTag players = tag.getCompound("players");
		for (String rawPlayerId : players.getAllKeys()) {
			UUID playerId;
			try {
				playerId = UUID.fromString(rawPlayerId);
			} catch (IllegalArgumentException exception) {
				LOGGER.warn("Ignoring invalid player UUID in runic compass data: {}", rawPlayerId, exception);
				continue;
			}
			Set<Long> towers = new HashSet<>();
			CompoundTag savedTowers = players.getCompound(rawPlayerId);
			for (String rawTower : savedTowers.getAllKeys()) {
				try {
					towers.add(Long.parseLong(rawTower));
				} catch (NumberFormatException exception) {
					LOGGER.warn("Ignoring invalid runic tower position in compass data: {}", rawTower, exception);
				}
			}
			data.completedTowers.put(playerId, towers);
		}
		return data;
	}
}
