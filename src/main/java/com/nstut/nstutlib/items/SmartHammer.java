package com.nstut.nstutlib.items;

import com.nstut.nstutlib.NsTutLib;
import com.nstut.nstutlib.blocks.MachineBlock;
import com.nstut.nstutlib.blocks.MachineBlockEntity;
import com.nstut.nstutlib.blocks.Multiblock;
import com.nstut.nstutlib.models.MultiblockBlock;
import com.nstut.nstutlib.models.MultiblockPattern;
import com.nstut.nstutlib.views.SmartHammerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

public class SmartHammer extends Item {

    public SmartHammer(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        // Check if player is right-clicking on air (not targeting any block or entity)
        if (level.isClientSide && Minecraft.getInstance().hitResult != null
                && Minecraft.getInstance().hitResult.getType() == HitResult.Type.MISS
                && NsTutLib.IS_DEV_ENV) {
            Minecraft.getInstance().setScreen(new SmartHammerScreen(level));
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }

        // Check if player is right-clicking on a block
        if (!level.isClientSide && Minecraft.getInstance().hitResult != null && Minecraft.getInstance().hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) Minecraft.getInstance().hitResult;
            BlockPos blockPos = blockHitResult.getBlockPos();
            BlockState blockState = level.getBlockState(blockPos);
            Block block = blockState.getBlock();

            // Check if the block is an instance of MachineBlock and player is holding shift key
            if (block instanceof MachineBlock && player.isShiftKeyDown()) {
                BlockEntity blockEntity = level.getBlockEntity(blockPos);
                if (blockEntity instanceof MachineBlockEntity machineBlockEntity) {
                    MultiblockPattern pattern = machineBlockEntity.getMultiblockPattern();
                    buildStructure(level, blockPos, pattern);
                    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                }
            }
        }

        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    private void buildStructure(Level level, BlockPos controllerPos, MultiblockPattern pattern) {
        MultiblockBlock[][][] blockArray = pattern.getPattern();
        BlockPos controllerOffset = null;

        // Find the position of the controller block (null) in the pattern
        outerLoop:
        for (int y = 0; y < blockArray.length; y++) {
            for (int z = 0; z < blockArray[y].length; z++) {
                for (int x = 0; x < blockArray[y][z].length; x++) {
                    if (blockArray[y][z][x] == null) {
                        controllerOffset = new BlockPos(x, y, z);
                        break outerLoop;
                    }
                }
            }
        }

        if (controllerOffset == null) {
            throw new IllegalStateException("Controller block position (null) not found in the pattern");
        }

        // Get the facing direction of the controller block
        BlockState controllerState = level.getBlockState(controllerPos);
        Direction facing = controllerState.getValue(HorizontalDirectionalBlock.FACING);

        // Build the structure relative to the controller block position and facing direction
        for (int y = 0; y < blockArray.length; y++) {
            for (int z = 0; z < blockArray[y].length; z++) {
                for (int x = 0; x < blockArray[y][z].length; x++) {
                    MultiblockBlock structureBlock = blockArray[y][z][x];
                    if (structureBlock != null) {
                        BlockPos targetPos = getRotatedPosition(controllerPos, controllerOffset, x, y, z, facing);
                        level.setBlock(targetPos, structureBlock.getBlock().defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private BlockPos getRotatedPosition(BlockPos controllerPos, BlockPos controllerOffset, int x, int y, int z, Direction facing) {
        int dx = x - controllerOffset.getX();
        int dy = - y + controllerOffset.getY();
        int dz = z - controllerOffset.getZ();

        return switch (facing) {
            case NORTH -> controllerPos.offset(-dx, dy, -dz);
            case SOUTH -> controllerPos.offset(dx, dy, dz);
            case WEST -> controllerPos.offset(-dz, dy, dx);
            case EAST -> controllerPos.offset(dz, dy, -dx);
            default -> controllerPos.offset(dx, dy, dz);
        };
    }
}
