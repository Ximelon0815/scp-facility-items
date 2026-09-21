package com.example.melonstools.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncBoundBlocksPacket {
    private final List<BlockPos> blocks;

    public SyncBoundBlocksPacket(List<BlockPos> blocks) {
        this.blocks = blocks;
    }

    public SyncBoundBlocksPacket(FriendlyByteBuf buf) {
        this.blocks = new ArrayList<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            this.blocks.add(buf.readBlockPos());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(blocks.size());
        for (BlockPos pos : blocks) {
            buf.writeBlockPos(pos);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handleSyncBoundBlocks(this);
        });
        ctx.get().setPacketHandled(true);
    }

    public List<BlockPos> getBlocks() {
        return blocks;
    }
}
