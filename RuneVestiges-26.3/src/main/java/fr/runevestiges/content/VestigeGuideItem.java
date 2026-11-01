package fr.runevestiges.content;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public final class VestigeGuideItem extends WrittenBookItem {
   public VestigeGuideItem(Properties properties) {
      super(properties);
   }

   public InteractionResult use(Level level, Player player, InteractionHand hand) {
      return InteractionResult.PASS;
   }
}
