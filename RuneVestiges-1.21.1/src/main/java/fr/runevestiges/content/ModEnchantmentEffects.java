package fr.runevestiges.content;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;

public final class ModEnchantmentEffects {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation VITALITY_MODIFIER =
		ResourceLocation.fromNamespaceAndPath("runevestiges", "vitality");
	private static final Map<UUID, Long> LAST_DOUBLE_JUMP = new HashMap<>();
	private static final Set<UUID> ACTIVE_STICKY_CLIMBS = new HashSet<>();
	private static final Set<UUID> USED_DOUBLE_JUMPS = new HashSet<>();
	private static final Map<UUID, Long> LAST_LETHAL_EDGE_TARGET_HIT = new HashMap<>();
	private static final Map<UUID, Long> LAST_SPIKED_HARNESS_HIT = new HashMap<>();
	private static final Map<UUID, HookshotTarget> ACTIVE_HOOKSHOTS = new HashMap<>();
	private static final Map<UUID, VortexEffect> ACTIVE_VORTICES = new HashMap<>();
	private static final Map<UUID, CarriedMob> CARRIED_MOBS = new HashMap<>();
	private static final Set<UUID> CASCADE_BREAKS = new HashSet<>();
	private static final Map<UUID, ArrayDeque<PendingSmelt>> PENDING_SMELTS = new HashMap<>();
	private static final Map<UUID, List<ItemStack>> SALVATION_INVENTORIES = new HashMap<>();

