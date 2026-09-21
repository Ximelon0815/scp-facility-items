package com.example.melonstools.network;

import com.example.melonstools.qte.QTEType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StartQTEPacket {
    private final int qteId; // UUID or unique ID for this QTE instance
    private final QTEType type;
    private final int durationTicks;
    private final float difficulty;

    public StartQTEPacket(int qteId, QTEType type, int durationTicks, float difficulty) {
        this.qteId = qteId;
        this.type = type;
        this.durationTicks = durationTicks;
        this.difficulty = difficulty;
    }

    public StartQTEPacket(FriendlyByteBuf buf) {
        this.qteId = buf.readInt();
        this.type = QTEType.fromId(buf.readInt());
        this.durationTicks = buf.readInt();
        this.difficulty = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(qteId);
        buf.writeInt(type.ordinal());
        buf.writeInt(durationTicks);
        buf.writeFloat(difficulty);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Client side handling
            ClientPacketHandler.handleStartQTE(this);
        });
        ctx.get().setPacketHandled(true);
    }

    public int getQteId() {
        return qteId;
    }

    public QTEType getType() {
        return type;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public float getDifficulty() {
        return difficulty;
    }
}
