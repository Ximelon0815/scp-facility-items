package com.example.melonstools.network;

import com.example.melonstools.qte.QTEType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenBinderUIPacket {
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
    private final List<String> completedPlayers;
    private final String bindingMode;
    private final String anomalyInstanceId;
    private final int anomalyRestoreAmount;
    private final int anomalyCooldownSeconds;
    private final int anomalyMinCardLevel;
    private final String anomalyRequiredDepartment;

    public OpenBinderUIPacket(BlockPos pos, boolean isBound, QTEType type, int duration, float difficulty, String requiredItemId, int rounds,
                              boolean redstoneOutput, int redstoneTicks, String successCommands, String failureCommands,
                              String teamWhitelist, String tagWhitelist, boolean onlyNotCompleted, boolean bypassAfterComplete,
                              List<String> completedPlayers) {
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
        this.completedPlayers = completedPlayers == null ? new ArrayList<>() : completedPlayers;
        this.bindingMode = "normal";
        this.anomalyInstanceId = "";
        this.anomalyRestoreAmount = 25;
        this.anomalyCooldownSeconds = 0;
        this.anomalyMinCardLevel = 0;
        this.anomalyRequiredDepartment = "科研部门";
    }

    public OpenBinderUIPacket(BlockPos pos, boolean isBound, QTEType type, int duration, float difficulty, String requiredItemId, int rounds,
                              boolean redstoneOutput, int redstoneTicks, String successCommands, String failureCommands,
                              String teamWhitelist, String tagWhitelist, boolean onlyNotCompleted, boolean bypassAfterComplete,
                              List<String> completedPlayers, String bindingMode, String anomalyInstanceId, int anomalyRestoreAmount,
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
        this.completedPlayers = completedPlayers == null ? new ArrayList<>() : completedPlayers;
        this.bindingMode = bindingMode == null || bindingMode.isBlank() ? "normal" : bindingMode;
        this.anomalyInstanceId = anomalyInstanceId == null ? "" : anomalyInstanceId;
        this.anomalyRestoreAmount = Math.max(1, anomalyRestoreAmount);
        this.anomalyCooldownSeconds = Math.max(0, anomalyCooldownSeconds);
        this.anomalyMinCardLevel = Math.max(0, anomalyMinCardLevel);
        this.anomalyRequiredDepartment = anomalyRequiredDepartment == null || anomalyRequiredDepartment.isBlank() ? "科研部门" : anomalyRequiredDepartment;
    }

    public OpenBinderUIPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.isBound = buf.readBoolean();
        if (this.isBound) {
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
            int count = buf.readInt();
            this.completedPlayers = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                this.completedPlayers.add(buf.readUtf());
            }
            this.bindingMode = buf.readUtf();
            this.anomalyInstanceId = buf.readUtf();
            this.anomalyRestoreAmount = buf.readInt();
            this.anomalyCooldownSeconds = buf.readInt();
            this.anomalyMinCardLevel = buf.readInt();
            this.anomalyRequiredDepartment = buf.readUtf();
        } else {
            this.type = QTEType.MASHING;
            this.duration = 100;
            this.difficulty = 1.0f;
            this.requiredItemId = "";
            this.rounds = 1;
            this.redstoneOutput = false;
            this.redstoneTicks = 20;
            this.successCommands = "";
            this.failureCommands = "";
            this.teamWhitelist = "";
            this.tagWhitelist = "";
            this.onlyNotCompleted = false;
            this.bypassAfterComplete = false;
            this.completedPlayers = new ArrayList<>();
            this.bindingMode = "normal";
            this.anomalyInstanceId = "";
            this.anomalyRestoreAmount = 25;
            this.anomalyCooldownSeconds = 0;
            this.anomalyMinCardLevel = 0;
            this.anomalyRequiredDepartment = "科研部门";
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.isBound);
        if (this.isBound) {
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
            buf.writeInt(this.completedPlayers.size());
            for (String name : this.completedPlayers) {
                buf.writeUtf(name);
            }
            buf.writeUtf(this.bindingMode);
            buf.writeUtf(this.anomalyInstanceId);
            buf.writeInt(this.anomalyRestoreAmount);
            buf.writeInt(this.anomalyCooldownSeconds);
            buf.writeInt(this.anomalyMinCardLevel);
            buf.writeUtf(this.anomalyRequiredDepartment);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handleOpenBinderUI(this);
        });
        ctx.get().setPacketHandled(true);
    }
    
    public BlockPos getPos() { return pos; }
    public boolean isBound() { return isBound; }
    public QTEType getType() { return type; }
    public int getDuration() { return duration; }
    public float getDifficulty() { return difficulty; }
    public String getRequiredItemId() { return requiredItemId; }
    public int getRounds() { return rounds; }
    public boolean isRedstoneOutput() { return redstoneOutput; }
    public int getRedstoneTicks() { return redstoneTicks; }
    public String getSuccessCommands() { return successCommands; }
    public String getFailureCommands() { return failureCommands; }
    public String getTeamWhitelist() { return teamWhitelist; }
    public String getTagWhitelist() { return tagWhitelist; }
    public boolean isOnlyNotCompleted() { return onlyNotCompleted; }
    public boolean isBypassAfterComplete() { return bypassAfterComplete; }
    public List<String> getCompletedPlayers() { return completedPlayers; }
    public String getBindingMode() { return bindingMode; }
    public String getAnomalyInstanceId() { return anomalyInstanceId; }
    public int getAnomalyRestoreAmount() { return anomalyRestoreAmount; }
    public int getAnomalyCooldownSeconds() { return anomalyCooldownSeconds; }
    public int getAnomalyMinCardLevel() { return anomalyMinCardLevel; }
    public String getAnomalyRequiredDepartment() { return anomalyRequiredDepartment; }
}
