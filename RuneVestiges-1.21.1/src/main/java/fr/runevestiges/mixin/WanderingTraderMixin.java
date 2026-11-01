package fr.runevestiges.mixin;

import fr.runevestiges.content.ModTrades;
import fr.runevestiges.content.RunicCurator;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractVillager.class)
abstract class WanderingTraderMixin {
	@Inject(method = "getOffers", at = @At("RETURN"), cancellable = true)
	private void runevestiges$curatorSellsOnlyRunicBooks(CallbackInfoReturnable<MerchantOffers> callback) {
		AbstractVillager villager = (AbstractVillager) (Object) this;
		if (villager instanceof WanderingTrader trader
			&& !trader.level().isClientSide
			&& RunicCurator.isCurator(trader)) {
			MerchantOffers filteredOffers = ModTrades.ensureCuratorBookOffers(trader, callback.getReturnValue());
			callback.setReturnValue(filteredOffers);
		}
	}
}
