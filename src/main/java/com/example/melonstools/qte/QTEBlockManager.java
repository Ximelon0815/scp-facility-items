package com.example.melonstools.qte;

import com.example.melonstools.block.ModBlocks;
import com.example.melonstools.block.entity.QteRedstoneEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class QTEBlockManager extends SavedData {
    public static class BoundQTE {
        public final QTEType type;
        public final int duration;
        public final float difficulty;
        public final String requiredItemId;
        public final int rounds;

        // 完成后动作
        public final boolean redstoneOutput;
        public final int redstoneTicks;
        public final String successCommands;
        public final String failureCommands;

        // 人群交互机制
        public final String teamWhitelist;
        public final String tagWhitelist;
        public final boolean onlyNotCompleted;
        public final boolean bypassAfterComplete;

        // 异常物维护点模式
        public final String bindingMode;
        public final String anomalyInstanceId;
        public final int anomalyRestoreAmount;
        public final int anomalyCooldownSeconds;
        public final int anomalyMinCardLevel;
        public final String anomalyRequiredDepartment;

        // 完成者记录 (UUID 字符串)
        public final Set<String> completedPlayers = new HashSet<>();

        public BoundQTE(QTEType type, int duration, float difficulty, String requiredItemId) {
            this(type, duration, difficulty, requiredItemId, 1);
        }

        public BoundQTE(QTEType type, int duration, float difficulty, String requiredItemId, int rounds) {
            this(type, duration, difficulty, requiredItemId, rounds,
                    false, 20, "", "", "", "", false, false);
        }

        public BoundQTE(QTEType type, int duration, float difficulty, String requiredItemId, int rounds,
                        boolean redstoneOutput, int redstoneTicks,
                        String successCommands, String failureCommands,
                        String teamWhitelist, String tagWhitelist,
                        boolean onlyNotCompleted, boolean bypassAfterComplete) {
            this(type, duration, difficulty, requiredItemId, rounds, redstoneOutput, redstoneTicks,
                    successCommands, failureCommands, teamWhitelist, tagWhitelist, onlyNotCompleted, bypassAfterComplete,
                    "normal", "", 25, 0, 0, "科研部门");
        }

        public BoundQTE(QTEType type, int duration, float difficulty, String requiredItemId, int rounds,
                        boolean redstoneOutput, int redstoneTicks,
                        String successCommands, String failureCommands,
                        String teamWhitelist, String tagWhitelist,
                        boolean onlyNotCompleted, boolean bypassAfterComplete,
                        String bindingMode, String anomalyInstanceId, int anomalyRestoreAmount, int anomalyCooldownSeconds,
                        int anomalyMinCardLevel, String anomalyRequiredDepartment) {
            this.type = type;
            this.duration = duration;
            this.difficulty = difficulty;
            this.requiredItemId = requiredItemId == null ? "" : requiredItemId;
            this.rounds = Math.max(1, rounds);
            this.redstoneOutput = redstoneOutput;
            this.redstoneTicks = Math.max(1, redstoneTicks);
            this.successCommands = successCommands == null ? "" : successCommands;
            this.failureCommands = failureCommands == null ? "" : failureCommands;
            this.teamWhitelist = teamWhitelist == null ? "" : teamWhitelist;
            this.tagWhitelist = tagWhitelist == null ? "" : tagWhitelist;
            this.onlyNotCompleted = onlyNotCompleted;
            this.bypassAfterComplete = bypassAfterComplete;
            this.bindingMode = bindingMode == null || bindingMode.isBlank() ? "normal" : bindingMode;
            this.anomalyInstanceId = anomalyInstanceId == null ? "" : anomalyInstanceId;
            this.anomalyRestoreAmount = Math.max(1, anomalyRestoreAmount);
            this.anomalyCooldownSeconds = Math.max(0, anomalyCooldownSeconds);
            this.anomalyMinCardLevel = Math.max(0, anomalyMinCardLevel);
            this.anomalyRequiredDepartment = anomalyRequiredDepartment == null || anomalyRequiredDepartment.isBlank() ? "科研部门" : anomalyRequiredDepartment;
        }
    }

    private final Map<BlockPos, BoundQTE> boundBlocks = new HashMap<>();

    // Client-side cache for rendering green boxes
    private static final List<BlockPos> clientBoundBlocks = new ArrayList<>();

    public static QTEBlockManager get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getDataStorage().computeIfAbsent(
                    QTEBlockManager::load,
                    QTEBlockManager::new,
                    "qte_blocks"
            );
        }
        return null;
    }

    public void bindQTE(BlockPos pos, QTEType type, int duration, float difficulty, String requiredItemId, int rounds) {
        bindQTE(pos, new BoundQTE(type, duration, difficulty, requiredItemId, rounds));
    }

    public void bindQTE(BlockPos pos, QTEType type, int duration, float difficulty, String requiredItemId) {
        bindQTE(pos, type, duration, difficulty, requiredItemId, 1);
    }

    public void bindQTE(BlockPos pos, BoundQTE newBound) {
        BoundQTE existing = boundBlocks.get(pos);
        if (existing != null) {
            // 重新配置时保留已有的完成记录
            newBound.completedPlayers.addAll(existing.completedPlayers);
        }
        boundBlocks.put(pos, newBound);
        setDirty();
    }

    // ===================== 完成者记录 =====================

    public void recordCompletion(BlockPos pos, UUID playerUuid) {
        BoundQTE bound = boundBlocks.get(pos);
        if (bound != null && playerUuid != null) {
            bound.completedPlayers.add(playerUuid.toString());
            setDirty();
        }
    }

    public boolean hasCompleted(BlockPos pos, UUID playerUuid) {
        BoundQTE bound = boundBlocks.get(pos);
        return bound != null && playerUuid != null && bound.completedPlayers.contains(playerUuid.toString());
    }

    public void clearCompletions(BlockPos pos) {
        BoundQTE bound = boundBlocks.get(pos);
        if (bound != null) {
            bound.completedPlayers.clear();
            setDirty();
        }
    }

    public List<String> getCompletedPlayerNames(BlockPos pos, ServerLevel level) {
        List<String> names = new ArrayList<>();
        BoundQTE bound = boundBlocks.get(pos);
        if (bound == null) return names;
        for (String uuidStr : bound.completedPlayers) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ServerPlayer online = level.getServer().getPlayerList().getPlayer(uuid);
                if (online != null) {
                    names.add(online.getName().getString());
                } else {
                    names.add(uuidStr.substring(0, Math.min(8, uuidStr.length())));
                }
            } catch (IllegalArgumentException ignored) {
                names.add(uuidStr);
            }
        }
        return names;
    }

    // ===================== 红石脉冲 =====================

    /**
     * 在指定位置输出一次红石脉冲：用隐藏发射方块替换原方块并输出信号 15，
     * 经过 ticks 后自动还原原方块及其方块实体数据。
     */
    public static void startRedstonePulse(ServerLevel level, BlockPos pos, int ticks) {
        if (level == null || pos == null) return;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof com.example.melonstools.block.QteRedstoneEmitterBlock) {
            return; // 已在脉冲中
        }
        CompoundTag nbt = null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            nbt = be.saveWithFullMetadata();
        }
        level.setBlock(pos, ModBlocks.QTE_REDSTONE_EMITTER.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof QteRedstoneEmitterBlockEntity emitter) {
            emitter.setOriginal(state, nbt);
        }
        level.scheduleTick(pos, ModBlocks.QTE_REDSTONE_EMITTER.get(), Math.max(1, ticks));
    }

    public void unbindQTE(BlockPos pos) {
        boundBlocks.remove(pos);
        setDirty();
    }

    public BoundQTE getBoundQTE(BlockPos pos) {
        return boundBlocks.get(pos);
    }

    public boolean hasBoundQTE(BlockPos pos) {
        return boundBlocks.containsKey(pos);
    }

    public Map<BlockPos, BoundQTE> getAllBindings() {
        return boundBlocks;
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, BoundQTE> entry : boundBlocks.entrySet()) {
            CompoundTag tag = new CompoundTag();
            BlockPos pos = entry.getKey();
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            
            BoundQTE qte = entry.getValue();
            tag.putInt("type", qte.type.ordinal());
            tag.putInt("duration", qte.duration);
            tag.putFloat("difficulty", qte.difficulty);
            tag.putString("requiredItemId", qte.requiredItemId);
            tag.putInt("rounds", qte.rounds);

            tag.putBoolean("redstoneOutput", qte.redstoneOutput);
            tag.putInt("redstoneTicks", qte.redstoneTicks);
            tag.putString("successCommands", qte.successCommands);
            tag.putString("failureCommands", qte.failureCommands);
            tag.putString("teamWhitelist", qte.teamWhitelist);
            tag.putString("tagWhitelist", qte.tagWhitelist);
            tag.putBoolean("onlyNotCompleted", qte.onlyNotCompleted);
            tag.putBoolean("bypassAfterComplete", qte.bypassAfterComplete);
            tag.putString("bindingMode", qte.bindingMode);
            tag.putString("anomalyInstanceId", qte.anomalyInstanceId);
            tag.putInt("anomalyRestoreAmount", qte.anomalyRestoreAmount);
            tag.putInt("anomalyCooldownSeconds", qte.anomalyCooldownSeconds);
            tag.putInt("anomalyMinCardLevel", qte.anomalyMinCardLevel);
            tag.putString("anomalyRequiredDepartment", qte.anomalyRequiredDepartment);

            ListTag completed = new ListTag();
            for (String uuid : qte.completedPlayers) {
                completed.add(StringTag.valueOf(uuid));
            }
            tag.put("completedPlayers", completed);

            list.add(tag);
        }
        compound.put("bindings", list);
        return compound;
    }

    public static QTEBlockManager load(CompoundTag compound) {
        QTEBlockManager manager = new QTEBlockManager();
        ListTag list = compound.getList("bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            QTEType type = QTEType.fromId(tag.getInt("type"));
            int duration = tag.getInt("duration");
            float difficulty = tag.getFloat("difficulty");
            String requiredItemId = tag.getString("requiredItemId");
            int rounds = tag.contains("rounds") ? tag.getInt("rounds") : 1;

            boolean redstoneOutput = tag.getBoolean("redstoneOutput");
            int redstoneTicks = tag.contains("redstoneTicks") ? tag.getInt("redstoneTicks") : 20;
            String successCommands = tag.getString("successCommands");
            String failureCommands = tag.getString("failureCommands");
            String teamWhitelist = tag.getString("teamWhitelist");
            String tagWhitelist = tag.getString("tagWhitelist");
            boolean onlyNotCompleted = tag.getBoolean("onlyNotCompleted");
            boolean bypassAfterComplete = tag.getBoolean("bypassAfterComplete");
            String bindingMode = tag.contains("bindingMode") ? tag.getString("bindingMode") : "normal";
            String anomalyInstanceId = tag.getString("anomalyInstanceId");
            int anomalyRestoreAmount = tag.contains("anomalyRestoreAmount") ? tag.getInt("anomalyRestoreAmount") : 25;
            int anomalyCooldownSeconds = tag.getInt("anomalyCooldownSeconds");
            int anomalyMinCardLevel = tag.getInt("anomalyMinCardLevel");
            String anomalyRequiredDepartment = tag.contains("anomalyRequiredDepartment") ? tag.getString("anomalyRequiredDepartment") : "科研部门";

            BoundQTE qte = new BoundQTE(type, duration, difficulty, requiredItemId, rounds,
                    redstoneOutput, redstoneTicks, successCommands, failureCommands,
                    teamWhitelist, tagWhitelist, onlyNotCompleted, bypassAfterComplete,
                    bindingMode, anomalyInstanceId, anomalyRestoreAmount, anomalyCooldownSeconds, anomalyMinCardLevel, anomalyRequiredDepartment);

            if (tag.contains("completedPlayers")) {
                ListTag completed = tag.getList("completedPlayers", Tag.TAG_STRING);
                for (int j = 0; j < completed.size(); j++) {
                    qte.completedPlayers.add(completed.getString(j));
                }
            }

            manager.boundBlocks.put(pos, qte);
        }
        return manager;
    }

    // Client side methods
    public static List<BlockPos> getClientBoundBlocks() {
        return clientBoundBlocks;
    }

    public static void setClientBoundBlocks(List<BlockPos> blocks) {
        clientBoundBlocks.clear();
        clientBoundBlocks.addAll(blocks);
    }
}
