package fr.runevestiges.mixin;

import fr.runevestiges.content.RunicCurator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Inject(method = "startUsingItem(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
	private void runevestiges$preventCuratorFromDrinkingPotions(InteractionHand hand, CallbackInfo callback) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (entity instanceof WanderingTrader trader
			&& RunicCurator.isCurator(trader)
			&& trader.getItemInHand(hand).is(Items.POTION)) {
			callback.cancel();
		}
	}

	@Inject(
		method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private void runevestiges$preventCuratorInvisibility(
			MobEffectInstance effect,
			net.minecraft.world.entity.Entity source,
			CallbackInfoReturnable<Boolean> callback
	) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (entity instanceof WanderingTrader trader
			&& RunicCurator.isCurator(trader)
			&& effect.is(MobEffects.INVISIBILITY)) {
			callback.setReturnValue(false);
		}
	}
}
