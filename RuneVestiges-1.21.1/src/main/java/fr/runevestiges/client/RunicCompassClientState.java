package fr.runevestiges.client;

import fr.runevestiges.content.ModContent;
import fr.runevestiges.content.RunicCompassPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class RunicCompassClientState {
	private static int targetX;
	private static int targetZ;
	private static int state;
	private static boolean hasTarget;

	private RunicCompassClientState() {
	}

	public static void registerModelProperties() {
		FabricModelPredicateProviderRegistry.register(
			ModContent.RUNIC_COMPASS,
			ResourceLocation.withDefaultNamespace("angle"),
			(stack, level, entity, seed) -> angle()
		);
		FabricModelPredicateProviderRegistry.register(
			ModContent.RUNIC_COMPASS,
			ResourceLocation.fromNamespaceAndPath("runevestiges", "compass_state"),
			(stack, level, entity, seed) -> state
		);
	}

	public static void registerNetworking() {
		ClientPlayNetworking.registerGlobalReceiver(
			RunicCompassPayloads.Update.TYPE,
			(payload, context) -> context.client().execute(() -> {
				targetX = payload.targetX();
				targetZ = payload.targetZ();
				state = Math.clamp(payload.state(), 0, 2);
				hasTarget = payload.hasTarget();
			})
		);
	}

	private static float angle() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !hasTarget) {
			return (float) ((System.currentTimeMillis() / 2_000.0) % 1.0);
		}
		double targetAngle = Math.toDegrees(Math.atan2(
			targetZ - client.player.getZ(),
			targetX - client.player.getX()
		));
		double relativeAngle = (targetAngle - client.player.getYRot() - 90.0) % 360.0;
		if (relativeAngle < 0.0) {
			relativeAngle += 360.0;
		}
		return (float) (relativeAngle / 360.0);
	}
}
