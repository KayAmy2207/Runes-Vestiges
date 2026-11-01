package fr.runevestiges.mixin.client;

import fr.runevestiges.client.RunicCuratorCapeLayer;
import fr.runevestiges.client.RunicCuratorModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.WanderingTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WanderingTraderRenderer.class)
abstract class WanderingTraderRendererMixin {
	private static final ResourceLocation CURATOR_TEXTURE =
		ResourceLocation.fromNamespaceAndPath("runevestiges", "textures/entity/curator.png");

	@Inject(method = "<init>", at = @At("TAIL"))
	private void runevestiges$useRunicCuratorModel(
		EntityRendererProvider.Context context,
		CallbackInfo callback
	) {
		WanderingTraderRenderer renderer = (WanderingTraderRenderer) (Object) this;
		VillagerModel<WanderingTrader> vanillaModel = renderer.getModel();
		RunicCuratorModel model = new RunicCuratorModel(
			vanillaModel,
			context.bakeLayer(RunicCuratorModel.MODEL_LAYER)
		);
		((LivingEntityRendererAccessor) renderer).runevestiges$setModel(model);
		((LivingEntityRendererAccessor) renderer).runevestiges$addLayer(
			new RunicCuratorCapeLayer(renderer, context.bakeLayer(RunicCuratorCapeLayer.MODEL_LAYER))
		);
	}

	@Inject(method = "getTextureLocation", at = @At("RETURN"), cancellable = true)
	private void runevestiges$useCuratorSkin(
		WanderingTrader trader,
		CallbackInfoReturnable<ResourceLocation> callback
	) {
		if (RunicCuratorModel.isCurator(trader)) {
			callback.setReturnValue(CURATOR_TEXTURE);
		}
	}
}
