package fr.runevestiges.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mob.class)
interface MobAccessor {
	@Invoker("setItemSlot")
	void runevestiges$setItemSlot(EquipmentSlot slot, ItemStack stack);
}
