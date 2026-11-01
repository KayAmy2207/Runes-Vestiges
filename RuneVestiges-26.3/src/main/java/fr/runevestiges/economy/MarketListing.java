package fr.runevestiges.economy;

import java.util.UUID;
import net.minecraft.world.item.ItemStack;

public record MarketListing(long id, UUID sellerId, String sellerName, long price, long createdAt, ItemStack item) {
   public MarketListing(long id, UUID sellerId, String sellerName, long price, long createdAt, ItemStack item) {
      item = item.copy();
      this.id = id;
      this.sellerId = sellerId;
      this.sellerName = sellerName;
      this.price = price;
      this.createdAt = createdAt;
      this.item = item;
   }

   public ItemStack item() {
      return this.item.copy();
   }
}
