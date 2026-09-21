package com.example.melonstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class SyncNearbyPlayersPacket {
    private final Map<UUID, String> nearbyPlayers;

    public SyncNearbyPlayersPacket(Map<UUID, String> nearbyPlayers) {
        this.nearbyPlayers = nearbyPlayers;
    }

    public SyncNearbyPlayersPacket(FriendlyByteBuf buf) {
        this.nearbyPlayers = new HashMap<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            this.nearbyPlayers.put(buf.readUUID(), buf.readUtf());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.nearbyPlayers.size());
        for (Map.Entry<UUID, String> entry : this.nearbyPlayers.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handleSyncNearbyPlayers(this);
        });
        ctx.get().setPacketHandled(true);
    }

    public Map<UUID, String> getNearbyPlayers() {
        return nearbyPlayers;
    }
}
