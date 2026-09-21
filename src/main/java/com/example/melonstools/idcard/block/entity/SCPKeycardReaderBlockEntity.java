package com.example.melonstools.idcard.block.entity;

import com.example.melonstools.idcard.item.custom.IdentityCardItem;
import com.google.common.collect.ImmutableList;
import net.geforcemods.securitycraft.api.ILinkedAction;
import net.geforcemods.securitycraft.api.LinkableBlockEntity;
import net.geforcemods.securitycraft.api.LinkedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SCPKeycardReaderBlockEntity extends LinkableBlockEntity {
    private List<LinkedBlock> linkedBlocks = new ArrayList<>();
    private int accessIndex = 1;
    private List<String> requiredDepartments = new ArrayList<>(List.of("\u6240\u6709\u90E8\u95E8"));
    private boolean latchMode = false;
    private int openTicks = 40;
    private boolean pairingOnly = false;
    private final Set<BlockPos> pairedPositions = new HashSet<>();
    
    public SCPKeycardReaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCP_KEYCARD_READER_BLOCK_ENTITY.get(), pos, state);
    }

    public void updateConfig(int accessIndex, List<String> departments, boolean latchMode, int openTicks, boolean pairingOnly) {
        this.accessIndex = accessIndex;
        this.requiredDepartments = new ArrayList<>(departments);
        this.latchMode = latchMode;
        this.openTicks = Math.max(1, openTicks);
        this.pairingOnly = pairingOnly;
        if (this.requiredDepartments.isEmpty()) {
            this.requiredDepartments.add("\u6240\u6709\u90E8\u95E8");
        }
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public int getAccessIndex() {
        return this.accessIndex;
    }

    public boolean isLatchMode() {
        return this.latchMode;
    }

    public int getOpenTicks() {
        return this.openTicks;
    }

    public boolean isPairingOnly() {
        return this.pairingOnly;
    }

    /** 直接设置锁存开关状态（用于自身切换或同步对门门锁）。 */
    public void setPoweredForLatch(boolean powered) {
        if (this.level == null) return;
        boolean current = this.getBlockState().getValue(BlockStateProperties.POWERED);
        if (current == powered) return;
        this.propagate(new ILinkedAction.StateChanged<>(BlockStateProperties.POWERED, current, powered), this);
        this.level.setBlock(this.worldPosition, this.getBlockState().setValue(BlockStateProperties.POWERED, powered), 3);
        this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
        this.level.updateNeighborsAt(this.worldPosition.relative(this.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite()), this.getBlockState().getBlock());
    }

    /** 应用开关状态：锁存模式只切换状态；普通模式开启时同时安排自动关闭。 */
    public void applyPoweredState(boolean powered) {
        if (this.level == null) return;
        boolean current = this.getBlockState().getValue(BlockStateProperties.POWERED);
        if (current != powered) {
            setPoweredForLatch(powered);
        }
        if (powered && !this.latchMode) {
            this.level.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), Math.max(1, this.openTicks));
        }
    }

    /** 关闭所有已配对的门锁（本侧自动关闭时同步）。 */
    public void closePairedReader() {
        if (this.level == null) return;
        for (net.minecraft.world.level.block.entity.BlockEntity partner : getPairedReaderEntities()) {
            com.example.melonstools.idcard.ReaderPairingHelper.applyPairedState(partner, false);
        }
    }

    // ===================== 对门配对（支持多配对） =====================

    public Set<BlockPos> getPairedPositions() {
        return new HashSet<>(this.pairedPositions);
    }

    public boolean isPairedTo(BlockPos pos) {
        return this.pairedPositions.contains(pos);
    }

    public void addPairedPos(BlockPos pos) {
        if (this.pairedPositions.add(pos)) {
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    public void removePairedPos(BlockPos pos) {
        if (this.pairedPositions.remove(pos)) {
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    /** 获取所有已配对的门锁方块实体（跳过已损坏的）。 */
    public java.util.List<net.minecraft.world.level.block.entity.BlockEntity> getPairedReaderEntities() {
        java.util.List<net.minecraft.world.level.block.entity.BlockEntity> list = new ArrayList<>();
        if (this.level == null) return list;
        for (BlockPos pos : this.pairedPositions) {
            net.minecraft.world.level.block.entity.BlockEntity be =
                    com.example.melonstools.idcard.ReaderPairingHelper.getReaderAt(this.level, pos);
            if (be != null) list.add(be);
        }
        return list;
    }

    public List<String> getRequiredDepartments() {
        return new ArrayList<>(this.requiredDepartments);
    }

    public void cycleAccessLevel(Player player) {
        this.accessIndex = (this.accessIndex % 5) + 1;
        if (this.level != null && !this.level.isClientSide) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7\u0061\u6240\u9700\u6743\u9650\u5DF2\u5207\u6362\u81F3\u003A\u0020\u7B49\u7EA7\u0020" + this.accessIndex), true);
        }
        this.setChanged();
    }

    public void tryOpenDoor(Player player, ItemStack card) {
        // 仅配对模式：不接受刷卡，只能由已配对的门锁开启
        if (this.pairingOnly) {
            if (this.level != null) {
                this.level.playSound(null, this.worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
            }
            return;
        }

        boolean hasAccess = false;
        
        if (player.getTags().contains("trbypass")) {
            hasAccess = true;
        }
        
        ItemStack activeCard = card;
        if (!(activeCard.getItem() instanceof IdentityCardItem)) {
            if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
                ItemStack curioStack = com.example.melonstools.compat.CuriosCompat.findIdentityCard(player);
                if (!curioStack.isEmpty()) {
                    activeCard = curioStack;
                }
            }
        }

        if (!hasAccess && activeCard.getItem() instanceof IdentityCardItem) {
            int cardLvl = IdentityCardItem.getCardLevel(activeCard);
            int requiredLvl = this.accessIndex; 
            
            boolean departmentMatch = false;
            if (this.requiredDepartments.isEmpty() || this.requiredDepartments.contains("\u6240\u6709\u90E8\u95E8") || this.requiredDepartments.contains("\u4E0D\u9650")) {
                departmentMatch = true;
            } else {
                String cardDept = "";
                if (activeCard.hasTag() && activeCard.getTag().contains("CardDepartment")) {
                    cardDept = activeCard.getTag().getString("CardDepartment");
                }
                
                if (cardDept.equals("\u7BA1\u7406\u90E8\u95E8") || cardDept.equals("\u6240\u6709\u90E8\u95E8")) {
                    departmentMatch = true;
                } else {
                    departmentMatch = this.requiredDepartments.contains(cardDept);
                }
            }
            
            if (cardLvl >= requiredLvl && departmentMatch) {
                hasAccess = true;
            }
        }
        
        if (hasAccess) {
            if (this.level == null) return;
            boolean currentPowered = this.getBlockState().getValue(BlockStateProperties.POWERED);
            boolean newPowered = this.latchMode ? !currentPowered : true;

            // 应用自身开关状态（普通模式会安排自动关闭）
            applyPoweredState(newPowered);

            // 同步所有已配对的门锁（一层传播，不会级联到它们的配对对象）
            for (net.minecraft.world.level.block.entity.BlockEntity partner : getPairedReaderEntities()) {
                com.example.melonstools.idcard.ReaderPairingHelper.applyPairedState(partner, newPowered);
            }

            this.level.playSound(null, this.worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0F,
                    this.latchMode ? (newPowered ? 1.0F : 0.6F) : 1.0F);
        } else {
            if (this.level != null) {
                this.level.playSound(null, this.worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
            }
        }
    }

    @Override
    protected ImmutableList<LinkedBlock> getLinkedBlocks() {
        return ImmutableList.copyOf(linkedBlocks);
    }

    @Override
    protected void addLinkedBlock(LinkedBlock block) {
        if (!linkedBlocks.contains(block)) {
            linkedBlocks.add(block);
            this.setChanged();
        }
    }

    @Override
    protected void removeLinkedBlock(LinkedBlock block) {
        linkedBlocks.remove(block);
        this.setChanged();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("accessIndex", accessIndex);
        tag.putBoolean("latchMode", latchMode);
        tag.putInt("openTicks", openTicks);
        tag.putBoolean("pairingOnly", pairingOnly);
        if (!pairedPositions.isEmpty()) {
            ListTag pairedList = new ListTag();
            for (BlockPos pos : pairedPositions) {
                CompoundTag pt = new CompoundTag();
                pt.putInt("x", pos.getX());
                pt.putInt("y", pos.getY());
                pt.putInt("z", pos.getZ());
                pairedList.add(pt);
            }
            tag.put("pairedPositions", pairedList);
        }
        
        ListTag deptList = new ListTag();
        for (String dept : this.requiredDepartments) {
            deptList.add(StringTag.valueOf(dept));
        }
        tag.put("requiredDepartments", deptList);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("accessIndex")) {
            this.accessIndex = tag.getInt("accessIndex");
        }
        if (tag.contains("latchMode")) {
            this.latchMode = tag.getBoolean("latchMode");
        }
        if (tag.contains("openTicks")) {
            this.openTicks = Math.max(1, tag.getInt("openTicks"));
        }
        if (tag.contains("pairingOnly")) {
            this.pairingOnly = tag.getBoolean("pairingOnly");
        }
        if (tag.contains("pairedPositions", 9)) {
            this.pairedPositions.clear();
            ListTag pairedList = tag.getList("pairedPositions", 10);
            for (int i = 0; i < pairedList.size(); i++) {
                CompoundTag pt = pairedList.getCompound(i);
                this.pairedPositions.add(new BlockPos(pt.getInt("x"), pt.getInt("y"), pt.getInt("z")));
            }
        }
        if (tag.contains("requiredDepartments", 9)) {
            this.requiredDepartments.clear();
            ListTag deptList = tag.getList("requiredDepartments", 8);
            for (int i = 0; i < deptList.size(); i++) {
                this.requiredDepartments.add(deptList.getString(i));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setRemoved() {
        // 方块被移除时从所有配对对象的列表中清理指向自己的引用
        if (this.level != null && !this.level.isClientSide && !this.pairedPositions.isEmpty()) {
            for (BlockPos pos : new ArrayList<>(this.pairedPositions)) {
                net.minecraft.world.level.block.entity.BlockEntity partner =
                        com.example.melonstools.idcard.ReaderPairingHelper.getReaderAt(this.level, pos);
                if (partner != null) {
                    com.example.melonstools.idcard.ReaderPairingHelper.removePairedPos(partner, this.worldPosition);
                }
            }
            this.pairedPositions.clear();
        }
        super.setRemoved();
    }

    @Override
    public net.geforcemods.securitycraft.api.Option<?>[] customOptions() {
        return new net.geforcemods.securitycraft.api.Option[0];
    }

    @Override
    public net.geforcemods.securitycraft.misc.ModuleType[] acceptedModules() {
        return new net.geforcemods.securitycraft.misc.ModuleType[0];
    }
}
