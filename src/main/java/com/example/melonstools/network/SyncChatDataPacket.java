package com.example.melonstools.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncChatDataPacket {
    private final CompoundTag nbt;

    public SyncChatDataPacket(CompoundTag nbt) {
        this.nbt = nbt;
    }

    public SyncChatDataPacket(FriendlyByteBuf buf) {
        this.nbt = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(this.nbt);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handleSyncChatData(this);
        });
        ctx.get().setPacketHandled(true);
    }

    public CompoundTag getNbt() {
        return nbt;
    }
}
