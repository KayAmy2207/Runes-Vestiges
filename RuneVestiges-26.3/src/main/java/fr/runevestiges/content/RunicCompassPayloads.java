package fr.runevestiges.content;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public final class RunicCompassPayloads {
   private RunicCompassPayloads() {
   }

   public record Update(int targetX, int targetZ, int state, boolean hasTarget) implements CustomPacketPayload {
      public static final Type<RunicCompassPayloads.Update> TYPE = new Type(Identifier.fromNamespaceAndPath("runevestiges", "runic_compass_update"));
      public static final StreamCodec<RegistryFriendlyByteBuf, RunicCompassPayloads.Update> CODEC = StreamCodec.of((buffer, payload) -> {
         buffer.writeVarInt(payload.targetX());
         buffer.writeVarInt(payload.targetZ());
         buffer.writeVarInt(payload.state());
         buffer.writeBoolean(payload.hasTarget());
      }, buffer -> new RunicCompassPayloads.Update(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
