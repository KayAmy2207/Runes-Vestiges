package fr.runevestiges.client;

import fr.runevestiges.content.ModContent;
import fr.runevestiges.content.RunicCompassPayloads;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

public final class RunicCompassClientState {
   private static final CustomModelData[] MODEL_DATA = createModelData();
   private static int targetX;
   private static int targetZ;
   private static int state;
   private static boolean hasTarget;

   private RunicCompassClientState() {
   }

   public static void registerNetworking() {
      ClientPlayNetworking.registerGlobalReceiver(RunicCompassPayloads.Update.TYPE, (payload, context) -> context.client().execute(() -> {
         targetX = payload.targetX();
         targetZ = payload.targetZ();
         state = Math.clamp((long)payload.state(), 0, 2);
         hasTarget = payload.hasTarget();
      }));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null) {
            int frame = Math.floorMod((int)(angle() * 32.0F), 32);
            int modelIndex = Math.clamp((long)state, 0, 2) * 32 + frame;
            CustomModelData modelData = MODEL_DATA[modelIndex];

            for (ItemStack stack : client.player.getInventory().getNonEquipmentItems()) {
               updateModelData(stack, modelData);
            }

            updateModelData(client.player.getOffhandItem(), modelData);
         }
      });
   }

   private static CustomModelData[] createModelData() {
      CustomModelData[] modelData = new CustomModelData[96];

      for (int index = 0; index < modelData.length; index++) {
         modelData[index] = new CustomModelData(List.of((float)index), List.of(), List.of(), List.of());
      }

      return modelData;
   }

   private static void updateModelData(ItemStack stack, CustomModelData modelData) {
      if (stack.is(ModContent.RUNIC_COMPASS) && !modelData.equals(stack.get(DataComponents.CUSTOM_MODEL_DATA))) {
         stack.set(DataComponents.CUSTOM_MODEL_DATA, modelData);
      }
   }

   private static float angle() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && hasTarget) {
         double targetAngle = Math.toDegrees(Math.atan2(targetZ - client.player.getZ(), targetX - client.player.getX()));
         double relativeAngle = (targetAngle - client.player.getYRot() - 90.0) % 360.0;
         if (relativeAngle < 0.0) {
            relativeAngle += 360.0;
         }

         return (float)(relativeAngle / 360.0);
      } else {
         return (float)(System.currentTimeMillis() / 2000.0 % 1.0);
      }
   }
}
