package fr.runevestiges.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public final class RuneAltarBlockEntity extends BlockEntity implements Container {
	private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
	private float bookOpen;
	private float oldBookOpen;
	private float bookRotation;
	private float oldBookRotation;
	private float targetBookRotation;
	private long lastBookAnimationTick = Long.MIN_VALUE;
	private int repairsRemaining;

	public RuneAltarBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.RUNE_ALTAR, pos, state);
	}

	public void tickBookAnimation(boolean playerNearby, float playerRotation) {
		if (level == null || level.getGameTime() == lastBookAnimationTick) {
			return;
		}
		lastBookAnimationTick = level.getGameTime();
		oldBookOpen = bookOpen;
		oldBookRotation = bookRotation;

		if (playerNearby) {
			targetBookRotation = playerRotation;
			bookOpen = Mth.clamp(bookOpen + 0.1F, 0.0F, 1.0F);
		} else {
			targetBookRotation += 0.02F;
			bookOpen = Mth.clamp(bookOpen - 0.1F, 0.0F, 1.0F);
		}

		targetBookRotation = wrapRadians(targetBookRotation);
		bookRotation = wrapRadians(bookRotation + wrapRadians(targetBookRotation - bookRotation) * 0.4F);
	}

	public void tickServer() {
		if (level == null || level.isClientSide || !getBlockState().is(ModContent.RUNE_ALTAR)
			|| level.getGameTime() % 20 != 0) {
			return;
		}

		Player player = level.getEntitiesOfClass(
				Player.class,
				new AABB(worldPosition).inflate(6.0),
				candidate -> candidate.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
					worldPosition.getZ() + 0.5) <= 36.0
					&& findRejuvenationTarget(candidate) != null
			)
			.stream()
			.findFirst()
			.orElse(null);
		ItemStack shard = getItem(2);
		if (player == null || shard.isEmpty()) {
			return;
		}

		if (repairsRemaining == 0) {
			shard.shrink(1);
			if (shard.isEmpty()) {
				setItem(2, ItemStack.EMPTY);
			} else {
				setChanged();
			}
			repairsRemaining = 10;
		}

		ItemStack target = findRejuvenationTarget(player);
		if (target == null) {
			return;
		}
		target.setDamageValue(target.getDamageValue() - 1);
		player.getInventory().setChanged();
		repairsRemaining--;

		if (level instanceof ServerLevel serverLevel) {
			double x = Mth.lerp(0.5, worldPosition.getX() + 0.5, player.getX());
			double y = Mth.lerp(0.5, worldPosition.getY() + 0.8, player.getY() + player.getBbHeight() * 0.5);
			double z = Mth.lerp(0.5, worldPosition.getZ() + 0.5, player.getZ());
			serverLevel.sendParticles(ParticleTypes.ENCHANT, x, y, z, 8, 0.18, 0.18, 0.18, 0.0);
		}
	}

	private static ItemStack findRejuvenationTarget(Player player) {
		for (ItemStack stack : player.getInventory().items) {
			if (hasRepairableRejuvenation(player, stack)) {
				return stack;
			}
		}
		for (ItemStack stack : player.getInventory().armor) {
			if (hasRepairableRejuvenation(player, stack)) {
				return stack;
			}
		}
		for (ItemStack stack : player.getInventory().offhand) {
			if (hasRepairableRejuvenation(player, stack)) {
				return stack;
			}
		}
		return null;
	}

	private static boolean hasRepairableRejuvenation(Player player, ItemStack stack) {
		return stack.isDamageableItem() && stack.isDamaged()
			&& ModEnchantmentEffects.level(player, stack, "rejuvenation") > 0;
	}

	public float getBookOpen(float partialTick) {
		return Mth.lerp(partialTick, oldBookOpen, bookOpen);
	}

	public float getBookRotation(float partialTick) {
		return oldBookRotation + wrapRadians(bookRotation - oldBookRotation) * partialTick;
	}

	private static float wrapRadians(float angle) {
		while (angle >= Math.PI) {
			angle -= (float) (Math.PI * 2.0);
		}
		while (angle < -Math.PI) {
			angle += (float) (Math.PI * 2.0);
		}
		return angle;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		ContainerHelper.loadAllItems(tag, items, registries);
	}

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
		if (!removed.isEmpty()) {
			setChanged();
		}
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (stack.getCount() > getMaxStackSize()) {
			stack.setCount(getMaxStackSize());
		}
		setChanged();
	}

	@Override
	public int getMaxStackSize() {
		return 64;
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
		setChanged();
	}
}
