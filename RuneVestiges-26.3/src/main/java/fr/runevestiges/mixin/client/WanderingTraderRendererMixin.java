package fr.runevestiges.mixin.client;

import fr.runevestiges.client.RunicCuratorRenderState;
import fr.runevestiges.content.RunicCurator;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({WanderingTraderRenderer.class})
abstract class WanderingTraderRendererMixin {
   private static final Identifier CURATOR_TEXTURE = Identifier.fromNamespaceAndPath("runevestiges", "textures/entity/curator.png");

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void runevestiges$markCurator(WanderingTrader trader, VillagerRenderState state, float partialTick, CallbackInfo callback) {
      boolean isCurator = RunicCurator.isCurator(trader);
      ((RunicCuratorRenderState)state).runevestiges$setCurator(isCurator);
      if (isCurator) {
         state.outlineColor = 0;
      }
   }

   @Inject(
      method = {"getTextureLocation"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void runevestiges$useCuratorSkin(VillagerRenderState state, CallbackInfoReturnable<Identifier> callback) {
      if (((RunicCuratorRenderState)state).runevestiges$isCurator()) {
         callback.setReturnValue(CURATOR_TEXTURE);
      }
   }
}
