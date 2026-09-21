package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class QTEResultPacket {
    private final int qteId;
    private final boolean success;
    private final float score;

    public QTEResultPacket(int qteId, boolean success, float score) {
        this.qteId = qteId;
        this.success = success;
        this.score = score;
    }

    public QTEResultPacket(FriendlyByteBuf buf) {
        this.qteId = buf.readInt();
        this.success = buf.readBoolean();
        this.score = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(qteId);
        buf.writeBoolean(success);
        buf.writeFloat(score);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Server side handling
            ServerPacketHandler.handleQTEResult(this, ctx.get().getSender());
        });
        ctx.get().setPacketHandled(true);
    }

    public int getQteId() {
        return qteId;
    }

    public boolean isSuccess() {
        return success;
    }

    public float getScore() {
        return score;
    }
}
