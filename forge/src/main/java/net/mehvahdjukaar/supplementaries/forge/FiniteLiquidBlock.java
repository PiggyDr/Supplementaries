package net.mehvahdjukaar.supplementaries.forge;

import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidInteractionRegistry;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// diff property means we need a diff class
public class FiniteLiquidBlock extends Block implements BucketPickup {

    public static final VoxelShape STABLE_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);
    public static final int MAX_LEVEL = 13;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;

    private final List<FluidState> stateCache;
    private final Supplier<? extends FiniteFluid> supplier;
    private boolean fluidStateCacheInitialized = false;


    public FiniteLiquidBlock(Supplier<? extends FiniteFluid> supplier, BlockBehaviour.Properties arg) {
        super(arg);
        this.supplier = supplier;
        this.stateCache = Lists.newArrayList();
        this.registerDefaultState((this.stateDefinition.any()).setValue(LEVEL, 0));
    }

    public FiniteFluid getFluid() {
        return supplier.get();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        int i = state.getValue(LEVEL);
        if (!this.fluidStateCacheInitialized) {
            this.initFluidStateCache();
        }

        return this.stateCache.get(Math.min(i, MAX_LEVEL));
    }

    protected synchronized void initFluidStateCache() {
        if (!this.fluidStateCacheInitialized) {
            this.stateCache.add(this.getFluid().makeState(MAX_LEVEL, false));

            for (int i = 1; i < MAX_LEVEL; ++i) {
                this.stateCache.add(this.getFluid().makeState(MAX_LEVEL - i, false));
            }
            this.stateCache.add(this.getFluid().makeState(MAX_LEVEL, true));
            this.fluidStateCacheInitialized = true;
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context.isAbove(STABLE_SHAPE, pos, true) && state.getValue(LEVEL) == 0 && context.canStandOnFluid(level.getFluidState(pos.above()), state.getFluidState()) ? STABLE_SHAPE : Shapes.empty();
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getFluidState().isRandomlyTicking();
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        state.getFluidState().randomTick(level, pos, random);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return true;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction direction) {
        return adjacentBlockState.getFluidState().getType().isSame(this.getFluid());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public List<ItemStack> getDrops(BlockState arg, LootParams.Builder arg2) {
        return Collections.emptyList();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!FluidInteractionRegistry.canInteract(level, pos)) {
            level.scheduleTick(pos, state.getFluidState().getType(), this.getFluid().getTickDelay(level));
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (state.getFluidState().isSource() || neighborState.getFluidState().isSource()) {
            level.scheduleTick(currentPos, state.getFluidState().getType(), this.getFluid().getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!FluidInteractionRegistry.canInteract(level, pos)) {
            level.scheduleTick(pos, state.getFluidState().getType(), this.getFluid().getTickDelay(level));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    public ItemStack pickupBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        //find connected blocks around. if their LEVEL sum is greater than 13 pickup fluid and delete them
        AtomicInteger currentLevel = new AtomicInteger(state.getFluidState().getAmount());
        Map<BlockPos, Integer> posList = new HashMap<>();
        posList.put(pos, 0);
        this.findConnectedFluids(level, pos, posList, currentLevel);
        if (currentLevel.get() < MAX_LEVEL) return ItemStack.EMPTY;
        for (Map.Entry<BlockPos, Integer> entry : posList.entrySet()) {
            BlockPos p = entry.getKey();
            Integer value = entry.getValue();
            if (value == 0) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 11);
            }else {
                level.setBlock(p, this.getFluid().makeState(value, false).createLegacyBlock(), 11);
            }
        }
        return new ItemStack(this.getFluid().getBucket());
    }

    // breath first search
    private void findConnectedFluids(LevelAccessor level, BlockPos pos, Map<BlockPos, Integer> remainders, AtomicInteger currentLevel) {
        Queue<BlockPos> queue = new LinkedList<>();
        queue.offer(pos);

        while (!queue.isEmpty()) {
            BlockPos currentPos = queue.poll();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (currentLevel.get() >= MAX_LEVEL) return;
                BlockPos newPos = currentPos.relative(direction);
                if (!remainders.containsKey(newPos)) {
                    BlockState state = level.getBlockState(newPos);
                    if (state.getBlock() instanceof FiniteLiquidBlock) {
                        int l = state.getFluidState().getAmount();
                        if (l > 0) {
                            currentLevel.addAndGet(l);
                            remainders.put(newPos, Math.max(0, currentLevel.get() - MAX_LEVEL));
                            queue.offer(newPos);
                        }
                    }
                }
            }
        }
    }
    @Override
    public Optional<SoundEvent> getPickupSound() {
        return this.getFluid().getPickupSound();
    }

}
