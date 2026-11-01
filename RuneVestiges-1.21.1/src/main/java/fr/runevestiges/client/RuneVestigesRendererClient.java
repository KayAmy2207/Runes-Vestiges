package fr.runevestiges.client;

import fr.runevestiges.content.ModBlockEntities;
import fr.runevestiges.content.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

public final class RuneVestigesRendererClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(
			RunicCuratorModel.MODEL_LAYER,
			RunicCuratorModel::createLayer
		);
		EntityModelLayerRegistry.registerModelLayer(
			RunicCuratorCapeLayer.MODEL_LAYER,
			RunicCuratorCapeLayer::createLayer
		);
		BlockEntityRendererRegistry.register(ModBlockEntities.RUNE_ALTAR, RuneAltarRenderer::new);
		MenuScreens.register(ModMenus.RUNE_ALTAR, RuneAltarScreen::new);
	}
}
