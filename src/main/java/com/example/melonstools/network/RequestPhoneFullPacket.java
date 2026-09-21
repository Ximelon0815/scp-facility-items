package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端打开手机时请求全量数据 */
public class RequestPhoneFullPacket {
    public RequestPhoneFullPacket() {
    }

    public RequestPhoneFullPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPacketHandler.handleRequestPhoneFull(ctx.get().getSender());
        });
        ctx.get().setPacketHandled(true);
    }
}
