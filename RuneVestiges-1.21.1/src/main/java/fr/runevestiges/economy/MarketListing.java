package fr.runevestiges.economy;

import java.util.UUID;
import net.minecraft.world.item.ItemStack;

public record MarketListing(
	long id,
	UUID sellerId,
	String sellerName,
	long price,
	long createdAt,
	ItemStack item
) {
	public MarketListing {
		item = item.copy();
	}

	@Override
	public ItemStack item() {
		return item.copy();
	}
}
