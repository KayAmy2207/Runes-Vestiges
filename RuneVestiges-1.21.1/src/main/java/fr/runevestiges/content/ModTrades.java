package fr.runevestiges.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.MerchantOffer;

public final class ModTrades {
	private static final int CURATOR_OFFER_COUNT = 6;
	private static final Set<String> CURATOR_ENCHANTMENTS = Set.of(ModContent.enchantmentIds());

	private ModTrades() {
	}

	public static void register() {
		for (String enchantmentId : ModContent.enchantmentIds()) {
			registerBookTrade(2, enchantmentId, 1, 14, 1, 8, 10);
			if (ModContent.maxEnchantmentLevel(enchantmentId) >= 2) {
				registerBookTrade(3, enchantmentId, 2, 28, 2, 5, 20);
			}
			if (ModContent.maxEnchantmentLevel(enchantmentId) >= 3) {
				registerBookTrade(5, enchantmentId, 3, 42, 3, 3, 30);
			}
		}
	}

	public static MerchantOffers ensureCuratorBookOffers(WanderingTrader trader, MerchantOffers currentOffers) {
		if (hasValidCuratorBookOffers(currentOffers)) {
			return currentOffers;
		}

		List<String> enchantments = new ArrayList<>(List.of(ModContent.enchantmentIds()));
		shuffle(enchantments, trader.getRandom());
		List<Integer> levels = new ArrayList<>(List.of(1, 1, 2, 2, 3, 3));
		shuffle(levels, trader.getRandom());

		MerchantOffers bookOffers = new MerchantOffers();
		for (int i = 0; i < CURATOR_OFFER_COUNT; i++) {
			int level = Math.min(
				levels.get(i),
				ModContent.maxEnchantmentLevel(enchantments.get(i))
			);
			bookOffers.add(createEnchantedBookOffer(
				trader,
				enchantments.get(i),
				level,
				curatorEmeraldCost(level),
				level,
				3,
				10
			));
		}
		trader.overrideOffers(bookOffers);
		return bookOffers;
	}

	private static boolean hasValidCuratorBookOffers(MerchantOffers offers) {
		if (offers.size() != CURATOR_OFFER_COUNT) {
			return false;
		}

		Set<String> offeredEnchantments = new HashSet<>();
		for (MerchantOffer offer : offers) {
			ItemStack result = offer.getResult();
			if (!result.is(Items.ENCHANTED_BOOK)) {
				return false;
			}

			var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(result);
			if (enchantments.size() != 1) {
				return false;
			}

			var enchantment = enchantments.entrySet().iterator().next();
			int level = enchantment.getValue();
			var key = enchantment.getKey().unwrapKey();
			if (key.isEmpty()) {
				return false;
			}

			ResourceLocation location = key.orElseThrow().location();
			if (!location.getNamespace().equals("runevestiges")
				|| !CURATOR_ENCHANTMENTS.contains(location.getPath())
				|| !offeredEnchantments.add(location.getPath())) {
				return false;
			}
			if (level < 1 || level > ModContent.maxEnchantmentLevel(location.getPath())) {
				return false;
			}
		}

		return offeredEnchantments.size() == CURATOR_OFFER_COUNT;
	}

	private static int curatorEmeraldCost(int level) {
		return switch (level) {
			case 1 -> 24;
			case 2 -> 28;
			case 3 -> 42;
			default -> throw new IllegalArgumentException("Unsupported curator book level: " + level);
		};
	}

	private static <T> void shuffle(List<T> values, RandomSource random) {
		for (int i = values.size() - 1; i > 0; i--) {
			int swapIndex = random.nextInt(i + 1);
			T value = values.get(i);
			values.set(i, values.get(swapIndex));
			values.set(swapIndex, value);
		}
	}

	private static void registerBookTrade(
		int villagerLevel,
		String enchantmentId,
		int enchantmentLevel,
		int emeraldCost,
		int shardCost,
		int maxUses,
		int villagerExperience
	) {
		TradeOfferHelper.registerVillagerOffers(
			VillagerProfession.LIBRARIAN,
			villagerLevel,
			factories -> factories.add((entity, random) ->
				createEnchantedBookOffer(
					entity,
					enchantmentId,
					enchantmentLevel,
					emeraldCost,
					shardCost,
					maxUses,
					villagerExperience
				))
		);
	}

	private static MerchantOffer createEnchantedBookOffer(
		Entity entity,
		String enchantmentId,
		int enchantmentLevel,
		int emeraldCost,
		int shardCost,
		int maxUses,
		int villagerExperience
	) {
		ResourceLocation location = ResourceLocation.fromNamespaceAndPath("runevestiges", enchantmentId);
		ResourceKey<Enchantment> enchantmentKey = ResourceKey.create(Registries.ENCHANTMENT, location);
		Holder<Enchantment> enchantment = entity.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(enchantmentKey);

		var book = ModContent.createEnchantedBook(enchantment, enchantmentLevel);

		return new MerchantOffer(
			new ItemCost(Items.EMERALD, emeraldCost),
			Optional.of(new ItemCost(ModContent.RUNIC_SHARD, shardCost)),
			book,
			0,
			maxUses,
			villagerExperience,
			0.05F
		);
	}
}
