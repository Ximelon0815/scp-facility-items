package com.example.melonstools.qte;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class QTELogicManager {
    // Basic server tracking
    private static final Map<UUID, ActiveQTE> activeQTEs = new HashMap<>();
    private static final Map<UUID, net.minecraft.core.BlockPos> bypassedPlayers = new HashMap<>();
    private static int nextQteId = 1;

    public static void startQTE(ServerPlayer player, QTEType type, int durationTicks, float difficulty) {
        startQTEBlock(player, type, durationTicks, difficulty, null, null, null);
    }

    public static void startQTEBlock(ServerPlayer player, QTEType type, int durationTicks, float difficulty, 
                                     net.minecraft.core.BlockPos targetPos, 
                                     net.minecraft.world.InteractionHand hand, 
                                     net.minecraft.world.phys.BlockHitResult hitResult, int rounds) {
        int qteId = nextQteId++;
        int actualRounds = type == QTEType.SEQUENCE ? 1 : Math.max(1, rounds);
        activeQTEs.put(player.getUUID(), new ActiveQTE(qteId, type, player.server.getTickCount() + durationTicks + 20, targetPos, hand, hitResult, actualRounds, durationTicks, difficulty));
        
        com.example.melonstools.network.NetworkManager.INSTANCE.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.example.melonstools.network.StartQTEPacket(qteId, type, durationTicks, difficulty)
        );
    }

    public static void startQTEBlock(ServerPlayer player, QTEType type, int durationTicks, float difficulty, 
                                     net.minecraft.core.BlockPos targetPos, 
                                     net.minecraft.world.InteractionHand hand, 
                                     net.minecraft.world.phys.BlockHitResult hitResult) {
        startQTEBlock(player, type, durationTicks, difficulty, targetPos, hand, hitResult, 1);
    }

    public static void handleClientResult(ServerPlayer player, int qteId, boolean success, float score) {
        ActiveQTE active = activeQTEs.get(player.getUUID());
        if (active != null && active.qteId == qteId) {
            if (success) {
                active.roundsRemaining--;
                if (active.roundsRemaining > 0) {
                    // Start next round
                    int newQteId = nextQteId++;
                    active.qteId = newQteId;
                    active.timeoutTick = player.server.getTickCount() + active.originalDurationTicks + 20;
                    
                    com.example.melonstools.network.NetworkManager.INSTANCE.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new com.example.melonstools.network.StartQTEPacket(newQteId, active.type, active.originalDurationTicks, active.originalDifficulty)
                    );
                    return;
                }
            }

            // Valid QTE completed or failed
            activeQTEs.remove(player.getUUID());
            
            if (success) {
                player.sendSystemMessage(Component.literal("QTE Success! Score: " + score));

                // 记录完成者 + 触发完成后动作（红石 / 指令）
                if (active.targetPos != null) {
                    ServerLevel level = player.serverLevel();
                    QTEBlockManager manager = QTEBlockManager.get(level);
                    if (manager != null) {
                        QTEBlockManager.BoundQTE bound = manager.getBoundQTE(active.targetPos);
                        if (bound != null) {
                            manager.recordCompletion(active.targetPos, player.getUUID());
                            if ("anomaly_maintenance".equals(bound.bindingMode) && !bound.anomalyInstanceId.isBlank()) {
                                com.example.melonstools.anomaly.AnomalySavedData.get(level).maintenanceSuccess(bound.anomalyInstanceId, bound.anomalyRestoreAmount);
                                com.example.melonstools.data.HqTaskSavedData.recordEvent(level, com.example.melonstools.data.HqTaskSavedData.EVENT_ANOMALY_MAINTENANCE);
                                player.sendSystemMessage(Component.literal("§a异常物维护完成，稳定度已恢复。"));
                            }
                            if (bound.redstoneOutput) {
                                QTEBlockManager.startRedstonePulse(level, active.targetPos, bound.redstoneTicks);
                            }
                            runCommands(player, bound.successCommands, score, active.targetPos);
                        }
                    }
                }
                
                // If this QTE was triggered by a block, trigger the block action now!
                if (active.targetPos != null && active.hitResult != null && active.hand != null) {
                    bypassedPlayers.put(player.getUUID(), active.targetPos);
                    player.gameMode.useItemOn(player, player.serverLevel(), player.getItemInHand(active.hand), active.hand, active.hitResult);
                }
            } else {
                player.sendSystemMessage(Component.literal("QTE Failed!"));

                // 失败指令
                if (active.targetPos != null) {
                    QTEBlockManager manager = QTEBlockManager.get(player.serverLevel());
                    if (manager != null) {
                        QTEBlockManager.BoundQTE bound = manager.getBoundQTE(active.targetPos);
                        if (bound != null) {
                            runCommands(player, bound.failureCommands, score, active.targetPos);
                        }
                    }
                }
            }
        }
    }

    /** 以控制台权限（等级 4）执行配置的指令，支持占位符替换。 */
    private static void runCommands(ServerPlayer player, String commands, float score, BlockPos pos) {
        if (commands == null || commands.trim().isEmpty()) return;
        MinecraftServer server = player.getServer();
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        for (String raw : commands.split("[;\n]")) {
            String cmd = raw.trim();
            if (cmd.isEmpty()) continue;
            cmd = cmd.replace("%player%", player.getName().getString())
                    .replace("%uuid%", player.getUUID().toString())
                    .replace("%score%", String.format("%.1f", score))
                    .replace("%pos%", pos.getX() + " " + pos.getY() + " " + pos.getZ())
                    .replace("%x%", String.valueOf(pos.getX()))
                    .replace("%y%", String.valueOf(pos.getY()))
                    .replace("%z%", String.valueOf(pos.getZ()));
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            server.getCommands().performPrefixedCommand(source, cmd);
        }
    }

    public static void cleanUpPlayer(ServerPlayer player) {
        activeQTEs.remove(player.getUUID());
        bypassedPlayers.remove(player.getUUID());
    }

    public static void onBlockInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            // Check if player is holding QTE_BINDER, if so, don't trigger the QTE, let the tool select
            if (player.getMainHandItem().is(com.example.melonstools.item.ModItems.QTE_BINDER.get())) {
                return;
            }

            // Check if this interaction is bypassed (because player just succeeded QTE)
            if (event.getPos().equals(bypassedPlayers.get(player.getUUID()))) {
                bypassedPlayers.remove(player.getUUID());
                return; // Let vanilla handle opening the chest/door
            }

            QTEBlockManager manager = QTEBlockManager.get(event.getLevel());
            if (manager != null) {
                QTEBlockManager.BoundQTE bound = manager.getBoundQTE(event.getPos());
                if (bound != null) {
                    // 已完成者可跳过 QTE（直接放行原方块交互）
                    if (bound.bypassAfterComplete && bound.completedPlayers.contains(player.getUUID().toString())) {
                        return;
                    }

                    event.setCanceled(true); // Stop default block interaction
                    
                    // Check required item
                    if (bound.requiredItemId != null && !bound.requiredItemId.trim().isEmpty()) {
                        net.minecraft.resources.ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());
                        if (itemId == null || !itemId.toString().equals(bound.requiredItemId.trim())) {
                            player.displayClientMessage(Component.literal(""), true);
                            return; // Do not start QTE
                        }
                    }

                    // ===== 异常物维护点权限 =====
                    if ("anomaly_maintenance".equals(bound.bindingMode)) {
                        net.minecraft.world.item.ItemStack card = com.example.melonstools.utils.PhoneUtils.findIdentityCard(player);
                        String dept = card.isEmpty() ? "" : com.example.melonstools.idcard.item.custom.IdentityCardItem.getCardDepartment(card);
                        int level = card.isEmpty() ? 0 : com.example.melonstools.idcard.item.custom.IdentityCardItem.getCardLevel(card);
                        if (!bound.anomalyRequiredDepartment.equals(dept) || level < bound.anomalyMinCardLevel) {
                            player.displayClientMessage(Component.literal("§c需要科研部门身份卡权限才能维护异常物。"), true);
                            return;
                        }
                    }

                    // ===== 人群交互机制 =====
                    // 队伍白名单
                    if (!isWhitelisted(bound.teamWhitelist, player.getTeam() == null ? null : player.getTeam().getName())) {
                        player.displayClientMessage(Component.translatable("message.melonstools.qte_team_only"), true);
                        return;
                    }
                    // Tag 白名单
                    if (!hasAnyTag(bound.tagWhitelist, player.getTags())) {
                        player.displayClientMessage(Component.translatable("message.melonstools.qte_tag_only"), true);
                        return;
                    }
                    // 仅未完成者可触发
                    if (bound.onlyNotCompleted && bound.completedPlayers.contains(player.getUUID().toString())) {
                        player.displayClientMessage(Component.translatable("message.melonstools.qte_already_completed"), true);
                        return;
                    }
                    
                    float finalDifficulty = bound.difficulty;
                    
                    // ID Card Difficulty Adjustment
                    int cardLevel = 0;
                    net.minecraft.world.item.ItemStack handStack = player.getMainHandItem();
                    if (handStack.getItem() instanceof com.example.melonstools.idcard.item.custom.IdentityCardItem) {
                        cardLevel = com.example.melonstools.idcard.item.custom.IdentityCardItem.getCardLevel(handStack);
                    } else {
                        java.util.Optional<top.theillusivec4.curios.api.SlotResult> curioResult = top.theillusivec4.curios.api.CuriosApi.getCuriosHelper().findFirstCurio(player, com.example.melonstools.idcard.item.ModItems.IDENTITY_CARD.get());
                        if (curioResult.isPresent()) {
                            cardLevel = com.example.melonstools.idcard.item.custom.IdentityCardItem.getCardLevel(curioResult.get().stack());
                        }
                    }

                    if (cardLevel > 0) {
                        switch (cardLevel) {
                            case 5: finalDifficulty = 1.0f; break; // Level 5 -> Difficulty 1
                            case 4: finalDifficulty = Math.min(bound.difficulty, 2.0f); break; // Level 4 -> Difficulty 1-2
                            case 3: finalDifficulty = Math.min(Math.max(bound.difficulty, 2.0f), 3.0f); break; // Level 3 -> Difficulty 2-3
                            case 2: finalDifficulty = Math.min(Math.max(bound.difficulty, 3.0f), 4.0f); break; // Level 2 -> Difficulty 3-4
                            case 1: finalDifficulty = Math.max(bound.difficulty, 4.0f); break; // Level 1 -> Difficulty 4-5
                        }
                    }

                    startQTEBlock(player, bound.type, bound.duration, finalDifficulty, event.getPos(), event.getHand(), event.getHitVec(), bound.rounds);
                }
            }
        }
    }

    public static void tickServer() {
        long currentTick = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null ? 
                           net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount() : 0;
        
        if (currentTick == 0) return;

        activeQTEs.entrySet().removeIf(entry -> {
            ActiveQTE active = entry.getValue();
            if (currentTick > active.timeoutTick) {
                ServerPlayer player = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    player.sendSystemMessage(Component.literal("QTE Timed Out/Failed!"));
                }
                return true;
            }
            return false;
        });
    }
    
    /** 检查值是否在逗号分隔的白名单中（空名单表示不限制）。 */
    private static boolean isWhitelisted(String whitelist, String value) {
        if (whitelist == null || whitelist.trim().isEmpty()) return true;
        if (value == null) return false;
        for (String entry : whitelist.split(",")) {
            if (entry.trim().equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    /** 检查玩家标签是否命中逗号分隔的 Tag 白名单（空名单表示不限制）。 */
    private static boolean hasAnyTag(String whitelist, Set<String> tags) {
        if (whitelist == null || whitelist.trim().isEmpty()) return true;
        for (String entry : whitelist.split(",")) {
            if (tags.contains(entry.trim())) return true;
        }
        return false;
    }

    private static class ActiveQTE {
        int qteId;
        QTEType type;
        long timeoutTick;
        net.minecraft.core.BlockPos targetPos;
        net.minecraft.world.InteractionHand hand;
        net.minecraft.world.phys.BlockHitResult hitResult;

        int roundsRemaining;
        int originalDurationTicks;
        float originalDifficulty;

        ActiveQTE(int qteId, QTEType type, long timeoutTick, net.minecraft.core.BlockPos targetPos, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult, int roundsRemaining, int originalDurationTicks, float originalDifficulty) {
            this.qteId = qteId;
            this.type = type;
            this.timeoutTick = timeoutTick;
            this.targetPos = targetPos;
            this.hand = hand;
            this.hitResult = hitResult;
            this.roundsRemaining = roundsRemaining;
            this.originalDurationTicks = originalDurationTicks;
            this.originalDifficulty = originalDifficulty;
        }
    }
}
