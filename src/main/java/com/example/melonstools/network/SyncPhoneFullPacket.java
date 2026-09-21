package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端向客户端下发手机全量数据(JSON 字符串) */
public class SyncPhoneFullPacket {
    private final String json;

    public SyncPhoneFullPacket(String json) {
        this.json = json;
    }

    public SyncPhoneFullPacket(FriendlyByteBuf buf) {
        this.json = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.json);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handleSyncPhoneFull(this);
        });
        ctx.get().setPacketHandled(true);
    }

    public String getJson() {
        return json;
    }
}
