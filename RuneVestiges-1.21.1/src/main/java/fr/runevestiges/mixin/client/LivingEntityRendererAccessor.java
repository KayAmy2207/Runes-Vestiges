package fr.runevestiges.mixin.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
	@Accessor("model")
	void runevestiges$setModel(EntityModel<?> model);

	@Invoker("addLayer")
	boolean runevestiges$addLayer(RenderLayer<?, ?> layer);
}
