package fr.runevestiges.content;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;

public final class RunicCompassData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "runevestiges_compass";
   private static final Codec<RunicCompassData> CODEC = CompoundTag.CODEC.xmap(RunicCompassData::load, RunicCompassData::save);
   private static final SavedDataType<RunicCompassData> TYPE = new SavedDataType(
      Identifier.fromNamespaceAndPath("runevestiges", "runevestiges_compass"), RunicCompassData::new, CODEC, DataFixTypes.LEVEL
   );
   private final Map<UUID, Set<Long>> completedTowers = new HashMap<>();

   private RunicCompassData() {
   }

   public static RunicCompassData get(MinecraftServer server) {
      return (RunicCompassData)server.overworld().getDataStorage().computeIfAbsent(TYPE);
   }

   public boolean isCompleted(UUID playerId, ChunkPos towerChunk) {
      return this.completedTowers.getOrDefault(playerId, Set.of()).contains(key(towerChunk));
   }

   public boolean toggleCompleted(UUID playerId, ChunkPos towerChunk) {
      Set<Long> towers = this.completedTowers.computeIfAbsent(playerId, ignored -> new HashSet<>());
      boolean completed;
      if (towers.remove(key(towerChunk))) {
         completed = false;
         if (towers.isEmpty()) {
            this.completedTowers.remove(playerId);
         }
      } else {
         towers.add(key(towerChunk));
         completed = true;
      }

      this.setDirty();
      return completed;
   }

   public int reset(UUID playerId) {
      Set<Long> completed = this.completedTowers.remove(playerId);
      if (completed == null) {
         return 0;
      } else {
         this.setDirty();
         return completed.size();
      }
   }

   private static long key(ChunkPos chunk) {
      return (long)chunk.x() << 32 | chunk.z() & 4294967295L;
   }

   private CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      CompoundTag players = new CompoundTag();
      this.completedTowers.forEach((playerId, towers) -> {
         CompoundTag savedTowers = new CompoundTag();

         for (long tower : towers) {
            savedTowers.putBoolean(Long.toString(tower), true);
         }

         players.put(playerId.toString(), savedTowers);
      });
      tag.put("players", players);
      return tag;
   }

   private static RunicCompassData load(CompoundTag tag) {
      RunicCompassData data = new RunicCompassData();
      CompoundTag players = tag.getCompoundOrEmpty("players");

      for (String rawPlayerId : players.keySet()) {
         UUID playerId;
         try {
            playerId = UUID.fromString(rawPlayerId);
         } catch (IllegalArgumentException var12) {
            LOGGER.warn("Ignoring invalid player UUID in runic compass data: {}", rawPlayerId, var12);
            continue;
         }

         Set<Long> towers = new HashSet<>();
         CompoundTag savedTowers = players.getCompoundOrEmpty(rawPlayerId);

         for (String rawTower : savedTowers.keySet()) {
            try {
               towers.add(Long.parseLong(rawTower));
            } catch (NumberFormatException var11) {
               LOGGER.warn("Ignoring invalid runic tower position in compass data: {}", rawTower, var11);
            }
         }

         data.completedTowers.put(playerId, towers);
      }

      return data;
   }
}
