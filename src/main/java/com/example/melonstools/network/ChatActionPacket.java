package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChatActionPacket {
    public enum Action {
        CREATE_ROOM, SEND_MESSAGE, LEAVE_ROOM, KICK_MEMBER, RENAME_ROOM, JOIN_ROOM, INVITE_MEMBER, SET_ROOM_AVATAR, DISSOLVE_ROOM, MARK_READ
    }

    private final Action action;
    private final String roomId;
    private final String data; // For CREATE_ROOM it can be room name, for SEND_MESSAGE it's the message
    private final String avatar; // 仅 CREATE_ROOM 使用:房间头像物品注册名(可为空)

    public ChatActionPacket(Action action, String roomId, String data) {
        this(action, roomId, data, "");
    }

    public ChatActionPacket(Action action, String roomId, String data, String avatar) {
        this.action = action;
        this.roomId = roomId;
        this.data = data;
        this.avatar = avatar;
    }

    public ChatActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.roomId = buf.readUtf();
        this.data = buf.readUtf();
        this.avatar = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        buf.writeUtf(this.roomId);
        buf.writeUtf(this.data);
        buf.writeUtf(this.avatar);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPacketHandler.handleChatAction(this, ctx.get().getSender());
        });
        ctx.get().setPacketHandled(true);
    }

    public Action getAction() {
        return action;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getData() {
        return data;
    }

    public String getAvatar() {
        return avatar;
    }
}
