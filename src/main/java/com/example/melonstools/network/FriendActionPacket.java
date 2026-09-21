package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class FriendActionPacket {
    public enum Action {
        ADD, ACCEPT, REJECT, REMOVE
    }

    private final Action action;
    private final UUID targetUuid;

    public FriendActionPacket(Action action, UUID targetUuid) {
        this.action = action;
        this.targetUuid = targetUuid;
    }

    public FriendActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.targetUuid = buf.readUUID();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        buf.writeUUID(this.targetUuid);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPacketHandler.handleFriendAction(this, ctx.get().getSender());
        });
        ctx.get().setPacketHandled(true);
    }

    public Action getAction() {
        return action;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }
}
