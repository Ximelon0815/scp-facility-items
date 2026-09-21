package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 终端页面动作(JSON 数组字符串)→ 服务端处理。
 *
 * <p>B 阶段新增总部任务动作壳：hq_task_save/delete/complete/fail/fill；服务端统一 OP gate。</p>
 */
public class TerminalActionPacket {
    private final String json;

    public TerminalActionPacket(String json) {
        this.json = json;
    }

    public TerminalActionPacket(FriendlyByteBuf buf) {
        this.json = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.json);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPacketHandler.handleTerminalAction(this, ctx.get().getSender());
        });
        ctx.get().setPacketHandled(true);
    }

    public String getJson() {
        return json;
    }
}
