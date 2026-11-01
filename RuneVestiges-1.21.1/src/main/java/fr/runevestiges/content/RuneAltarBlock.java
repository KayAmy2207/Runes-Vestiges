package fr.runevestiges.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class RuneAltarBlock extends Block implements EntityBlock {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	public enum Operation {
		ENCHANT,
		DISENCHANT,
		COMBINE
	}

	private final Operation operation;

	public RuneAltarBlock(Properties properties, Operation operation) {
		super(properties);
		this.operation = operation;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RuneAltarBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
		Level level,
		BlockState state,
		BlockEntityType<T> type
	) {
		if (operation != Operation.ENCHANT || type != ModBlockEntities.RUNE_ALTAR) {
			return null;
		}
		return (world, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof RuneAltarBlockEntity altar) {
				altar.tickServer();
			}
		};
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public InteractionResult useWithoutItem(
		BlockState state,
		Level level,
		BlockPos pos,
		Player player,
		BlockHitResult hit
	) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		return openMenu(level, pos, player)
			? InteractionResult.CONSUME
			: InteractionResult.FAIL;
	}

	@Override
	public ItemInteractionResult useItemOn(
		ItemStack heldStack,
		BlockState state,
		Level level,
		BlockPos pos,
		Player player,
		InteractionHand hand,
		BlockHitResult hit
	) {
		if (level.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}

		return openMenu(level, pos, player)
			? ItemInteractionResult.CONSUME
			: ItemInteractionResult.FAIL;
	}

	@Override
	public void onRemove(
		BlockState state,
		Level level,
		BlockPos pos,
		BlockState newState,
		boolean movedByPiston
	) {
		if (!state.is(newState.getBlock()) && !level.isClientSide
			&& level.getBlockEntity(pos) instanceof RuneAltarBlockEntity altar) {
			net.minecraft.world.Containers.dropContents(level, pos, altar);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	private boolean openMenu(Level level, BlockPos pos, Player player) {
		if (!(level.getBlockEntity(pos) instanceof RuneAltarBlockEntity altar)) {
			return false;
		}

		player.openMenu(new SimpleMenuProvider(
			(containerId, inventory, menuPlayer) -> new RuneAltarMenu(
				containerId,
				inventory,
				altar,
				operation
			),
			Component.translatable(altar.getBlockState().getBlock().getDescriptionId())
		));
		return true;
	}
}
