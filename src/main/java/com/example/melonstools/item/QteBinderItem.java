package com.example.melonstools.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.example.melonstools.qte.SelectionManager;
import com.example.melonstools.qte.QTEBlockManager;
// import com.example.melonstools.network.ModNetworkHandler;
import com.example.melonstools.network.OpenBinderUIPacket;
import com.example.melonstools.qte.QTEType;
import net.minecraftforge.network.PacketDistributor;

public class QteBinderItem extends Item {
    public QteBinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide() && context.getPlayer() instanceof ServerPlayer serverPlayer) {
            SelectionManager.setSelectedBlock(serverPlayer, context.getClickedPos(), context.getLevel().dimension());
            
            QTEBlockManager manager = QTEBlockManager.get(context.getLevel());
            boolean isBound = false;
            QTEType type = QTEType.MASHING;
            int duration = 100;
            float difficulty = 1.0f;
            String requiredItemId = "";
            int rounds = 1;

            boolean redstoneOutput = false;
            int redstoneTicks = 20;
            String successCommands = "";
            String failureCommands = "";
            String teamWhitelist = "";
            String tagWhitelist = "";
            boolean onlyNotCompleted = false;
            boolean bypassAfterComplete = false;
            java.util.List<String> completedPlayers = new java.util.ArrayList<>();
            String bindingMode = "normal";
            String anomalyInstanceId = "";
            int anomalyRestoreAmount = 25;
            int anomalyCooldownSeconds = 0;
            int anomalyMinCardLevel = 0;
            String anomalyRequiredDepartment = "科研部门";

            if (manager != null && manager.hasBoundQTE(context.getClickedPos())) {
                QTEBlockManager.BoundQTE bound = manager.getBoundQTE(context.getClickedPos());
                isBound = true;
                type = bound.type;
                duration = bound.duration;
                difficulty = bound.difficulty;
                requiredItemId = bound.requiredItemId;
                rounds = bound.rounds;
                redstoneOutput = bound.redstoneOutput;
                redstoneTicks = bound.redstoneTicks;
                successCommands = bound.successCommands;
                failureCommands = bound.failureCommands;
                teamWhitelist = bound.teamWhitelist;
                tagWhitelist = bound.tagWhitelist;
                onlyNotCompleted = bound.onlyNotCompleted;
                bypassAfterComplete = bound.bypassAfterComplete;
                bindingMode = bound.bindingMode;
                anomalyInstanceId = bound.anomalyInstanceId;
                anomalyRestoreAmount = bound.anomalyRestoreAmount;
                anomalyCooldownSeconds = bound.anomalyCooldownSeconds;
                anomalyMinCardLevel = bound.anomalyMinCardLevel;
                anomalyRequiredDepartment = bound.anomalyRequiredDepartment;
                if (context.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    completedPlayers = manager.getCompletedPlayerNames(context.getClickedPos(), serverLevel);
                }
            }

            com.example.melonstools.network.NetworkManager.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> serverPlayer),
                new com.example.melonstools.network.OpenBinderUIPacket(context.getClickedPos(), isBound, type, duration, difficulty, requiredItemId, rounds,
                        redstoneOutput, redstoneTicks, successCommands, failureCommands,
                        teamWhitelist, tagWhitelist, onlyNotCompleted, bypassAfterComplete, completedPlayers,
                        bindingMode, anomalyInstanceId, anomalyRestoreAmount, anomalyCooldownSeconds, anomalyMinCardLevel, anomalyRequiredDepartment)
            );
            
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }
}
