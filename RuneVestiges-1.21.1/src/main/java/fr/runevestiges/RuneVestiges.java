package fr.runevestiges;

import fr.runevestiges.content.ModContent;
import fr.runevestiges.content.ModEnchantmentEffects;
import fr.runevestiges.content.ModGuide;
import fr.runevestiges.content.ModLoot;
import fr.runevestiges.content.ModTrades;
import fr.runevestiges.content.RunicCompassService;
import fr.runevestiges.content.LiftOffEnchantment;
import fr.runevestiges.economy.EconomyNetworking;
import net.fabricmc.api.ModInitializer;

public final class RuneVestiges implements ModInitializer {
	public static final String MOD_ID = "runevestiges";

	@Override
	public void onInitialize() {
		ModContent.register();
		ModLoot.register();
		ModTrades.register();
		ModEnchantmentEffects.register();
		LiftOffEnchantment.register();
		ModGuide.register();
		RunicCompassService.register();
		EconomyNetworking.register();
	}
}