	private ModEnchantmentEffects() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(DoubleJumpRequest.TYPE, DoubleJumpRequest.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DoubleJumpRequest.TYPE, (payload, context) ->
			context.server().execute(() -> doubleJump(context.player())));
		PayloadTypeRegistry.playC2S().register(StickyClimbRequest.TYPE, StickyClimbRequest.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(StickyClimbRequest.TYPE, (payload, context) ->
			context.server().execute(() -> {
				if (payload.jumpHeld()) {
					ACTIVE_STICKY_CLIMBS.add(context.player().getUUID());
				} else {
					ACTIVE_STICKY_CLIMBS.remove(context.player().getUUID());
				}
			}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID playerId = handler.player.getUUID();
			releaseCarriedMob(handler.player);
			LAST_DOUBLE_JUMP.remove(playerId);
			ACTIVE_STICKY_CLIMBS.remove(playerId);
			USED_DOUBLE_JUMPS.remove(playerId);
			ACTIVE_HOOKSHOTS.remove(playerId);
			PENDING_SMELTS.remove(playerId);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ModEnchantmentEffects::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(ModEnchantmentEffects::afterDeath);
		ServerLivingEntityEvents.ALLOW_DEATH.register(ModEnchantmentEffects::saveInventoryOnDeath);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!restoreSalvationInventory(newPlayer)) {
				LOGGER.warn("Rune of Salvation could not place the saved inventory near {}'s respawn point yet.",
					newPlayer.getGameProfile().getName());
			}
		});
		PlayerBlockBreakEvents.AFTER.register(ModEnchantmentEffects::afterBlockBreak);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof Player player
				&& source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
				&& hasObsidianArmor(player)) {
				return false;
			}
			if (!(entity instanceof Player player) || !source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
				|| player.fallDistance < 3.0F) {
				return true;
			}

			int slimeJump = level(player, player.getItemBySlot(EquipmentSlot.FEET), "soft_landing");
			if (slimeJump == 0) {
				return true;
			}

			Vec3 velocity = player.getDeltaMovement();
			double bounce = Math.min(1.5, 0.65 + (player.fallDistance - 3.0) * 0.04)
				* (0.9 + 0.05 * slimeJump);
			player.setDeltaMovement(velocity.x, bounce, velocity.z);
			player.resetFallDistance();
			player.hurtMarked = true;
			return false;
		});
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (world.getGameTime() % 1200 == 0) {
				LAST_LETHAL_EDGE_TARGET_HIT.values().removeIf(time -> world.getGameTime() - time > 1200);
				LAST_SPIKED_HARNESS_HIT.values().removeIf(time -> world.getGameTime() - time > 1200);
			}
			for (ServerPlayer player : world.players()) {
				tickStickyClimb(player);
				tickPlayer(world, player);
				tickObsidianArmor(world, player);
				restoreSalvationInventory(player);
			}
			tickVortices(world);
			tickSpikedHarness(world);
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (player.isShiftKeyDown() && stack.isEmpty()
				&& level(player, player.getItemBySlot(EquipmentSlot.CHEST), "carry") > 0
				&& carriedMob(player) != null) {
				if (!world.isClientSide) {
					releaseCarriedMob(player);
				}
				return InteractionResultHolder.success(stack);
			}
			return InteractionResultHolder.pass(stack);
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!player.isShiftKeyDown()
				|| level(player, player.getItemBySlot(EquipmentSlot.CHEST), "carry") == 0
				|| carriedMob(player) == null) {
				return InteractionResult.PASS;
			}
			if (!world.isClientSide) {
				BlockPos dropPos = hitResult.getBlockPos().relative(hitResult.getDirection());
				releaseCarriedMob(player, Vec3.atBottomCenterOf(dropPos));
			}
			return InteractionResult.SUCCESS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
			int carryLevel = level(player, player.getItemBySlot(EquipmentSlot.CHEST), "carry");
			if (!player.isShiftKeyDown() || carryLevel == 0 || !(target instanceof Mob mob)) {
				return InteractionResult.PASS;
			}
			if (mob.getMaxHealth() > carryLevel * 10.0F) {
				if (!world.isClientSide) {
					player.displayClientMessage(
						net.minecraft.network.chat.Component.translatable("message.runevestiges.carry_too_strong"),
						true
					);
				}
				return InteractionResult.SUCCESS;
			}
			Mob carried = carriedMob(player);
			if (!world.isClientSide) {
				if (carried == null && mob.isAlive() && !mob.isPassenger()) {
					carryMob(player, mob);
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static Mob carriedMob(Entity carrier) {
		CarriedMob carried = CARRIED_MOBS.get(carrier.getUUID());
		if (carried == null) {
			return null;
		}
		if (carried.mob().isAlive()) {
			return carried.mob();
		}
		CARRIED_MOBS.remove(carrier.getUUID());
		return null;
	}

	private static void carryMob(Entity carrier, Mob mob) {
		CARRIED_MOBS.put(carrier.getUUID(), new CarriedMob(
			mob,
			carrier.level().dimension(),
			mob.isNoAi(),
			mob.isNoGravity()
		));
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.setDeltaMovement(Vec3.ZERO);
	}

	private static void releaseCarriedMob(Entity carrier) {
		releaseCarriedMob(carrier, null);
	}

	private static void releaseCarriedMob(Entity carrier, Vec3 dropPosition) {
		Mob carried = carriedMob(carrier);
		CarriedMob carryData = CARRIED_MOBS.remove(carrier.getUUID());
		if (carried != null) {
			carried.setDeltaMovement(Vec3.ZERO);
			carried.setNoAi(carryData != null && carryData.wasNoAi());
			carried.setNoGravity(carryData != null && carryData.hadNoGravity());
			if (dropPosition != null) {
				carried.setPos(dropPosition.x, dropPosition.y, dropPosition.z);
			}
		}
	}

	private static void doubleJump(ServerPlayer player) {
		if (!player.isAlive() || player.onGround() || player.isPassenger()) {
			return;
		}

		int springheel = level(player, player.getItemBySlot(EquipmentSlot.FEET), "springheel");
		if (springheel == 0) {
			return;
		}

		long now = player.level().getGameTime();
		Long previous = LAST_DOUBLE_JUMP.get(player.getUUID());
		if (USED_DOUBLE_JUMPS.contains(player.getUUID()) || (previous != null && now - previous < 10)) {
			return;
		}
		LAST_DOUBLE_JUMP.put(player.getUUID(), now);
		USED_DOUBLE_JUMPS.add(player.getUUID());

		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(velocity.x, 0.55 + springheel * 0.2, velocity.z);
		player.resetFallDistance();
		player.hurtMarked = true;
	}

	private static void tickStickyClimb(ServerPlayer player) {
		if (!ACTIVE_STICKY_CLIMBS.contains(player.getUUID())) {
			return;
		}
		if (!player.isAlive() || player.isPassenger() || !hasClimbableSurface(player)) {
			ACTIVE_STICKY_CLIMBS.remove(player.getUUID());
			return;
		}
		int stickyPants = level(player, player.getItemBySlot(EquipmentSlot.LEGS), "sticky_pants");
		if (stickyPants == 0) {
			ACTIVE_STICKY_CLIMBS.remove(player.getUUID());
			return;
		}
		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(velocity.x, 0.2 + stickyPants * 0.02, velocity.z);
		player.fallDistance = 0;
		player.hurtMarked = true;
	}

	public static boolean hasClimbableSurface(Entity entity) {
		AABB body = entity.getBoundingBox().inflate(0.08, 0.0, 0.08);
		int minX = net.minecraft.core.BlockPos.containing(body.minX, body.minY, body.minZ).getX();
		int minY = net.minecraft.core.BlockPos.containing(body.minX, body.minY, body.minZ).getY();
		int minZ = net.minecraft.core.BlockPos.containing(body.minX, body.minY, body.minZ).getZ();
		int maxX = net.minecraft.core.BlockPos.containing(body.maxX, body.maxY, body.maxZ).getX();
		int maxY = net.minecraft.core.BlockPos.containing(body.maxX, body.maxY, body.maxZ).getY();
		int maxZ = net.minecraft.core.BlockPos.containing(body.maxX, body.maxY, body.maxZ).getZ();
		CollisionContext context = CollisionContext.of(entity);
		for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
			BlockState state = entity.level().getBlockState(pos);
			if (state.getCollisionShape(entity.level(), pos, context)
				.move(pos.getX(), pos.getY(), pos.getZ())
				.toAabbs().stream().anyMatch(box -> box.intersects(body))) {
				return true;
			}
		}
		return false;
	}

	public static void onArrowHit(Projectile projectile, Entity hitEntity) {
		if (!(projectile instanceof AbstractArrow arrow)
			|| !(projectile.getOwner() instanceof Player player)
			|| !(hitEntity instanceof LivingEntity target)
			|| player.level().isClientSide) {
			return;
		}

		ItemStack weapon = player.getMainHandItem();
		if (!weapon.is(Items.BOW) && !weapon.is(Items.CROSSBOW)) {
			weapon = player.getOffhandItem();
		}
		int longshot = level(player, weapon, "longshot");
		if (longshot > 0 && weapon.is(Items.BOW)) {
			float distanceBonus = (float) Math.min(24.0, player.distanceTo(target) * longshot * 0.1);
			if (distanceBonus > 0.0F) {
				target.hurt(player.damageSources().magic(), distanceBonus);
			}
		}
		int hookshot = level(player, weapon, "hookshot");
		if (hookshot > 0 && weapon.is(Items.CROSSBOW)
			&& player.level() instanceof ServerLevel world) {
			pullPlayerWithHookshot(world, player, target.getBoundingBox().getCenter(), hookshot);
		}
	}

	public static void onTridentHit(Projectile projectile, Entity hitEntity) {
		if (!(projectile instanceof ThrownTrident trident)
			|| !(projectile.getOwner() instanceof Player player)
			|| !(hitEntity instanceof LivingEntity target)
			|| !(player.level() instanceof ServerLevel world)) {
			return;
		}

		int vortex = level(player, trident.getPickupItemStackOrigin(), "vortex");
		if (vortex == 0) {
			vortex = level(player, player.getMainHandItem(), "vortex");
		}
		if (vortex == 0) {
			vortex = level(player, player.getOffhandItem(), "vortex");
		}
		if (vortex == 0) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				vortex = level(player, player.getInventory().getItem(slot), "vortex");
				if (vortex > 0) {
					break;
				}
			}
		}
		if (vortex == 0) {
			return;
		}

		long startedAt = world.getGameTime();
		VortexEffect effect = new VortexEffect(
			target.getBoundingBox().getCenter(),
			vortex,
			world.dimension(),
			startedAt,
			startedAt + 100
		);
		ACTIVE_VORTICES.put(UUID.randomUUID(), effect);
		spawnVortexParticles(world, effect, startedAt);
		pullAndDamageMobs(world, effect, false);
	}

	private static void tickVortices(ServerLevel world) {
		long time = world.getGameTime();
		ACTIVE_VORTICES.entrySet().removeIf(entry -> {
			VortexEffect effect = entry.getValue();
			if (!effect.dimension().equals(world.dimension())) {
				return false;
			}
			if (time >= effect.expiresAt()) {
				return true;
			}
			if (time % 2 == 0) {
				spawnVortexParticles(world, effect, time);
			}
			long elapsed = time - effect.startedAt();
			if (elapsed % 5 == 0) {
				pullAndDamageMobs(world, effect, elapsed % 20 == 0);
			}
			return false;
		});
	}

	private static void spawnVortexParticles(ServerLevel world, VortexEffect effect, long time) {
		double height = 5.0 + effect.level() * 2.0;
		for (int index = 0; index < 32; index++) {
			double progress = index / 32.0;
			double angle = time * 0.38 + index * Math.PI / 8.0;
			double radius = 0.8 + progress * (2.0 + effect.level() * 0.35);
			double y = effect.center().y() + progress * height;
			world.sendParticles(
				ParticleTypes.CLOUD,
				effect.center().x() + Math.cos(angle) * radius,
				y,
				effect.center().z() + Math.sin(angle) * radius,
				2, 0.12, 0.15, 0.12, 0.03
			);
			world.sendParticles(
				ParticleTypes.CAMPFIRE_COSY_SMOKE,
				effect.center().x() + Math.cos(angle) * radius,
				y,
				effect.center().z() + Math.sin(angle) * radius,
				1, 0.04, 0.08, 0.04, 0.01
			);
		}
	}

	private static void pullAndDamageMobs(ServerLevel world, VortexEffect effect, boolean damage) {
		double radius = 10.0 + effect.level() * 5.0;
		AABB bounds = new AABB(
			effect.center().x() - radius, effect.center().y() - 3.0, effect.center().z() - radius,
			effect.center().x() + radius, effect.center().y() + 12.0, effect.center().z() + radius
		);
		for (Monster monster : world.getEntitiesOfClass(
			Monster.class,
			bounds,
			Monster::isAlive
		)) {
			Vec3 toCenter = effect.center().subtract(monster.getBoundingBox().getCenter());
			Vec3 horizontal = new Vec3(toCenter.x(), 0.0, toCenter.z());
			if (horizontal.lengthSqr() > radius * radius) {
				continue;
			}
			Vec3 pull;
			if (horizontal.lengthSqr() < 0.01) {
				double angle = world.getGameTime() * 0.35;
				pull = new Vec3(Math.cos(angle), 0.0, Math.sin(angle))
					.scale(0.55 + effect.level() * 0.18);
			} else {
				pull = horizontal.normalize().scale(0.55 + effect.level() * 0.18);
			}
			monster.setDeltaMovement(monster.getDeltaMovement().add(pull.x(), 0.24, pull.z()));
			if (damage) {
				monster.hurt(world.damageSources().magic(), effect.level());
			}
			monster.hurtMarked = true;
		}
	}

	public static void onProjectileHitBlock(Projectile projectile, BlockPos hitPos) {
		if (!(projectile instanceof AbstractArrow)
			|| !(projectile.getOwner() instanceof Player player)
			|| !(player.level() instanceof ServerLevel world)) {
			return;
		}
		ItemStack weapon = player.getMainHandItem();
		if (!weapon.is(Items.CROSSBOW)) {
			weapon = player.getOffhandItem();
		}
		int hookshot = weapon.is(Items.CROSSBOW) ? level(player, weapon, "hookshot") : 0;
		if (hookshot > 0) {
			pullPlayerWithHookshot(world, player, Vec3.atCenterOf(hitPos), hookshot);
			projectile.discard();
		}
	}

	private static void pullPlayerWithHookshot(ServerLevel world, Player player, Vec3 target, int hookshot) {
		if (target.distanceToSqr(player.position()) < 1.0) {
			return;
		}
		ACTIVE_HOOKSHOTS.put(player.getUUID(), new HookshotTarget(
			target,
			world.dimension(),
			world.getGameTime() + 100,
			1.1 + hookshot * 0.1
		));
		player.fallDistance = 0;
		player.hurtMarked = true;
	}

	private static void tickHookshot(ServerLevel world, ServerPlayer player) {
		HookshotTarget hookshot = ACTIVE_HOOKSHOTS.get(player.getUUID());
		if (hookshot == null) {
			return;
		}
		if (!hookshot.dimension().equals(world.dimension()) || world.getGameTime() > hookshot.expiresAt()) {
			ACTIVE_HOOKSHOTS.remove(player.getUUID());
			return;
		}

		Vec3 pull = hookshot.position().subtract(player.position());
		if (pull.lengthSqr() < 1.44) {
			ACTIVE_HOOKSHOTS.remove(player.getUUID());
			player.setDeltaMovement(Vec3.ZERO);
			player.hurtMarked = true;
			return;
		}

		double speed = Math.min(hookshot.speed(), Math.max(0.45, pull.length() * 0.25));
		player.setDeltaMovement(pull.normalize().scale(speed));
		player.fallDistance = 0;
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 3, 0, true, false));
		player.hurtMarked = true;
	}

	public static void onFishingHookRetrieve(Player player, Entity hookedEntity) {
		if (player.level().isClientSide || !(hookedEntity instanceof LivingEntity target) || !target.isAlive()) {
			return;
		}
		int hooking = Math.max(
			level(player, player.getMainHandItem(), "hooking"),
			level(player, player.getOffhandItem(), "hooking")
		);
		if (hooking == 0) {
			return;
		}
		target.hurt(player.damageSources().playerAttack(player), 4.0F + hooking * 5.0F);
		Vec3 pull = player.position().subtract(target.position()).normalize();
		target.setDeltaMovement(target.getDeltaMovement().add(pull.scale(0.8 + hooking * 0.25)));
		target.hurtMarked = true;
	}

	public static void onExperienceOrbPickup(ExperienceOrb orb, Player player) {
		if (player.level().isClientSide || orb.getTags().contains("runevestiges_experience_bonus_applied")) {
			return;
		}

		int quickLearner = Math.max(
			level(player, player.getMainHandItem(), "quick_learner"),
			level(player, player.getOffhandItem(), "quick_learner")
		);
		int bonus = orb.getValue() * quickLearner;
		if (bonus > 0) {
			orb.addTag("runevestiges_experience_bonus_applied");
			player.giveExperiencePoints(bonus);
		}
	}

	private static void afterDamage(
		LivingEntity target,
		DamageSource source,
		float baseDamage,
		float damageTaken,
		boolean blocked
	) {
		Entity attacker = source.getEntity();
		if (target.level().isClientSide) {
			return;
		}

		if (attacker instanceof Player player && damageTaken > 0.0F
			&& source.is(net.minecraft.tags.DamageTypeTags.IS_PLAYER_ATTACK)) {
			ItemStack weapon = player.getMainHandItem();
			int endSlayer = level(player, weapon, "end_slayer");

			if (endSlayer > 0 && target instanceof EnderMan) {
				int hitsToKill = Math.max(1, 4 - endSlayer);
				float slayerDamage = Math.max(
					0.0F,
					target.getMaxHealth() / hitsToKill - damageTaken
				);
				if (hitsToKill == 1) {
					slayerDamage = Math.max(slayerDamage, target.getHealth());
				}
				if (slayerDamage > 0.0F) {
					target.hurt(player.damageSources().magic(), slayerDamage);
				}
			}

			int colossus = level(player, weapon, "colossus");
			if (colossus > 0) {
				target.hurt(player.damageSources().magic(), 4.0F + colossus * 6.0F);
			}

		}

		if (attacker instanceof LivingEntity mob && !blocked) {
			int spikedHarness = level(target, target.getItemBySlot(EquipmentSlot.BODY), "spiked_harness");
			if (spikedHarness > 0) {
				mob.hurt(target.damageSources().thorns(target), 2.0F + spikedHarness * 2.0F);
			}
		}

		if (attacker instanceof LivingEntity mob && target instanceof Player player && !blocked) {
			int witherThorns = level(player, player.getItemBySlot(EquipmentSlot.CHEST), "wither_thorns");
			if (witherThorns > 0) {
				mob.addEffect(new MobEffectInstance(MobEffects.WITHER, 40 + 20 * witherThorns, witherThorns - 1));
			}
		}

		if (target instanceof Player player && blocked && player.isBlocking()) {
			ItemStack shield = player.getUseItem();
			int eject = level(player, shield, "piston_eject");
			if (attacker instanceof LivingEntity mob) {
				Vec3 direction = mob.position().subtract(player.position()).normalize();
				if (eject > 0) {
					mob.knockback(1.0 + eject * 0.65, -direction.x, -direction.z);
					mob.hurt(player.damageSources().thorns(player), 4.0F + eject * 4.0F);
				}
			}
		}
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof Player player) || player.level().isClientSide) {
			return;
		}

		int lifeReaper = level(player, player.getMainHandItem(), "vampiric");
		if (lifeReaper > 0) {
			player.heal(Math.min(entity.getMaxHealth() * (0.08F * lifeReaper), 8.0F));
		}
	}

	private static boolean saveInventoryOnDeath(LivingEntity entity, DamageSource source, float damage) {
		if (!(entity instanceof ServerPlayer player)
			|| level(player, player.getItemBySlot(EquipmentSlot.CHEST), "rune_of_salvation") == 0) {
			return true;
		}

		List<ItemStack> saved = new ArrayList<>();
		for (ItemStack stack : player.getInventory().items) {
			saveInventoryStack(saved, stack, player);
		}
		for (ItemStack stack : player.getInventory().armor) {
			saveInventoryStack(saved, stack, player);
		}
		for (ItemStack stack : player.getInventory().offhand) {
			saveInventoryStack(saved, stack, player);
		}
		SALVATION_INVENTORIES.put(player.getUUID(), saved);
		player.getInventory().clearContent();
		return true;
	}

	private static void saveInventoryStack(List<ItemStack> saved, ItemStack stack, ServerPlayer player) {
		if (stack.isEmpty()) {
			return;
		}
		ItemStack copy = stack.copy();
		if (level(player, stack, "rune_of_salvation") > 0) {
			Holder<Enchantment> salvation = player.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(ResourceKey.create(
					Registries.ENCHANTMENT,
					ResourceLocation.fromNamespaceAndPath("runevestiges", "rune_of_salvation")
				));
			EnchantmentHelper.updateEnchantments(copy, mutable -> mutable.set(salvation, 0));
		}
		saved.add(copy);
	}

	private static boolean restoreSalvationInventory(ServerPlayer player) {
		List<ItemStack> saved = SALVATION_INVENTORIES.get(player.getUUID());
		if (saved == null || saved.isEmpty()) {
			SALVATION_INVENTORIES.remove(player.getUUID());
			return true;
		}

		ServerLevel world = player.serverLevel();
		BlockPos firstChest = findChestSpot(world, player.blockPosition());
		if (firstChest == null) {
			return false;
		}
		BlockPos secondChest = saved.size() > 27
			? findAdjacentChestSpot(world, firstChest)
			: null;
		if (saved.size() > 27 && secondChest == null) {
			return false;
		}

		var chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
		world.setBlock(firstChest, chestState, 3);
		if (secondChest != null) {
			world.setBlock(secondChest, chestState, 3);
		}

		if (!(world.getBlockEntity(firstChest) instanceof Container first)
			|| (secondChest != null && !(world.getBlockEntity(secondChest) instanceof Container))) {
			LOGGER.error("Rune of Salvation placed an invalid chest for {}.",
				player.getGameProfile().getName());
			return false;
		}
		Container second = secondChest == null ? null : (Container) world.getBlockEntity(secondChest);
		for (int index = 0; index < saved.size(); index++) {
			ItemStack stack = saved.get(index);
			Container destination = index < 27 ? first : second;
			if (destination == null || !insertSavedStack(destination, stack)) {
				LOGGER.error("Rune of Salvation could not store all saved items for {}.",
					player.getGameProfile().getName());
				return false;
			}
		}
		SALVATION_INVENTORIES.remove(player.getUUID());
		player.displayClientMessage(Component.translatable("message.runevestiges.salvation_chest_full"), false);
		return true;
	}

	private static BlockPos findChestSpot(ServerLevel world, BlockPos center) {
		for (int radius = 0; radius <= 5; radius++) {
			for (int yOffset = -1; yOffset <= 1; yOffset++) {
				for (int x = -radius; x <= radius; x++) {
					for (int z = -radius; z <= radius; z++) {
						if (Math.max(Math.abs(x), Math.abs(z)) != radius) {
							continue;
						}
						BlockPos candidate = center.offset(x, yOffset, z);
						if (isChestSpot(world, candidate)) {
							return candidate;
						}
					}
				}
			}
		}
		return null;
	}

	private static BlockPos findAdjacentChestSpot(ServerLevel world, BlockPos first) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidate = first.relative(direction);
			if (isChestSpot(world, candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean isChestSpot(ServerLevel world, BlockPos pos) {
		return world.isEmptyBlock(pos)
			&& world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), Direction.UP);
	}

	private static boolean insertSavedStack(Container container, ItemStack stack) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack existing = container.getItem(slot);
			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)
				&& existing.getCount() < existing.getMaxStackSize()) {
				int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
				existing.grow(moved);
				container.setChanged();
				stack.shrink(moved);
				if (stack.isEmpty()) {
					return true;
				}
			}
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).isEmpty()) {
				container.setItem(slot, stack.copy());
				stack.setCount(0);
				return true;
			}
		}
		return stack.isEmpty();
	}

	private static void tickObsidianArmor(ServerLevel world, ServerPlayer player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (level(player, chest, "obsidian_armor") == 0) {
			return;
		}
		if (player.isOnFire()) {
			player.clearFire();
		}
		if (player.isEyeInFluid(FluidTags.LAVA) && world.getGameTime() % 40 == 0) {
			chest.hurtAndBreak(1, player, EquipmentSlot.CHEST);
		}
	}

	public static boolean hasObsidianArmor(LivingEntity entity) {
		return level(entity, entity.getItemBySlot(EquipmentSlot.CHEST), "obsidian_armor") > 0;
	}

	private static void afterBlockBreak(
		Level world,
		Player player,
		BlockPos pos,
		BlockState state,
		net.minecraft.world.level.block.entity.BlockEntity blockEntity
	) {
		if (world.isClientSide) {
			return;
		}

		ItemStack tool = player.getMainHandItem();
		boolean cascading = CASCADE_BREAKS.contains(player.getUUID());
		if (!cascading) {
			int quarrymaster = level(player, tool, "quarrymaster");
			if (quarrymaster > 0 && state.is(BlockTags.MINEABLE_WITH_PICKAXE) && blockEntity == null) {
				breakMiningPlane(world, player, pos, player.getDirection().getAxis(), quarrymaster);
			}

			int leafFeller = level(player, tool, "leaf_feller");
			if (leafFeller > 0 && state.is(BlockTags.LEAVES)) {
				breakLeaves(world, player, pos, leafFeller);
			}

			int lumberjack = level(player, tool, "lumberlord");
			if (lumberjack > 0 && state.is(BlockTags.LOGS)) {
				breakTree(world, player, pos);
			}
		}

		int wisdom = level(player, tool, "wisdom");
		if (wisdom > 0 && world instanceof ServerLevel serverWorld) {
			ExperienceOrb.award(serverWorld, Vec3.atCenterOf(pos), wisdom);
		}

		int melting = level(player, tool, "melting");
		if (melting > 0 && isOreBlock(state) && world instanceof ServerLevel serverWorld) {
			PENDING_SMELTS.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>())
				.addLast(new PendingSmelt(pos.immutable(), serverWorld.getGameTime() + 4 - melting));
		}

		int oreAlchemy = level(player, tool, "ore_alchemy");
		if (oreAlchemy > 0 && isOreBlock(state) && world instanceof ServerLevel serverWorld
			&& serverWorld.random.nextFloat() < Math.min(0.1F * oreAlchemy, 0.3F)) {
			dropItem(serverWorld, pos, randomOreDrop(serverWorld));
		}

		int archaeologist = level(player, tool, "archaeologist");
		if (archaeologist > 0
			&& (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.SUSPICIOUS_SAND))
			&& world instanceof ServerLevel serverWorld) {
			dropArchaeologyLoot(serverWorld, pos);
			if (serverWorld.random.nextFloat() < 0.1F * archaeologist) {
				dropArchaeologyLoot(serverWorld, pos);
			}
		}

		int luckyGardener = level(player, tool, "lucky_gardener");
		if (luckyGardener > 0 && state.getBlock() instanceof CropBlock crop
			&& state.getValue(CropBlock.AGE) == crop.getMaxAge()
			&& world instanceof ServerLevel serverWorld
			&& serverWorld.random.nextFloat() < Math.min(0.25F * luckyGardener, 0.75F)) {
			ItemStack extraProduce = cropProduce(state);
			if (!extraProduce.isEmpty()) {
				extraProduce.setCount(1 + serverWorld.random.nextInt(luckyGardener));
				dropItem(serverWorld, pos, extraProduce);
			}
			if (state.is(Blocks.WHEAT) || state.is(Blocks.BEETROOTS)) {
				ItemStack extraSeeds = new ItemStack(state.is(Blocks.WHEAT) ? Items.WHEAT_SEEDS : Items.BEETROOT_SEEDS,
					1 + serverWorld.random.nextInt(luckyGardener));
				dropItem(serverWorld, pos, extraSeeds);
			}
		}

		int spawnerSilk = level(player, tool, "spawner_silk");
		if (spawnerSilk > 0 && state.is(Blocks.SPAWNER)
			&& blockEntity != null && world instanceof ServerLevel serverWorld) {
			ItemStack spawner = new ItemStack(Items.SPAWNER);
			CompoundTag spawnerData = blockEntity.saveWithoutMetadata(world.registryAccess());
			sanitizeSpawnerEntityData(spawnerData);
			spawner.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(spawnerData));
			dropItem(serverWorld, pos, spawner);
			if (tool.isDamageableItem()) {
				int durabilityCost = Math.max(
					1,
					(tool.getMaxDamage() * (4 - spawnerSilk) + 9) / 10
				);
				tool.hurtAndBreak(durabilityCost, player, EquipmentSlot.MAINHAND);
			}
		}

	}

	private static void sanitizeSpawnerEntityData(CompoundTag spawnerData) {
		spawnerData.putString("id", "minecraft:mob_spawner");
		CompoundTag spawnData = spawnerData.getCompound("SpawnData");
		CompoundTag entityData = spawnData.getCompound("entity");
		ensureValidSpawnerEntityId(entityData);
		spawnData.put("entity", entityData);
		spawnerData.put("SpawnData", spawnData);

		ListTag spawnPotentials = spawnerData.getList("SpawnPotentials", Tag.TAG_COMPOUND);
		for (int index = 0; index < spawnPotentials.size(); index++) {
			CompoundTag potential = spawnPotentials.getCompound(index);
			CompoundTag potentialData = potential.getCompound("data");
			CompoundTag potentialEntity = potentialData.getCompound("entity");
			ensureValidSpawnerEntityId(potentialEntity);
			potentialData.put("entity", potentialEntity);
			potential.put("data", potentialData);
			spawnPotentials.set(index, potential);
		}
		spawnerData.put("SpawnPotentials", spawnPotentials);
	}

	public static void sanitizeSpawnerStacks(Player player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			sanitizeSpawnerStack(player.getInventory().getItem(slot));
		}
		sanitizeSpawnerStack(player.containerMenu.getCarried());
	}

	private static void sanitizeSpawnerStack(ItemStack stack) {
		if (!stack.is(Items.SPAWNER)) {
			return;
		}
		CustomData blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (blockEntityData == null) {
			return;
		}
		CompoundTag original = blockEntityData.copyTag();
		CompoundTag sanitized = original.copy();
		sanitizeSpawnerEntityData(sanitized);
		if (!sanitized.equals(original)) {
			stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(sanitized));
		}
	}

	private static void ensureValidSpawnerEntityId(CompoundTag entityData) {
		String id = entityData.contains("id", Tag.TAG_STRING) ? entityData.getString("id") : "";
		ResourceLocation entityId = ResourceLocation.tryParse(id);
		if (entityId == null || BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).isEmpty()) {
			LOGGER.warn("Spawner Silk found invalid spawner entity id '{}'; using minecraft:pig as a valid fallback.", id);
			entityData.putString("id", "minecraft:pig");
		}
	}

	private static void breakConnectedVein(Level world, Player player, BlockPos origin, BlockState oreState) {
		Set<BlockPos> visited = new HashSet<>();
		Set<BlockPos> queued = new HashSet<>();
		ArrayDeque<BlockPos> pending = new ArrayDeque<>();
		ItemStack tool = player.getMainHandItem();
		Block oreType = oreType(oreState);
		for (BlockPos neighbour : BlockPos.betweenClosed(
			origin.getX() - 1, origin.getY() - 1, origin.getZ() - 1,
			origin.getX() + 1, origin.getY() + 1, origin.getZ() + 1
		)) {
			BlockPos immutableNeighbour = neighbour.immutable();
			if (!immutableNeighbour.equals(origin)
				&& oreType(world.getBlockState(immutableNeighbour)) == oreType
				&& queued.add(immutableNeighbour)) {
				pending.addLast(immutableNeighbour);
			}
		}

		if (pending.isEmpty()) {
			return;
		}
		boolean startedCascade = CASCADE_BREAKS.add(player.getUUID());
		try {
			while (!pending.isEmpty() && visited.size() < 512) {
				BlockPos current = pending.removeFirst();
				if (!visited.add(current) || oreType(world.getBlockState(current)) != oreType) {
					continue;
				}
				for (BlockPos neighbour : BlockPos.betweenClosed(
					current.getX() - 1, current.getY() - 1, current.getZ() - 1,
					current.getX() + 1, current.getY() + 1, current.getZ() + 1
				)) {
					BlockPos immutableNeighbour = neighbour.immutable();
					if (!visited.contains(immutableNeighbour)
						&& oreType(world.getBlockState(immutableNeighbour)) == oreType
						&& queued.add(immutableNeighbour)) {
						pending.addLast(immutableNeighbour);
					}
				}
				if (world.destroyBlock(current, true, player, 512)
					&& !damageCascadeTool(player, tool)) {
					break;
				}
			}
		} finally {
			if (startedCascade) {
				CASCADE_BREAKS.remove(player.getUUID());
			}
		}
	}

	private static Block oreType(BlockState state) {
		if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) return Blocks.COAL_ORE;
		if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) return Blocks.IRON_ORE;
		if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)) return Blocks.COPPER_ORE;
		if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)) return Blocks.GOLD_ORE;
		if (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) return Blocks.REDSTONE_ORE;
		if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) return Blocks.LAPIS_ORE;
		if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) return Blocks.DIAMOND_ORE;
		if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) return Blocks.EMERALD_ORE;
		if (state.is(Blocks.NETHER_GOLD_ORE)) return Blocks.NETHER_GOLD_ORE;
		if (state.is(Blocks.NETHER_QUARTZ_ORE)) return Blocks.NETHER_QUARTZ_ORE;
		return null;
	}

	private static boolean isStoneForMinerFortune(BlockState state) {
		return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE);
	}

	private static boolean isOreBlock(BlockState state) {
		return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)
			|| state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)
			|| state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)
			|| state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)
			|| state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)
			|| state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)
			|| state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
			|| state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)
			|| state.is(Blocks.NETHER_GOLD_ORE) || state.is(Blocks.NETHER_QUARTZ_ORE);
	}

	private static ItemStack randomOreDrop(ServerLevel world) {
		ItemStack[] drops = {
			new ItemStack(Items.COAL, 1 + world.random.nextInt(2)),
			new ItemStack(Items.RAW_IRON),
			new ItemStack(Items.RAW_COPPER, 1 + world.random.nextInt(3)),
			new ItemStack(Items.RAW_GOLD),
			new ItemStack(Items.LAPIS_LAZULI, 2 + world.random.nextInt(4)),
			new ItemStack(Items.REDSTONE, 2 + world.random.nextInt(4)),
			new ItemStack(Items.DIAMOND),
			new ItemStack(Items.EMERALD),
			new ItemStack(Items.QUARTZ)
		};
		return drops[world.random.nextInt(drops.length)];
	}

	private static void dropItem(ServerLevel world, BlockPos pos, ItemStack stack) {
		ItemEntity item = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
		world.addFreshEntity(item);
	}

	private static void dropArchaeologyLoot(ServerLevel world, BlockPos pos) {
		int roll = world.random.nextInt(100);
		ItemStack loot;
		if (roll < 30) {
			ItemStack[] potterySherds = {
				new ItemStack(Items.ARCHER_POTTERY_SHERD),
				new ItemStack(Items.ARMS_UP_POTTERY_SHERD),
				new ItemStack(Items.BREWER_POTTERY_SHERD),
				new ItemStack(Items.PRIZE_POTTERY_SHERD),
				new ItemStack(Items.SKULL_POTTERY_SHERD)
			};
			loot = potterySherds[world.random.nextInt(potterySherds.length)];
		} else if (roll < 50) {
			loot = new ItemStack(Items.EMERALD);
		} else if (roll < 53) {
			loot = new ItemStack(Items.DIAMOND);
		} else if (roll < 65) {
			loot = new ItemStack(Items.GOLD_NUGGET, 1 + world.random.nextInt(3));
		} else if (roll < 77) {
			loot = new ItemStack(Items.IRON_NUGGET, 1 + world.random.nextInt(3));
		} else if (roll < 88) {
			loot = new ItemStack(Items.GUNPOWDER, 1 + world.random.nextInt(2));
		} else if (roll < 94) {
			loot = new ItemStack(Items.BRICK, 1 + world.random.nextInt(2));
		} else {
			loot = new ItemStack(Items.TNT);
		}
		dropItem(world, pos, loot);
	}

	private static ItemStack cropProduce(BlockState state) {
		if (state.is(Blocks.WHEAT)) return new ItemStack(Items.WHEAT);
		if (state.is(Blocks.CARROTS)) return new ItemStack(Items.CARROT);
		if (state.is(Blocks.POTATOES)) return new ItemStack(Items.POTATO);
		if (state.is(Blocks.BEETROOTS)) return new ItemStack(Items.BEETROOT);
		return ItemStack.EMPTY;
	}

	private static void breakMiningPlane(
		Level world,
		Player player,
		BlockPos origin,
		net.minecraft.core.Direction.Axis axis,
		int level
	) {
		int radius = Math.min(level, 3);
		boolean startedCascade = CASCADE_BREAKS.add(player.getUUID());
		try {
			for (int first = -radius; first <= radius; first++) {
				for (int second = -radius; second <= radius; second++) {
					if (first == 0 && second == 0) {
						continue;
					}

					BlockPos target;
					if (axis == net.minecraft.core.Direction.Axis.X) {
						target = origin.offset(0, first, second);
					} else if (axis == net.minecraft.core.Direction.Axis.Y) {
						target = origin.offset(first, 0, second);
					} else {
						target = origin.offset(first, second, 0);
					}
					BlockState state = world.getBlockState(target);
					if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)
						&& !state.is(BlockTags.LOGS)
						&& world.getBlockEntity(target) == null) {
						world.destroyBlock(target, true, player, 512);
					}
				}
			}
		} finally {
			if (startedCascade) {
				CASCADE_BREAKS.remove(player.getUUID());
			}
		}
	}

	private static void breakTree(Level world, Player player, BlockPos start) {
		ArrayDeque<BlockPos> pending = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		ItemStack tool = player.getMainHandItem();
		addConnectedBlocks(world, start, BlockTags.LOGS, pending, visited);
		boolean startedCascade = CASCADE_BREAKS.add(player.getUUID());
		try {
			while (!pending.isEmpty()) {
				BlockPos current = pending.removeFirst();
				if (!visited.add(current) || !world.getBlockState(current).is(BlockTags.LOGS)) {
					continue;
				}

				if (!world.destroyBlock(current, true, player, 512)) {
					continue;
				}
				if (!damageCascadeTool(player, tool)) {
					break;
				}
				addConnectedBlocks(world, current, BlockTags.LOGS, pending, visited);
			}
		} finally {
			if (startedCascade) {
				CASCADE_BREAKS.remove(player.getUUID());
			}
		}
	}

	private static void breakLeaves(Level world, Player player, BlockPos start, int level) {
		ArrayDeque<BlockPos> pending = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		ItemStack tool = player.getMainHandItem();
		addConnectedBlocks(world, start, BlockTags.LEAVES, pending, visited);
		boolean startedCascade = CASCADE_BREAKS.add(player.getUUID());
		try {
			while (!pending.isEmpty() && visited.size() < 64 * level) {
				BlockPos current = pending.removeFirst();
				if (!visited.add(current) || !world.getBlockState(current).is(BlockTags.LEAVES)) {
					continue;
				}

				BlockState leafState = world.getBlockState(current);
				if (!world.destroyBlock(current, false, player, 512)) {
					continue;
				}
				if (world instanceof ServerLevel serverWorld) {
					dropItem(serverWorld, current, new ItemStack(leafState.getBlock()));
				}
				if (!damageCascadeTool(player, tool)) {
					break;
				}
				addConnectedBlocks(world, current, BlockTags.LEAVES, pending, visited);
			}
		} finally {
			if (startedCascade) {
				CASCADE_BREAKS.remove(player.getUUID());
			}
		}
	}

	private static boolean damageCascadeTool(Player player, ItemStack tool) {
		if (tool.isEmpty() || player.getMainHandItem() != tool) {
			return false;
		}
		if (tool.isDamageableItem()) {
			tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		}
		return !tool.isEmpty() && player.getMainHandItem() == tool;
	}

	private static void addConnectedBlocks(
		Level world,
		BlockPos center,
		net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag,
		ArrayDeque<BlockPos> pending,
		Set<BlockPos> visited
	) {
		for (BlockPos neighbour : BlockPos.betweenClosed(
			center.getX() - 1,
			center.getY() - 1,
			center.getZ() - 1,
			center.getX() + 1,
			center.getY() + 1,
			center.getZ() + 1
		)) {
			if (!visited.contains(neighbour) && world.getBlockState(neighbour).is(tag)) {
				pending.add(neighbour.immutable());
			}
		}
	}

	private static void smeltOreDrops(Level world, BlockPos pos) {
		AABB area = new AABB(pos).inflate(1.5);
		for (ItemEntity drop : world.getEntitiesOfClass(ItemEntity.class, area, entity -> entity.tickCount <= 4)) {
			ItemStack stack = drop.getItem();
			ItemStack smelted = ItemStack.EMPTY;
			if (stack.is(Items.RAW_IRON) || stack.is(Items.IRON_ORE) || stack.is(Items.DEEPSLATE_IRON_ORE)) {
				smelted = new ItemStack(Items.IRON_INGOT, stack.getCount());
			} else if (stack.is(Items.RAW_GOLD) || stack.is(Items.GOLD_ORE)
				|| stack.is(Items.DEEPSLATE_GOLD_ORE)) {
				smelted = new ItemStack(Items.GOLD_INGOT, stack.getCount());
			} else if (stack.is(Items.RAW_COPPER) || stack.is(Items.COPPER_ORE)
				|| stack.is(Items.DEEPSLATE_COPPER_ORE)) {
				smelted = new ItemStack(Items.COPPER_INGOT, stack.getCount());
			} else if (stack.is(Items.GOLD_NUGGET) && stack.getCount() >= 9) {
				int ingots = stack.getCount() / 9;
				int nuggets = stack.getCount() % 9;
				stack.setCount(nuggets);
				if (nuggets == 0) {
					drop.discard();
				}
				smelted = new ItemStack(Items.GOLD_INGOT, ingots);
			}
			if (!smelted.isEmpty()) {
				if (stack.is(Items.GOLD_NUGGET) && !drop.isRemoved()) {
					dropItem((ServerLevel) world, pos, smelted);
				} else if (!drop.isRemoved()) {
					drop.setItem(smelted);
				} else {
					dropItem((ServerLevel) world, pos, smelted);
				}
			}
		}
	}

	private static void smeltPendingDrops(ServerLevel world, ServerPlayer player) {
		ArrayDeque<PendingSmelt> pending = PENDING_SMELTS.get(player.getUUID());
		if (pending == null) {
			return;
		}
		int queued = pending.size();
		for (int index = 0; index < queued; index++) {
			PendingSmelt next = pending.removeFirst();
			if (next.expiresAt() <= world.getGameTime()) {
				smeltOreDrops(world, next.pos());
			} else {
				pending.addLast(next);
			}
		}
		if (pending.isEmpty()) {
			PENDING_SMELTS.remove(player.getUUID());
		}
	}

	private static void tickPlayer(ServerLevel world, ServerPlayer player) {
		tickHookshot(world, player);
		sanitizeSpawnerStacks(player);
		smeltPendingDrops(world, player);
		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		ItemStack leggings = player.getItemBySlot(EquipmentSlot.LEGS);
		ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
		tickCarriedMob(player, chest);

		int vitality = level(player, chest, "endurance");
		setAttribute(player, Attributes.MAX_HEALTH, VITALITY_MODIFIER, vitality * 4.0);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}

		int lastStand = level(player, chest, "last_stand");
		if (lastStand > 0 && player.getHealth() <= player.getMaxHealth() * 0.25F) {
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, lastStand - 1, true, false));
		}

		int amphibian = level(player, chest, "amphibian");
		if (amphibian > 0 && player.isInWater()) {
			player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, amphibian - 1, true, false));
		}

		int magnetism = level(player, helmet, "magnetism");
		if (magnetism > 0) {
			AABB magnetismRange = player.getBoundingBox().inflate(10.0 + magnetism * 3.0);
			for (Entity collectible : world.getEntities(
				player,
				magnetismRange,
				entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb
			)) {
				Vec3 attraction = player.position().add(0.0, 0.5, 0.0)
					.subtract(collectible.position()).normalize();
				collectible.setDeltaMovement(
					collectible.getDeltaMovement().add(attraction.scale(0.18 + magnetism * 0.07))
				);
			}
		}

		int nightwatcher = level(player, helmet, "nightwatcher");
		MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
		if (nightwatcher > 0 && (nightVision == null || nightVision.getDuration() < 20)) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200 * nightwatcher, 0, true, false));
		}
		if (player.onGround()) {
			USED_DOUBLE_JUMPS.remove(player.getUUID());
		}
		int lethalEdge = level(player, chest, "lethal_edge");
		if (lethalEdge > 0 && chest.is(Items.ELYTRA) && player.isFallFlying()) {
			for (LivingEntity target : world.getEntitiesOfClass(
				LivingEntity.class,
				player.getBoundingBox().inflate(0.6, 0.7, 0.6),
				entity -> entity != player && !(entity instanceof Player) && entity.isAlive()
			)) {
				Long lastHit = LAST_LETHAL_EDGE_TARGET_HIT.get(target.getUUID());
				if (lastHit != null && world.getGameTime() - lastHit < 40) {
					continue;
				}
				float damage = target.getMaxHealth() * (0.5F + 0.1F * lethalEdge);
				if (target.hurt(player.damageSources().playerAttack(player), damage)) {
					LAST_LETHAL_EDGE_TARGET_HIT.put(target.getUUID(), world.getGameTime());
					break;
				}
			}
		}
	}

	private static void tickCarriedMob(ServerPlayer player, ItemStack chest) {
		Mob carried = carriedMob(player);
		if (carried == null) {
			return;
		}
		if (level(player, chest, "carry") == 0 || !carried.isAlive()) {
			releaseCarriedMob(player);
			return;
		}
		if (carried.level() != player.level()) {
			releaseCarriedMob(player);
			return;
		}
		double yaw = Math.toRadians(player.getYRot());
		double backX = Math.sin(yaw) * 0.35;
		double backZ = -Math.cos(yaw) * 0.35;
		carried.setPos(player.getX() + backX, player.getY() + 0.6, player.getZ() + backZ);
		carried.setYRot(player.getYRot());
		carried.setYBodyRot(player.getYRot());
		carried.setDeltaMovement(Vec3.ZERO);
	}

	private static void tickSpikedHarness(ServerLevel world) {
		if (world.getGameTime() % 5 != 0) {
			return;
		}
		for (Entity entity : world.getAllEntities()) {
			if (!(entity instanceof AbstractHorse horse)) {
				continue;
			}
			ItemStack armor = horse.getItemBySlot(EquipmentSlot.BODY);
			int horseWalker = level(horse, armor, "horse_walker");
			if (horseWalker > 0 && (horse.onGround() || horse.isInWater())) {
				BlockPos horsePos = horse.blockPosition();
				for (int x = -horseWalker; x <= horseWalker; x++) {
					for (int z = -horseWalker; z <= horseWalker; z++) {
						BlockPos icePos = horsePos.offset(x, -1, z);
						if (world.getFluidState(icePos).is(net.minecraft.tags.FluidTags.WATER)) {
							world.setBlockAndUpdate(icePos, Blocks.FROSTED_ICE.defaultBlockState());
							world.scheduleTick(icePos, Blocks.FROSTED_ICE, 40 + world.random.nextInt(20));
						}
					}
				}
			}
			int spikes = level(horse, armor, "spiked_harness");
			if (spikes == 0) {
				continue;
			}
			for (Mob mob : world.getEntitiesOfClass(
				Mob.class,
				horse.getBoundingBox().inflate(1.3),
				target -> target != horse && target.isAlive() && target.getVehicle() != horse
			)) {
				Long lastHit = LAST_SPIKED_HARNESS_HIT.get(mob.getUUID());
				if (lastHit != null && world.getGameTime() - lastHit < 20) {
					continue;
				}
				LAST_SPIKED_HARNESS_HIT.put(mob.getUUID(), world.getGameTime());
				mob.hurt(horse.damageSources().thorns(horse), 3.0F + spikes * 3.0F);
				Vec3 away = mob.position().subtract(horse.position()).normalize();
				mob.knockback(0.6 + spikes * 0.2, away.x, away.z);
			}
		}
	}

	private static void setAttribute(
		LivingEntity entity,
		Holder<Attribute> attribute,
		ResourceLocation modifierId,
		double amount
	) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}

		AttributeModifier current = instance.getModifier(modifierId);
		if (amount <= 0.0) {
			if (current != null) {
				instance.removeModifier(modifierId);
			}
		} else if (current == null || current.amount() != amount) {
			instance.removeModifier(modifierId);
			instance.addTransientModifier(new AttributeModifier(
				modifierId,
				amount,
				AttributeModifier.Operation.ADD_VALUE
			));
		}
	}

	static int level(LivingEntity entity, ItemStack stack, String enchantmentId) {
		if (stack.isEmpty()) {
			return 0;
		}
		var enchantments = entity.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key = ResourceKey.create(
			Registries.ENCHANTMENT,
			ResourceLocation.fromNamespaceAndPath("runevestiges", enchantmentId)
		);
		return enchantments.get(key)
			.map(enchantment -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack))
			.orElse(0);
	}

	public record StickyClimbRequest(boolean jumpHeld) implements CustomPacketPayload {
		public static final Type<StickyClimbRequest> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath("runevestiges", "sticky_climb")
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, StickyClimbRequest> CODEC =
			StreamCodec.of(
				(buffer, payload) -> buffer.writeBoolean(payload.jumpHeld()),
				buffer -> new StickyClimbRequest(buffer.readBoolean())
			);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record DoubleJumpRequest() implements CustomPacketPayload {
		public static final Type<DoubleJumpRequest> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath("runevestiges", "double_jump")
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, DoubleJumpRequest> CODEC =
			StreamCodec.unit(new DoubleJumpRequest());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private record VortexEffect(
		Vec3 center,
		int level,
		ResourceKey<Level> dimension,
		long startedAt,
		long expiresAt
	) {
	}

	private record CarriedMob(Mob mob, ResourceKey<Level> dimension, boolean wasNoAi, boolean hadNoGravity) {
	}

	private record HookshotTarget(Vec3 position, ResourceKey<Level> dimension, long expiresAt, double speed) {
	}

	private record PendingSmelt(BlockPos pos, long expiresAt) {
	}
}
