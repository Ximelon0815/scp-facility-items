package com.example.melonstools.block;

import com.example.melonstools.block.entity.QteRedstoneEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 隐藏的红石发射方块：QTE 成功后短暂替换绑定方块，
 * 向四周输出强度 15 的红石信号，脉冲结束后自动还原原方块及其方块实体数据。
 */
public class QteRedstoneEmitterBlock extends Block implements EntityBlock {
    public QteRedstoneEmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 15;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 15;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof QteRedstoneEmitterBlockEntity emitter) {
            BlockState original = emitter.getOriginalState();
            CompoundTag nbt = emitter.getOriginalNbt();

            // 还原原方块
            level.setBlock(pos, original, 3);
            if (nbt != null && original.hasBlockEntity()) {
                BlockEntity newBE = level.getBlockEntity(pos);
                if (newBE != null) {
                    newBE.load(nbt);
                    newBE.setChanged();
                }
            }
            level.sendBlockUpdated(pos, state, original, 3);
        } else {
            // 安全兜底：移除残留的发射方块
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QteRedstoneEmitterBlockEntity(pos, state);
    }
}
