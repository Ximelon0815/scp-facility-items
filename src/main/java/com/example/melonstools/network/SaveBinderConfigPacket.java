package com.example.melonstools.network;

import com.example.melonstools.qte.QTEBlockManager;
import com.example.melonstools.qte.QTEType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SaveBinderConfigPacket {
    private final BlockPos pos;
    private final boolean isBound;
    private final QTEType type;
    private final int duration;
    private final float difficulty;
    private final int rounds;

    private final String requiredItemId;

    private final boolean redstoneOutput;
    private final int redstoneTicks;
    private final String successCommands;
    private final String failureCommands;
    private final String teamWhitelist;
    private final String tagWhitelist;
    private final boolean onlyNotCompleted;
    private final boolean bypassAfterComplete;
    private final boolean clearCompletions;
    private final String bindingMode;
    private final String anomalyInstanceId;
    private final int anomalyRestoreAmount;
    private final int anomalyCooldownSeconds;
    private final int anomalyMinCardLevel;
    private final String anomalyRequiredDepartment;

    public SaveBinderConfigPacket(BlockPos pos, boolean isBound, QTEType type, int duration, float difficulty, String requiredItemId, int rounds,
                                  boolean redstoneOutput, int redstoneTicks, String successCommands, String failureCommands,
                                  String teamWhitelist, String tagWhitelist, boolean onlyNotCompleted, boolean bypassAfterComplete,
                                  boolean clearCompletions) {
        this.pos = pos;
        this.isBound = isBound;
        this.type = type;
        this.duration = duration;
        this.difficulty = difficulty;
        this.requiredItemId = requiredItemId == null ? "" : requiredItemId;
        this.rounds = rounds;
        this.redstoneOutput = redstoneOutput;
        this.redstoneTicks = redstoneTicks;
        this.successCommands = successCommands == null ? "" : successCommands;
        this.failureCommands = failureCommands == null ? "" : failureCommands;
        this.teamWhitelist = teamWhitelist == null ? "" : teamWhitelist;
        this.tagWhitelist = tagWhitelist == null ? "" : tagWhitelist;
        this.onlyNotCompleted = onlyNotCompleted;
        this.bypassAfterComplete = bypassAfterComplete;
        this.clearCompletions = clearCompletions;
        this.bindingMode = "normal";
        this.anomalyInstanceId = "";
        this.anomalyRestoreAmount = 25;
        this.anomalyCooldownSeconds = 0;
        this.anomalyMinCardLevel = 0;
        this.anomalyRequiredDepartment = "科研部门";
    }

    public SaveBinderConfigPacket(BlockPos pos, boolean isBound, QTEType type, int duration, float difficulty, String requiredItemId, int rounds,
                                  boolean redstoneOutput, int redstoneTicks, String successCommands, String failureCommands,
                                  String teamWhitelist, String tagWhitelist, boolean onlyNotCompleted, boolean bypassAfterComplete,
                                  boolean clearCompletions, String bindingMode, String anomalyInstanceId, int anomalyRestoreAmount,
                                  int anomalyCooldownSeconds, int anomalyMinCardLevel, String anomalyRequiredDepartment) {
        this.pos = pos;
        this.isBound = isBound;
        this.type = type;
        this.duration = duration;
        this.difficulty = difficulty;
        this.requiredItemId = requiredItemId == null ? "" : requiredItemId;
        this.rounds = rounds;
        this.redstoneOutput = redstoneOutput;
        this.redstoneTicks = redstoneTicks;
        this.successCommands = successCommands == null ? "" : successCommands;
        this.failureCommands = failureCommands == null ? "" : failureCommands;
        this.teamWhitelist = teamWhitelist == null ? "" : teamWhitelist;
        this.tagWhitelist = tagWhitelist == null ? "" : tagWhitelist;
        this.onlyNotCompleted = onlyNotCompleted;
        this.bypassAfterComplete = bypassAfterComplete;
        this.clearCompletions = clearCompletions;
        this.bindingMode = bindingMode == null || bindingMode.isBlank() ? "normal" : bindingMode;
        this.anomalyInstanceId = anomalyInstanceId == null ? "" : anomalyInstanceId;
        this.anomalyRestoreAmount = Math.max(1, anomalyRestoreAmount);
        this.anomalyCooldownSeconds = Math.max(0, anomalyCooldownSeconds);
        this.anomalyMinCardLevel = Math.max(0, anomalyMinCardLevel);
        this.anomalyRequiredDepartment = anomalyRequiredDepartment == null || anomalyRequiredDepartment.isBlank() ? "科研部门" : anomalyRequiredDepartment;
    }

    public SaveBinderConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.isBound = buf.readBoolean();
        this.type = QTEType.fromId(buf.readInt());
        this.duration = buf.readInt();
        this.difficulty = buf.readFloat();
        this.requiredItemId = buf.readUtf();
        this.rounds = buf.readInt();
        this.redstoneOutput = buf.readBoolean();
        this.redstoneTicks = buf.readInt();
        this.successCommands = buf.readUtf();
        this.failureCommands = buf.readUtf();
        this.teamWhitelist = buf.readUtf();
        this.tagWhitelist = buf.readUtf();
        this.onlyNotCompleted = buf.readBoolean();
        this.bypassAfterComplete = buf.readBoolean();
        this.clearCompletions = buf.readBoolean();
        this.bindingMode = buf.readUtf();
        this.anomalyInstanceId = buf.readUtf();
        this.anomalyRestoreAmount = buf.readInt();
        this.anomalyCooldownSeconds = buf.readInt();
        this.anomalyMinCardLevel = buf.readInt();
        this.anomalyRequiredDepartment = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.isBound);
        buf.writeInt(this.type.ordinal());
        buf.writeInt(this.duration);
        buf.writeFloat(this.difficulty);
        buf.writeUtf(this.requiredItemId);
        buf.writeInt(this.rounds);
        buf.writeBoolean(this.redstoneOutput);
        buf.writeInt(this.redstoneTicks);
        buf.writeUtf(this.successCommands);
        buf.writeUtf(this.failureCommands);
        buf.writeUtf(this.teamWhitelist);
        buf.writeUtf(this.tagWhitelist);
        buf.writeBoolean(this.onlyNotCompleted);
        buf.writeBoolean(this.bypassAfterComplete);
        buf.writeBoolean(this.clearCompletions);
        buf.writeUtf(this.bindingMode);
        buf.writeUtf(this.anomalyInstanceId);
        buf.writeInt(this.anomalyRestoreAmount);
        buf.writeInt(this.anomalyCooldownSeconds);
        buf.writeInt(this.anomalyMinCardLevel);
        buf.writeUtf(this.anomalyRequiredDepartment);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                QTEBlockManager manager = QTEBlockManager.get(player.level());
                if (manager != null) {
                    if (this.isBound) {
                        manager.bindQTE(this.pos, new QTEBlockManager.BoundQTE(
                                this.type, this.duration, this.difficulty, this.requiredItemId, this.rounds,
                                this.redstoneOutput, this.redstoneTicks, this.successCommands, this.failureCommands,
                                this.teamWhitelist, this.tagWhitelist, this.onlyNotCompleted, this.bypassAfterComplete,
                                this.bindingMode, this.anomalyInstanceId, this.anomalyRestoreAmount, this.anomalyCooldownSeconds,
                                this.anomalyMinCardLevel, this.anomalyRequiredDepartment));
                        if (this.clearCompletions) {
                            manager.clearCompletions(this.pos);
                            player.sendSystemMessage(Component.translatable("message.melonstools.qte_completions_reset"));
                        } else {
                            player.sendSystemMessage(Component.translatable("message.melonstools.qte_bound", this.pos.toShortString()));
                        }
                    } else {
                        manager.unbindQTE(this.pos);
                        player.sendSystemMessage(Component.translatable("message.melonstools.qte_unbound", this.pos.toShortString()));
                    }
                    
                    // Sync to all clients
                    List<BlockPos> boundBlocks = new ArrayList<>(manager.getAllBindings().keySet());
                    NetworkManager.INSTANCE.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), new SyncBoundBlocksPacket(boundBlocks));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
