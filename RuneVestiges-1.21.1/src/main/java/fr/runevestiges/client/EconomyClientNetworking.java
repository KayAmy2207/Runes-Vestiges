package fr.runevestiges.client;

import fr.runevestiges.economy.EconomyPayloads;
import fr.runevestiges.economy.EconomyService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class EconomyClientNetworking {
	private static final KeyMapping OPEN_ECONOMY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
		"key.runevestiges.open_economy",
		GLFW.GLFW_KEY_I,
		"key.categories.runevestiges"
	));

	private EconomyClientNetworking() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(
			EconomyPayloads.Snapshot.TYPE,
			(payload, context) -> context.client().execute(() -> EconomyScreen.receive(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
			EconomyPayloads.BalanceUpdate.TYPE,
			(payload, context) -> context.client().execute(() -> EconomyScreen.updateBalance(payload.balance()))
		);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (OPEN_ECONOMY.consumeClick() && client.player != null && client.screen == null) {
				EconomyScreen.open(EconomyService.HOME_TAB);
			}
		});
	}

	public static boolean send(EconomyPayloads.Action action) {
		if (!ClientPlayNetworking.canSend(EconomyPayloads.Action.TYPE)) {
			return false;
		}
		ClientPlayNetworking.send(action);
		return true;
	}
}
