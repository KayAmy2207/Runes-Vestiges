package fr.runevestiges.content;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public final class RunicCompassItem extends Item {
   public RunicCompassItem(Properties properties) {
      super(properties);
   }

   public InteractionResult use(Level level, Player player, InteractionHand hand) {
      return RunicCompassService.use(level, player, hand);
   }
}
