package com.example.melonstools.idcard;

import com.example.melonstools.idcard.block.SCPCheckpointKeycardReaderBlock;
import com.example.melonstools.idcard.block.SCPKeycardReaderBlock;
import com.example.melonstools.idcard.block.SCPLockedKeycardReaderBlock;
import com.example.melonstools.idcard.block.entity.SCPCheckpointKeycardReaderBlockEntity;
import com.example.melonstools.idcard.block.entity.SCPKeycardReaderBlockEntity;
import com.example.melonstools.idcard.block.entity.SCPLockedKeycardReaderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * 刷卡器"对门门锁"配对工具。
 * 支持两种配对方式：
 * - 左键（螺丝刀）：一对一配对，重新配对会替换旧配对；
 * - 右键（螺丝刀）：多选配对，可追加多个配对对象（一对多）。
 * 触发一个门锁时只同步它的直接配对对象（一层传播，不递归级联）。
 */
public final class ReaderPairingHelper {
    private ReaderPairingHelper() {
    }

    public static boolean isReaderBlock(Block block) {
        return block instanceof SCPKeycardReaderBlock
                || block instanceof SCPLockedKeycardReaderBlock
                || block instanceof SCPCheckpointKeycardReaderBlock;
    }

    /** 获取指定位置上的刷卡器方块实体（任意类型），不是刷卡器则返回 null。 */
    public static BlockEntity getReaderAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SCPKeycardReaderBlockEntity
                || be instanceof SCPLockedKeycardReaderBlockEntity
                || be instanceof SCPCheckpointKeycardReaderBlockEntity) {
            return be;
        }
        return null;
    }

    /** 获取门锁的全部配对坐标。 */
    public static Set<BlockPos> getPairedPositions(BlockEntity be) {
        if (be instanceof SCPKeycardReaderBlockEntity r) return r.getPairedPositions();
        if (be instanceof SCPLockedKeycardReaderBlockEntity r) return r.getPairedPositions();
        if (be instanceof SCPCheckpointKeycardReaderBlockEntity r) return r.getPairedPositions();
        return new HashSet<>();
    }

    /** 检查门锁是否已配对到指定坐标。 */
    public static boolean isPairedTo(BlockEntity be, BlockPos pos) {
        return getPairedPositions(be).contains(pos);
    }

    public static void addPairedPos(BlockEntity be, BlockPos pos) {
        if (be instanceof SCPKeycardReaderBlockEntity r) {
            r.addPairedPos(pos);
        } else if (be instanceof SCPLockedKeycardReaderBlockEntity r) {
            r.addPairedPos(pos);
        } else if (be instanceof SCPCheckpointKeycardReaderBlockEntity r) {
            r.addPairedPos(pos);
        }
    }

    public static void removePairedPos(BlockEntity be, BlockPos pos) {
        if (be instanceof SCPKeycardReaderBlockEntity r) {
            r.removePairedPos(pos);
        } else if (be instanceof SCPLockedKeycardReaderBlockEntity r) {
            r.removePairedPos(pos);
        } else if (be instanceof SCPCheckpointKeycardReaderBlockEntity r) {
            r.removePairedPos(pos);
        }
    }

    /** 一对一配对：清除 a、b 各自的旧配对后互相配对（替换语义，原左键行为）。 */
    public static void pairReaders(BlockEntity a, BlockEntity b) {
        if (a == null || b == null) return;
        if (a.getBlockPos().equals(b.getBlockPos())) return;
        unpairReader(a);
        unpairReader(b);
        addPairedPos(a, b.getBlockPos());
        addPairedPos(b, a.getBlockPos());
    }

    /** 追加多配对：将 b 添加到 a 的配对列表（不影响 a 的其它配对）。 */
    public static void addPair(BlockEntity a, BlockEntity b) {
        if (a == null || b == null) return;
        if (a.getBlockPos().equals(b.getBlockPos())) return;
        addPairedPos(a, b.getBlockPos());
        addPairedPos(b, a.getBlockPos());
    }

    /** 解除指定门锁的全部配对（同时从所有配对对象的列表中移除自己）。 */
    public static void unpairReader(BlockEntity reader) {
        if (reader == null || reader.getLevel() == null) return;
        Set<BlockPos> myPairs = new HashSet<>(getPairedPositions(reader));
        for (BlockPos pos : myPairs) {
            BlockEntity partner = getReaderAt(reader.getLevel(), pos);
            if (partner != null) {
                removePairedPos(partner, reader.getBlockPos());
            }
        }
        for (BlockPos pos : myPairs) {
            removePairedPos(reader, pos);
        }
    }

    /** 解除 a、b 之间的配对（不影响各自其它配对）。 */
    public static void removePair(BlockEntity a, BlockEntity b) {
        if (a == null || b == null) return;
        removePairedPos(a, b.getBlockPos());
        removePairedPos(b, a.getBlockPos());
    }

    /** 对门锁应用开关状态（普通模式会安排自动关闭）。 */
    public static void applyPairedState(BlockEntity be, boolean powered) {
        if (be instanceof SCPKeycardReaderBlockEntity r) {
            r.applyPoweredState(powered);
        } else if (be instanceof SCPLockedKeycardReaderBlockEntity r) {
            r.applyPoweredState(powered);
        } else if (be instanceof SCPCheckpointKeycardReaderBlockEntity r) {
            r.applyPoweredState(powered);
        }
    }

    /** 格式化配对坐标列表，用于提示消息。 */
    public static String formatPositions(Set<BlockPos> positions) {
        ArrayList<String> names = new ArrayList<>();
        for (BlockPos p : positions) {
            names.add(p.toShortString());
        }
        return String.join(", ", names);
    }
}
