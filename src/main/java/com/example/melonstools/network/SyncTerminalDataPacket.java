package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端向客户端下发终端全量数据(JSON 字符串)。
 *
 * <p>payload 约定预留字段：hqTasks / hqTaskEditor / isOp / facilityFund。</p>
 */
public class SyncTerminalDataPacket {
    private final String json;

    public SyncTerminalDataPacket(String json) {
        this.json = json;
    }

    public SyncTerminalDataPacket(FriendlyByteBuf buf) {
        this.json = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.json);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handleSyncTerminalData(this);
        });
        ctx.get().setPacketHandled(true);
    }

    public String getJson() {
        return json;
    }
}
