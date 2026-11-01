package fr.runevestiges.mixin;

import fr.runevestiges.content.RunicCurator;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UseItemGoal.class)
abstract class UseItemGoalMixin<T extends Mob> {
	@Shadow
	@Final
	private T mob;

	@Shadow
	@Final
	private ItemStack item;

	@Inject(method = "canUse()Z", at = @At("HEAD"), cancellable = true)
	private void runevestiges$keepCuratorPotionFree(CallbackInfoReturnable<Boolean> callback) {
		if (mob instanceof WanderingTrader trader
			&& RunicCurator.isCurator(trader)
			&& item.is(Items.POTION)) {
			if (trader.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.POTION)) {
				((MobAccessor) trader).runevestiges$setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
			callback.setReturnValue(false);
		}
	}
}
