package com.example.melonstools.qte;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
    private static final Map<UUID, SelectionData> selections = new HashMap<>();

    public static void setSelectedBlock(ServerPlayer player, BlockPos pos, ResourceKey<Level> dimension) {
        selections.put(player.getUUID(), new SelectionData(pos, dimension));
    }

    public static void clearSelection(ServerPlayer player) {
        selections.remove(player.getUUID());
    }

    public static SelectionData getSelection(ServerPlayer player) {
        return selections.get(player.getUUID());
    }

    public static class SelectionData {
        private final BlockPos pos;
        private final ResourceKey<Level> dimension;

        public SelectionData(BlockPos pos, ResourceKey<Level> dimension) {
            this.pos = pos;
            this.dimension = dimension;
        }

        public BlockPos getPos() {
            return pos;
        }

        public ResourceKey<Level> getDimension() {
            return dimension;
        }
    }
}
