package fr.runevestiges.client;

import fr.runevestiges.content.ModBlockEntities;
import fr.runevestiges.content.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;

public final class RuneVestigesRendererClient implements ClientModInitializer {
   public void onInitializeClient() {
      ModelLayerRegistry.registerModelLayer(RunicCuratorModel.MODEL_LAYER, RunicCuratorModel::createLayer);
      ModelLayerRegistry.registerModelLayer(RunicCuratorRenderLayer.capeModelLayer(), RunicCuratorRenderLayer::createCapeLayer);
      LivingEntityRenderLayerRegistrationCallback.EVENT
         .register(
            (LivingEntityRenderLayerRegistrationCallback)(entityType, renderer, helper, context) -> {
               if (renderer instanceof WanderingTraderRenderer) {
                  helper.register(
                     new RunicCuratorRenderLayer(context.bakeLayer(RunicCuratorModel.MODEL_LAYER), context.bakeLayer(RunicCuratorRenderLayer.capeModelLayer()))
                  );
               }
            }
         );
      BlockEntityRendererRegistry.register(ModBlockEntities.RUNE_ALTAR, RuneAltarRenderer::new);
      MenuScreens.register(ModMenus.RUNE_ALTAR, RuneAltarScreen::new);
   }
}
