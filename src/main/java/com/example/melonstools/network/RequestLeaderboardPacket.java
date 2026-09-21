package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 请求全服排行榜数据 */
public class RequestLeaderboardPacket {
    public RequestLeaderboardPacket() {
    }

    public RequestLeaderboardPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPacketHandler.handleRequestLeaderboard(ctx.get().getSender());
        });
        ctx.get().setPacketHandled(true);
    }
}
