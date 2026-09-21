package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 提交小游戏成绩 */
public class SubmitScorePacket {
    private final String game;
    private final String diff;
    private final int score;

    public SubmitScorePacket(String game, String diff, int score) {
        this.game = game;
        this.diff = diff;
        this.score = score;
    }

    public SubmitScorePacket(FriendlyByteBuf buf) {
        this.game = buf.readUtf();
        this.diff = buf.readUtf();
        this.score = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.game);
        buf.writeUtf(this.diff);
        buf.writeInt(this.score);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPacketHandler.handleSubmitScore(this, ctx.get().getSender());
        });
        ctx.get().setPacketHandled(true);
    }

    public String getGame() {
        return game;
    }

    public String getDiff() {
        return diff;
    }

    public int getScore() {
        return score;
    }
}
