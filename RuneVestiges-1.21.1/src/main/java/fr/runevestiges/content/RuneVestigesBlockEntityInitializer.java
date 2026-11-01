package fr.runevestiges.content;

import net.fabricmc.api.ModInitializer;

public final class RuneVestigesBlockEntityInitializer implements ModInitializer {
	@Override
	public void onInitialize() {
		ModBlockEntities.register();
	}
}
