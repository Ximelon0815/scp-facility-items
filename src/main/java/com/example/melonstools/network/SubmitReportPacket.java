package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 个人终端提交实验报告：仅传最小必要字段。 */
public class SubmitReportPacket {
    private final String anomalyInstanceId;
    private final String title;
    private final String content;

    public SubmitReportPacket(String anomalyInstanceId, String title, String content) {
        this.anomalyInstanceId = anomalyInstanceId;
        this.title = title;
        this.content = content;
    }

    public SubmitReportPacket(FriendlyByteBuf buf) {
        this.anomalyInstanceId = buf.readUtf();
        this.title = buf.readUtf();
        this.content = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.anomalyInstanceId == null ? "" : this.anomalyInstanceId);
        buf.writeUtf(this.title == null ? "" : this.title);
        buf.writeUtf(this.content == null ? "" : this.content);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ServerPacketHandler.handleSubmitReport(this, ctx.get().getSender()));
        ctx.get().setPacketHandled(true);
    }

    public String getAnomalyInstanceId() {
        return anomalyInstanceId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }
}
