package fr.runevestiges.client;

import fr.runevestiges.content.LiftOffEnchantment;
import fr.runevestiges.content.ModContent;
import fr.runevestiges.content.ModEnchantmentEffects;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public final class RuneVestigesClient implements ClientModInitializer {
   private static final ResourceKey<Enchantment> LIFT_OFF = ResourceKey.create(
      Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("runevestiges", "lift_off")
   );
   private static final ResourceKey<Enchantment> DOUBLE_JUMP = ResourceKey.create(
      Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("runevestiges", "springheel")
   );
   private static final ResourceKey<Enchantment> STICKY_PANTS = ResourceKey.create(
      Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("runevestiges", "sticky_pants")
   );
   private static final long DOUBLE_JUMP_WINDOW_MILLIS = 600L;
   private static final long LIFT_OFF_DOUBLE_TAP_MILLIS = 450L;
   private static long lastJumpPress;
   private static long lastLiftOffPress;
   private static boolean jumpWasDown;
   private static boolean liftOffPressArmed;

   public void onInitializeClient() {
      EconomyClientNetworking.register();
      RunicCompassClientState.registerNetworking();
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> {
         updateStickyClimbInput(client);
         handleJumpInput(client);
      });
      UseItemCallback.EVENT.register((UseItemCallback)(player, level, hand) -> {
         ItemStack stack = player.getItemInHand(hand);
         if (level.isClientSide() && stack.is(ModContent.VESTIGE_GUIDE)) {
            WrittenBookContent content = (WrittenBookContent)stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content == null) {
               return InteractionResult.FAIL;
            } else {
               List<Component> pages = content.pages().stream().map(page -> (Component)page.get(false)).toList();
               Minecraft.getInstance().setScreenAndShow(new VestigeGuideScreen(pages));
               return InteractionResult.SUCCESS;
            }
         } else {
            return InteractionResult.PASS;
         }
      });
   }

   private static void handleJumpInput(Minecraft client) {
      if (client.player != null && client.level != null && client.canInterruptScreen()) {
         boolean jumpDown = client.options.keyJump.isDown();
         if (jumpDown && !jumpWasDown) {
            jumpWasDown = true;
            long now = System.currentTimeMillis();
            boolean doubleJump = now - lastJumpPress <= 600L;
            lastJumpPress = now;
            if (!client.player.isFallFlying()) {
               ItemStack elytra = client.player.getItemBySlot(EquipmentSlot.CHEST);
               Registry<Enchantment> enchantments = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
               boolean hasLiftOff = elytra.is(Items.ELYTRA) && EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(LIFT_OFF), elytra) > 0;
               if (!hasLiftOff) {
                  liftOffPressArmed = false;
                  lastLiftOffPress = 0L;
               } else if (!liftOffPressArmed) {
                  liftOffPressArmed = client.player.onGround();
                  lastLiftOffPress = now;
               } else if (now - lastLiftOffPress > 450L) {
                  liftOffPressArmed = client.player.onGround();
                  lastLiftOffPress = now;
               } else if (ClientPlayNetworking.canSend(LiftOffEnchantment.LiftOffRequest.TYPE)) {
                  ClientPlayNetworking.send(new LiftOffEnchantment.LiftOffRequest());
                  liftOffPressArmed = false;
                  lastLiftOffPress = 0L;
                  return;
               }

               if (doubleJump
                  && !client.player.onGround()
                  && EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(DOUBLE_JUMP), client.player.getItemBySlot(EquipmentSlot.FEET)) > 0
                  && ClientPlayNetworking.canSend(ModEnchantmentEffects.DoubleJumpRequest.TYPE)) {
                  ClientPlayNetworking.send(new ModEnchantmentEffects.DoubleJumpRequest());
               }
            }
         } else {
            jumpWasDown = jumpDown;
         }
      } else {
         lastJumpPress = 0L;
         jumpWasDown = false;
         lastLiftOffPress = 0L;
         liftOffPressArmed = false;
      }
   }

   private static void updateStickyClimbInput(Minecraft client) {
      if (ClientPlayNetworking.canSend(ModEnchantmentEffects.StickyClimbRequest.TYPE) && client.player != null && client.level != null) {
         boolean jumpHeldAtWall = client.canInterruptScreen()
            && client.options.keyJump.isDown()
            && !client.player.isPassenger()
            && ModEnchantmentEffects.hasClimbableSurface(client.player)
            && EnchantmentHelper.getItemEnchantmentLevel(
                  client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(STICKY_PANTS), client.player.getItemBySlot(EquipmentSlot.LEGS)
               )
               > 0;
         if (jumpHeldAtWall) {
            Vec3 velocity = client.player.getDeltaMovement();
            client.player.setDeltaMovement(velocity.x, 0.24, velocity.z);
            client.player.fallDistance = 0.0;
         }

         ClientPlayNetworking.send(new ModEnchantmentEffects.StickyClimbRequest(jumpHeldAtWall));
      }
   }
}
