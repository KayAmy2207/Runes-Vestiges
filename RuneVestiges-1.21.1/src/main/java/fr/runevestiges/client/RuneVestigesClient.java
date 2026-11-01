package fr.runevestiges.client;

import fr.runevestiges.content.ModEnchantmentEffects;
import fr.runevestiges.content.ModContent;
import fr.runevestiges.content.LiftOffEnchantment;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class RuneVestigesClient implements ClientModInitializer {
	private static final ResourceKey<Enchantment> LIFT_OFF = ResourceKey.create(
		Registries.ENCHANTMENT,
		ResourceLocation.fromNamespaceAndPath("runevestiges", "lift_off")
	);
	private static final ResourceKey<Enchantment> DOUBLE_JUMP = ResourceKey.create(
		Registries.ENCHANTMENT,
		ResourceLocation.fromNamespaceAndPath("runevestiges", "springheel")
	);
	private static final ResourceKey<Enchantment> STICKY_PANTS = ResourceKey.create(
		Registries.ENCHANTMENT,
		ResourceLocation.fromNamespaceAndPath("runevestiges", "sticky_pants")
	);
	private static final long DOUBLE_JUMP_WINDOW_MILLIS = 600;
	private static final long LIFT_OFF_DOUBLE_TAP_MILLIS = 450;
	private static long lastJumpPress;
	private static long lastLiftOffPress;
	private static boolean jumpWasDown;
	private static boolean liftOffPressArmed;

	@Override
	public void onInitializeClient() {
		EconomyClientNetworking.register();
		FabricModelPredicateProviderRegistry.register(
			Items.ENCHANTED_BOOK,
			ResourceLocation.fromNamespaceAndPath("runevestiges", "runic_book"),
			(stack, level, entity, seed) -> ModContent.runicBookModelPredicateValue(stack)
		);
		RunicCompassClientState.registerModelProperties();
		RunicCompassClientState.registerNetworking();
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			updateStickyClimbInput(client);
			handleJumpInput(client);
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			var stack = player.getItemInHand(hand);
			if (!level.isClientSide || !stack.is(ModContent.VESTIGE_GUIDE)) {
				return InteractionResultHolder.pass(stack);
			}

			var content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (content == null) {
				return InteractionResultHolder.fail(stack);
			}

			Minecraft.getInstance().setScreen(new VestigeGuideScreen(content.getPages(true)));
			return InteractionResultHolder.success(stack);
		});
	}

	private static void handleJumpInput(Minecraft client) {
		if (client.player == null || client.level == null || client.screen != null) {
			lastJumpPress = 0;
			jumpWasDown = false;
			lastLiftOffPress = 0;
			liftOffPressArmed = false;
			return;
		}

		boolean jumpDown = client.options.keyJump.isDown();
		if (!jumpDown || jumpWasDown) {
			jumpWasDown = jumpDown;
			return;
		}
		jumpWasDown = true;

		long now = System.currentTimeMillis();
		boolean doubleJump = now - lastJumpPress <= DOUBLE_JUMP_WINDOW_MILLIS;
		lastJumpPress = now;
		if (client.player.isFallFlying()) {
			return;
		}

		var elytra = client.player.getItemBySlot(EquipmentSlot.CHEST);
		var enchantments = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		boolean hasLiftOff = elytra.is(Items.ELYTRA)
			&& EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(LIFT_OFF), elytra) > 0;
		if (!hasLiftOff) {
			liftOffPressArmed = false;
			lastLiftOffPress = 0;
		} else if (!liftOffPressArmed) {
			liftOffPressArmed = client.player.onGround();
			lastLiftOffPress = now;
		} else if (now - lastLiftOffPress > LIFT_OFF_DOUBLE_TAP_MILLIS) {
			liftOffPressArmed = client.player.onGround();
			lastLiftOffPress = now;
		} else if (ClientPlayNetworking.canSend(LiftOffEnchantment.LiftOffRequest.TYPE)) {
			ClientPlayNetworking.send(new LiftOffEnchantment.LiftOffRequest());
			liftOffPressArmed = false;
			lastLiftOffPress = 0;
			return;
		}

		if (doubleJump && !client.player.onGround()
			&& EnchantmentHelper.getItemEnchantmentLevel(
				enchantments.getOrThrow(DOUBLE_JUMP),
				client.player.getItemBySlot(EquipmentSlot.FEET)
			) > 0
			&& ClientPlayNetworking.canSend(ModEnchantmentEffects.DoubleJumpRequest.TYPE)) {
			ClientPlayNetworking.send(new ModEnchantmentEffects.DoubleJumpRequest());
		}
	}

	private static void updateStickyClimbInput(Minecraft client) {
		if (!ClientPlayNetworking.canSend(ModEnchantmentEffects.StickyClimbRequest.TYPE)
			|| client.player == null || client.level == null) {
			return;
		}

		boolean jumpHeldAtWall = client.screen == null
			&& client.options.keyJump.isDown()
			&& !client.player.isPassenger()
			&& ModEnchantmentEffects.hasClimbableSurface(client.player)
			&& EnchantmentHelper.getItemEnchantmentLevel(
				client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(STICKY_PANTS),
				client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS)
			) > 0;
		if (jumpHeldAtWall) {
			var velocity = client.player.getDeltaMovement();
			client.player.setDeltaMovement(velocity.x, 0.24, velocity.z);
			client.player.fallDistance = 0;
		}
		ClientPlayNetworking.send(new ModEnchantmentEffects.StickyClimbRequest(jumpHeldAtWall));
	}
}
