package fr.runevestiges.content;

import net.minecraft.world.entity.npc.WanderingTrader;

public final class RunicCurator {
	private static final String NAME = "Curateur des Vestiges";

	private RunicCurator() {
	}

	public static boolean isCurator(WanderingTrader trader) {
		return trader.getCustomName() != null && trader.getCustomName().getString().equals(NAME);
	}
}
