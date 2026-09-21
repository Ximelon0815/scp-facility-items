package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 领取每日任务奖励 */
public class ClaimTaskPacket {
    private final String taskId;

    public ClaimTaskPacket(String taskId) {
        this.taskId = taskId;
    }

    public ClaimTaskPacket(FriendlyByteBuf buf) {
        this.taskId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.taskId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPacketHandler.handleClaimTask(this, ctx.get().getSender());
        });
        ctx.get().setPacketHandled(true);
    }

    public String getTaskId() {
        return taskId;
    }
}
