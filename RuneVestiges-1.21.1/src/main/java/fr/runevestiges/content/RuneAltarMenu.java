package fr.runevestiges.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class RuneAltarMenu extends AbstractContainerMenu {
	private static final int RESULT_SLOT = 3;
	private static final int PLAYER_INVENTORY_START = 4;
	private static final int PLAYER_HOTBAR_START = 31;
	private static final int PLAYER_INVENTORY_END = 40;

	private final Container inputContainer;
	private final SimpleContainer resultContainer = new SimpleContainer(1);
	private final RuneAltarBlockEntity altar;
	private final DataSlot operationData = DataSlot.standalone();

	public RuneAltarMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new SimpleContainer(3), null, RuneAltarBlock.Operation.ENCHANT);
	}

	public RuneAltarMenu(
		int containerId,
		Inventory playerInventory,
		RuneAltarBlockEntity altar,
		RuneAltarBlock.Operation operation
	) {
		this(containerId, playerInventory, altar, altar, operation);
	}

	private RuneAltarMenu(
		int containerId,
		Inventory playerInventory,
		Container inputContainer,
		RuneAltarBlockEntity altar,
		RuneAltarBlock.Operation operation
	) {
		super(ModMenus.RUNE_ALTAR, containerId);
		this.inputContainer = inputContainer;
		this.altar = altar;
		operationData.set(operation.ordinal());

		addSlot(new Slot(inputContainer, 0, 26, 36) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return mayPlaceInput(0, stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addSlot(new Slot(inputContainer, 1, 62, 36) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return mayPlaceInput(1, stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addSlot(new Slot(inputContainer, 2, 98, 36) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return getOperation() == RuneAltarBlock.Operation.ENCHANT
					&& stack.is(ModContent.RUNIC_SHARD);
			}
		});
		addSlot(new Slot(resultContainer, 0, 152, 36) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack stack) {
				super.onTake(player, stack);
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(
					playerInventory,
					column + row * 9 + 9,
					16 + column * 18,
					84 + row * 18
				));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 16 + column * 18, 142));
		}
		addDataSlot(operationData);
		updateResult();
	}

	public RuneAltarBlock.Operation getOperation() {
		return RuneAltarBlock.Operation.values()[operationData.get()];
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (container == inputContainer) {
			updateResult();
		}
	}

	@Override
	public void broadcastChanges() {
		if (altar != null) {
			updateResult();
		}
		super.broadcastChanges();
	}

	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player player) {
		if (slotId == RESULT_SLOT && !player.level().isClientSide
			&& clickType == ClickType.PICKUP && (button == 0 || button == 1)) {
			updateResult();
			ItemStack result = resultContainer.getItem(0).copy();
			if (result.isEmpty()) {
				return;
			}
			if (!canAfford(player)) {
				showMissingCost(player);
				return;
			}

			ItemStack carried = getCarried();
			if (carried.isEmpty()) {
				setCarried(result);
			} else if (ItemStack.isSameItemSameComponents(carried, result)
				&& carried.getCount() + result.getCount() <= carried.getMaxStackSize()) {
				carried.grow(result.getCount());
				setCarried(carried);
			} else {
				return;
			}

			completeOperation(player);
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index < 0 || index >= slots.size()) {
			return ItemStack.EMPTY;
		}
		if (index == RESULT_SLOT && altar != null) {
			updateResult();
		}

		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index == RESULT_SLOT) {
			if (!canAfford(player)) {
				showMissingCost(player);
				return ItemStack.EMPTY;
			}
			if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onTake(player, original);
			completeOperation(player);
			return original;
		}

		if (index < PLAYER_INVENTORY_START) {
			if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (index < PLAYER_HOTBAR_START) {
			if (!moveItemStackTo(stack, PLAYER_HOTBAR_START, PLAYER_INVENTORY_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_HOTBAR_START, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return altar == null || altar.stillValid(player);
	}

	private boolean mayPlaceInput(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (slot == 2) {
			return getOperation() == RuneAltarBlock.Operation.ENCHANT
				&& stack.is(ModContent.RUNIC_SHARD);
		}

		return switch (getOperation()) {
			case ENCHANT -> slot == 0
				? stack.is(Items.ENCHANTED_BOOK)
				: !stack.is(Items.BOOK)
					&& !stack.is(Items.ENCHANTED_BOOK)
					&& EnchantmentHelper.canStoreEnchantments(stack);
			case DISENCHANT -> slot == 0
				? !stack.is(Items.BOOK) && !stack.is(Items.ENCHANTED_BOOK)
				: stack.is(Items.BOOK);
			case COMBINE -> stack.is(Items.ENCHANTED_BOOK);
		};
	}

	private void updateResult() {
		resultContainer.setItem(0, createResult());
	}

	private ItemStack createResult() {
		ItemStack first = inputContainer.getItem(0);
		ItemStack second = inputContainer.getItem(1);
		return switch (getOperation()) {
			case ENCHANT -> createEnchantedItem(first, second);
			case DISENCHANT -> createExtractedBook(first, second);
			case COMBINE -> createCombinedBook(first, second);
		};
	}

	private static ItemStack createEnchantedItem(ItemStack book, ItemStack target) {
		if (!book.is(Items.ENCHANTED_BOOK)
			|| target.isEmpty()
			|| !EnchantmentHelper.canStoreEnchantments(target)
			|| target.is(Items.BOOK)
			|| target.is(Items.ENCHANTED_BOOK)) {
			return ItemStack.EMPTY;
		}

		var bookEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(book);
		if (bookEnchantments.isEmpty()) {
			return ItemStack.EMPTY;
		}
		var currentEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(target);
		Set<Holder<Enchantment>> resultingEnchantments = new HashSet<>(currentEnchantments.keySet());
		var applicableEnchantments = new ArrayList<Map.Entry<Holder<Enchantment>, Integer>>();
		for (var entry : bookEnchantments.entrySet()) {
			Holder<Enchantment> enchantment = entry.getKey();
			if (entry.getValue() <= currentEnchantments.getLevel(enchantment)
				|| !enchantment.value().canEnchant(target)) {
				continue;
			}

			Set<Holder<Enchantment>> otherEnchantments = new HashSet<>(resultingEnchantments);
			otherEnchantments.remove(enchantment);
			if (EnchantmentHelper.isEnchantmentCompatible(otherEnchantments, enchantment)) {
				applicableEnchantments.add(entry);
				resultingEnchantments.add(enchantment);
			}
		}
		if (applicableEnchantments.isEmpty()) {
			return ItemStack.EMPTY;
		}

		ItemStack result = target.copy();
		EnchantmentHelper.updateEnchantments(result, mutable -> {
			for (Map.Entry<Holder<Enchantment>, Integer> entry : applicableEnchantments) {
				mutable.set(entry.getKey(), entry.getValue());
			}
		});
		return result;
	}

	private static ItemStack createExtractedBook(ItemStack item, ItemStack plainBook) {
		if (item.isEmpty() || !plainBook.is(Items.BOOK)) {
			return ItemStack.EMPTY;
		}
		Holder<Enchantment> enchantment = firstEnchantment(item);
		if (enchantment == null) {
			return ItemStack.EMPTY;
		}
		int level = EnchantmentHelper.getEnchantmentsForCrafting(item).getLevel(enchantment);
		return ModContent.createEnchantedBook(enchantment, level);
	}

	private static ItemStack createCombinedBook(ItemStack firstBook, ItemStack secondBook) {
		if (!firstBook.is(Items.ENCHANTED_BOOK)
			|| !secondBook.is(Items.ENCHANTED_BOOK)
			|| firstBook.getCount() != 1
			|| secondBook.getCount() != 1) {
			return ItemStack.EMPTY;
		}

		var firstEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(firstBook);
		var secondEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(secondBook);
		if (firstEnchantments.size() != 1 || secondEnchantments.size() != 1) {
			return ItemStack.EMPTY;
		}

		var first = firstEnchantments.entrySet().iterator().next();
		var second = secondEnchantments.entrySet().iterator().next();
		if (!first.getKey().equals(second.getKey()) || !first.getValue().equals(second.getValue())) {
			return ItemStack.EMPTY;
		}

		int nextLevel = first.getValue() + 1;
		if (nextLevel > first.getKey().value().getMaxLevel()) {
			return ItemStack.EMPTY;
		}
		return ModContent.createEnchantedBook(first.getKey(), nextLevel);
	}

	private static Holder<Enchantment> firstEnchantment(ItemStack item) {
		var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(item);
		Holder<Enchantment> selected = null;
		String selectedId = null;
		for (Holder<Enchantment> candidate : enchantments.keySet()) {
			String candidateId = candidate.unwrapKey().orElseThrow().location().toString();
			if (selectedId == null || candidateId.compareTo(selectedId) < 0) {
				selected = candidate;
				selectedId = candidateId;
			}
		}
		return selected;
	}

	private boolean canAfford(Player player) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		if (player.experienceLevel < (getOperation() == RuneAltarBlock.Operation.DISENCHANT ? 3 : 2)) {
			return false;
		}
		return getOperation() != RuneAltarBlock.Operation.ENCHANT
			|| player.getInventory().findSlotMatchingItem(new ItemStack(ModContent.RUNIC_SHARD)) >= 0;
	}

	private void showMissingCost(Player player) {
		if (getOperation() == RuneAltarBlock.Operation.ENCHANT
			&& !player.getAbilities().instabuild
			&& player.getInventory().findSlotMatchingItem(new ItemStack(ModContent.RUNIC_SHARD)) < 0) {
			player.displayClientMessage(Component.translatable("message.runevestiges.need_shard"), true);
			return;
		}
		player.displayClientMessage(Component.translatable("message.runevestiges.need_levels"), true);
	}

	private void completeOperation(Player player) {
		if (player.level().isClientSide || !canAfford(player)) {
			return;
		}

		switch (getOperation()) {
			case ENCHANT -> {
				consumeInput(0);
				consumeInput(1);
				if (!player.getAbilities().instabuild) {
					int shardSlot = player.getInventory()
						.findSlotMatchingItem(new ItemStack(ModContent.RUNIC_SHARD));
					player.getInventory().removeItem(shardSlot, 1);
				}
				player.giveExperienceLevels(player.getAbilities().instabuild ? 0 : -2);
				player.displayClientMessage(Component.translatable("message.runevestiges.applied"), true);
			}
			case DISENCHANT -> {
				ItemStack target = inputContainer.getItem(0);
				Holder<Enchantment> enchantment = firstEnchantment(target);
				if (enchantment != null) {
					EnchantmentHelper.updateEnchantments(target, mutable -> mutable.set(enchantment, 0));
				}
				inputContainer.removeItem(1, 1);
				player.giveExperienceLevels(player.getAbilities().instabuild ? 0 : -3);
				player.displayClientMessage(Component.translatable("message.runevestiges.extracted"), true);
			}
			case COMBINE -> {
				consumeInput(0);
				consumeInput(1);
				player.giveExperienceLevels(player.getAbilities().instabuild ? 0 : -2);
				player.displayClientMessage(Component.translatable("message.runevestiges.combined"), true);
			}
		}
		updateResult();
		broadcastChanges();
	}

	private void consumeInput(int slot) {
		inputContainer.setItem(slot, ItemStack.EMPTY);
		inputContainer.setChanged();
	}
}
