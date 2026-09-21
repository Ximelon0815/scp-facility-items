package com.example.melonstools.network;

import com.example.melonstools.client.gui.QTEUIManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.core.BlockPos;

public class ClientPacketHandler {
    @OnlyIn(Dist.CLIENT)
    public static void handleStartQTE(StartQTEPacket packet) {
        QTEUIManager.openQTEScreen(packet);
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleOpenBinderUI(OpenBinderUIPacket packet) {
        com.example.melonstools.client.ClientHooks.openBinderScreen(
                packet.getPos(),
                packet.isBound(),
                packet.getType(),
                packet.getDuration(),
                packet.getDifficulty(),
                packet.getRequiredItemId(),
                packet.getRounds(),
                packet.isRedstoneOutput(),
                packet.getRedstoneTicks(),
                packet.getSuccessCommands(),
                packet.getFailureCommands(),
                packet.getTeamWhitelist(),
                packet.getTagWhitelist(),
                packet.isOnlyNotCompleted(),
                packet.isBypassAfterComplete(),
                packet.getCompletedPlayers(),
                packet.getBindingMode(), packet.getAnomalyInstanceId(), packet.getAnomalyRestoreAmount(),
                packet.getAnomalyCooldownSeconds(), packet.getAnomalyMinCardLevel(), packet.getAnomalyRequiredDepartment()
        );
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSyncBoundBlocks(SyncBoundBlocksPacket packet) {
        com.example.melonstools.qte.QTEBlockManager.setClientBoundBlocks(packet.getBlocks());
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSyncPhoneFull(SyncPhoneFullPacket packet) {
        com.example.melonstools.client.PhoneBridgeClient.applyPhoneData(packet.getJson());
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSyncLeaderboard(SyncLeaderboardPacket packet) {
        com.example.melonstools.client.PhoneBridgeClient.applyLeaderboardData(packet.getJson());
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSyncTerminalData(SyncTerminalDataPacket packet) {
        com.example.melonstools.client.TerminalBridgeClient.applyTerminalData(packet.getJson());
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSyncNearbyPlayers(SyncNearbyPlayersPacket packet) {
        // Will be implemented in client subtask
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSyncPhoneData(SyncPhoneDataPacket packet) {
        // Will be implemented in client subtask
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleSyncChatData(SyncChatDataPacket packet) {
        // Will be implemented in client subtask
    }
}
