package fr.runevestiges.mixin.client;

import fr.runevestiges.client.RunicCuratorRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({VillagerRenderState.class})
abstract class VillagerRenderStateMixin implements RunicCuratorRenderState {
   @Unique
   private boolean runevestiges$curator;

   @Override
   public void runevestiges$setCurator(boolean curator) {
      this.runevestiges$curator = curator;
   }

   @Override
   public boolean runevestiges$isCurator() {
      return this.runevestiges$curator;
   }
}
