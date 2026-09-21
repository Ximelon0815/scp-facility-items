package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端下发全服排行榜(JSON) */
public class SyncLeaderboardPacket {
    private final String json;

    public SyncLeaderboardPacket(String json) {
        this.json = json;
    }

    public SyncLeaderboardPacket(FriendlyByteBuf buf) {
        this.json = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.json);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handleSyncLeaderboard(this);
        });
        ctx.get().setPacketHandled(true);
    }

    public String getJson() {
        return json;
    }
}
