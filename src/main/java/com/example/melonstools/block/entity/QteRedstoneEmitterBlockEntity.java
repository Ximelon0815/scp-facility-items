package com.example.melonstools.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 记录被红石发射方块临时替换的原方块状态与方块实体数据，
 * 以便脉冲结束后完整还原。
 */
public class QteRedstoneEmitterBlockEntity extends BlockEntity {
    private BlockState originalState = Blocks.AIR.defaultBlockState();
    private CompoundTag originalNbt = null;

    public QteRedstoneEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QTE_REDSTONE_EMITTER.get(), pos, state);
    }

    public void setOriginal(BlockState state, CompoundTag nbt) {
        this.originalState = state;
        this.originalNbt = nbt;
        this.setChanged();
    }

    public BlockState getOriginalState() {
        return originalState;
    }

    public CompoundTag getOriginalNbt() {
        return originalNbt;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("origState", NbtUtils.writeBlockState(originalState));
        if (originalNbt != null) {
            tag.put("origBE", originalNbt);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("origState")) {
            HolderGetter<Block> lookup = this.level != null
                    ? this.level.holderLookup(Registries.BLOCK)
                    : BuiltInRegistries.BLOCK.asLookup();
            this.originalState = NbtUtils.readBlockState(lookup, tag.getCompound("origState"));
        }
        if (tag.contains("origBE")) {
            this.originalNbt = tag.getCompound("origBE");
        }
    }
}
