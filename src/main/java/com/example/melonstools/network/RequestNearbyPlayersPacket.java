package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestNearbyPlayersPacket {
    public RequestNearbyPlayersPacket() {
    }

    public RequestNearbyPlayersPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPacketHandler.handleRequestNearbyPlayers(this, ctx.get().getSender());
        });
        ctx.get().setPacketHandled(true);
    }
}
