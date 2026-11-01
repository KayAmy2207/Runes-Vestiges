package fr.runevestiges.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null && !this.level.isClientSide()) {
            Containers.dropContents(this.level, pos, this);
        }
        super.preRemoveSideEffects(pos, state);
    }

    public void tickBookAnimation(boolean playerNearby, float playerRotation) {
        if (this.level == null || this.level.getGameTime() == this.lastBookAnimationTick) {
            return;
        }
        this.lastBookAnimationTick = this.level.getGameTime();
        this.oldBookOpen = this.bookOpen;
        this.oldBookRotation = this.bookRotation;
        if (playerNearby) {
            this.targetBookRotation = playerRotation;
            this.bookOpen = Mth.clamp(this.bookOpen + 0.1f, 0.0f, 1.0f);
        } else {
            this.targetBookRotation += 0.02f;
            this.bookOpen = Mth.clamp(this.bookOpen - 0.1f, 0.0f, 1.0f);
        }
        this.targetBookRotation = RuneAltarBlockEntity.wrapRadians(this.targetBookRotation);
        this.bookRotation = RuneAltarBlockEntity.wrapRadians(
            this.bookRotation + RuneAltarBlockEntity.wrapRadians(this.targetBookRotation - this.bookRotation) * 0.4f
        );
    }

    public void tickServer() {
        if (this.level == null || this.level.isClientSide() || !this.getBlockState().is(ModContent.RUNE_ALTAR)
            || this.level.getGameTime() % 20L != 0L) {
            return;
        }

        Player player = this.level.getEntitiesOfClass(
            Player.class,
            new AABB(this.worldPosition).inflate(6.0),
            candidate -> candidate.distanceToSqr(
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5
            ) <= 36.0 && RuneAltarBlockEntity.findRejuvenationTarget(candidate) != null
        ).stream().findFirst().orElse(null);
        ItemStack shard = this.getItem(2);
        if (player == null || shard.isEmpty()) {
            return;
        }

        ItemStack target = RuneAltarBlockEntity.findRejuvenationTarget(player);
        if (target == null) {
            return;
        }
        if (this.repairsRemaining == 0) {
            shard.shrink(1);
            if (shard.isEmpty()) {
                this.setItem(2, ItemStack.EMPTY);
            } else {
                this.setChanged();
            }
            this.repairsRemaining = 10;
        }

        target.setDamageValue(target.getDamageValue() - 1);
        player.getInventory().setChanged();
        --this.repairsRemaining;
        if (this.level instanceof ServerLevel serverLevel) {
            double x = Mth.lerp(0.5, this.worldPosition.getX() + 0.5, player.getX());
            double y = Mth.lerp(0.5, this.worldPosition.getY() + 0.8, player.getY() + player.getBbHeight() * 0.5);
            double z = Mth.lerp(0.5, this.worldPosition.getZ() + 0.5, player.getZ());
            serverLevel.sendParticles(ParticleTypes.ENCHANT, x, y, z, 8, 0.18, 0.18, 0.18, 0.0);
        }
    }

    private static ItemStack findRejuvenationTarget(Player player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (RuneAltarBlockEntity.hasRepairableRejuvenation(player, stack)) {
                return stack;
            }
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND
        }) {
            ItemStack stack = player.getItemBySlot(slot);
            if (RuneAltarBlockEntity.hasRepairableRejuvenation(player, stack)) {
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
        return Mth.lerp(partialTick, this.oldBookOpen, this.bookOpen);
    }

    public float getBookRotation(float partialTick) {
        return this.oldBookRotation + RuneAltarBlockEntity.wrapRadians(this.bookRotation - this.oldBookRotation) * partialTick;
    }

    private static float wrapRadians(float angle) {
        while ((double)angle >= Math.PI) {
            angle -= (float)Math.PI * 2;
        }
        while ((double)angle < -Math.PI) {
            angle += (float)Math.PI * 2;
        }
        return angle;
    }

    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
    }

    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, this.items);
    }

    public int getContainerSize() {
        return this.items.size();
    }

    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(this.items, slot, amount);
        if (!removed.isEmpty()) {
            this.setChanged();
        }
        return removed;
    }

    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    public int getMaxStackSize() {
        return 64;
    }

    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }
}
