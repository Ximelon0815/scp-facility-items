package com.example.melonstools.idcard.network;

import com.example.melonstools.idcard.block.entity.SCPKeycardReaderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SaveReaderConfigPacket {
    private final BlockPos pos;
    private final int accessIndex;
    private final List<String> departments;
    private final boolean latchMode;
    private final int openTicks;
    private final boolean pairingOnly;

    public SaveReaderConfigPacket(BlockPos pos, int accessIndex, List<String> departments, boolean latchMode, int openTicks, boolean pairingOnly) {
        this.pos = pos;
        this.accessIndex = accessIndex;
        this.departments = departments;
        this.latchMode = latchMode;
        this.openTicks = openTicks;
        this.pairingOnly = pairingOnly;
    }

    public static void encode(SaveReaderConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.accessIndex);
        buf.writeInt(msg.departments.size());
        for (String dept : msg.departments) {
            buf.writeUtf(dept);
        }
        buf.writeBoolean(msg.latchMode);
        buf.writeInt(msg.openTicks);
        buf.writeBoolean(msg.pairingOnly);
    }

    public static SaveReaderConfigPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int accessIndex = buf.readInt();
        int size = buf.readInt();
        List<String> depts = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            depts.add(buf.readUtf());
        }
        boolean latchMode = buf.readBoolean();
        int openTicks = buf.readInt();
        boolean pairingOnly = buf.readBoolean();
        return new SaveReaderConfigPacket(pos, accessIndex, depts, latchMode, openTicks, pairingOnly);
    }

    public static void handle(SaveReaderConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BlockEntity be = player.level().getBlockEntity(msg.pos);
                if (be instanceof com.example.melonstools.idcard.block.entity.SCPKeycardReaderBlockEntity reader) {
                    if (player.hasPermissions(2)) {
                        reader.updateConfig(msg.accessIndex, msg.departments, msg.latchMode, msg.openTicks, msg.pairingOnly);
                    }
                } else if (be instanceof com.example.melonstools.idcard.block.entity.SCPCheckpointKeycardReaderBlockEntity cpReader) {
                    if (player.hasPermissions(2)) {
                        cpReader.updateConfig(msg.accessIndex, msg.departments, msg.latchMode, msg.openTicks, msg.pairingOnly);
                    }
                } else if (be instanceof com.example.melonstools.idcard.block.entity.SCPLockedKeycardReaderBlockEntity lockedReader) {
                    if (player.hasPermissions(2)) {
                        lockedReader.updateConfig(msg.accessIndex, msg.departments, msg.latchMode, msg.openTicks, msg.pairingOnly);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
