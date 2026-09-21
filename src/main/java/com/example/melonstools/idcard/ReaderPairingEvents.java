package com.example.melonstools.idcard;

import com.example.melonstools.MelonsTools;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 螺丝刀配对门锁：
 * - 左键：一对一配对（重新配对替换旧配对；对同一门锁再左键一次解除全部配对）。
 * - 右键：多选配对（会话式）。右键起点门锁开始；右键目标门锁依次追加配对（每个门锁最多 5 个配对）；
 *   连续右键两次同一目标结束多选；再次右键起点门锁则取消（撤销本次会话添加的配对）。
 * 右键会被取消，避免触发门锁的配置页面或刷卡逻辑。
 */
@Mod.EventBusSubscriber(modid = MelonsTools.MODID)
public class ReaderPairingEvents {
    private static final String PAIRING_KEY = "melonstools_pairing";
    private static final int MAX_PARTNERS = 5;

    /** 右键多选配对会话（服务端按玩家记录，登出时清理）。 */
    private static final Map<UUID, MultiSession> sessions = new HashMap<>();

    private static class MultiSession {
        BlockPos source;
        BlockPos lastTarget;
        final Set<BlockPos> targets = new HashSet<>();
    }

    // ===================== 左键：一对一配对 =====================

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) return; // 仅 OP 可配对，防止误操作/恶意篡改

        ItemStack stack = player.getMainHandItem();
        if (!stack.is(com.example.melonstools.idcard.item.ModItems.SCREWDRIVER.get())) return;

        BlockEntity be = ReaderPairingHelper.getReaderAt(event.getLevel(), event.getPos());
        if (be == null) return;

        // 使用螺丝刀左键门锁：取消方块破坏
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        CompoundTag data = player.getPersistentData();
        if (data.contains(PAIRING_KEY)) {
            CompoundTag sel = data.getCompound(PAIRING_KEY);
            BlockPos first = new BlockPos(sel.getInt("x"), sel.getInt("y"), sel.getInt("z"));
            data.remove(PAIRING_KEY);

            if (first.equals(event.getPos())) {
                // 对同一个门锁再左键一次 → 解除配对
                ReaderPairingHelper.unpairReader(be);
                player.displayClientMessage(Component.translatable("message.melonstools.reader_pairing_unpaired"), true);
            } else {
                BlockEntity firstBE = ReaderPairingHelper.getReaderAt(event.getLevel(), first);
                if (firstBE == null) {
                    player.displayClientMessage(Component.translatable("message.melonstools.reader_pairing_lost", first.toShortString()), true);
                } else {
                    // 先提示已选择第二个门锁，再完成配对（一对一替换）
                    player.displayClientMessage(Component.translatable("message.melonstools.reader_pairing_select", event.getPos().toShortString()), true);
                    ReaderPairingHelper.pairReaders(firstBE, be);
                    player.displayClientMessage(Component.translatable("message.melonstools.reader_pairing_success", first.toShortString(), event.getPos().toShortString()), false);
                }
            }
        } else {
            CompoundTag sel = new CompoundTag();
            sel.putInt("x", event.getPos().getX());
            sel.putInt("y", event.getPos().getY());
            sel.putInt("z", event.getPos().getZ());
            data.put(PAIRING_KEY, sel);
            player.displayClientMessage(Component.translatable("message.melonstools.reader_pairing_select", event.getPos().toShortString()), true);
        }
    }

    // ===================== 右键：多选配对（会话式） =====================

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) return; // 仅 OP 可配对

        ItemStack stack = player.getMainHandItem();
        if (!stack.is(com.example.melonstools.idcard.item.ModItems.SCREWDRIVER.get())) return;

        BlockEntity be = ReaderPairingHelper.getReaderAt(event.getLevel(), event.getPos());
        if (be == null) return;

        // 螺丝刀右键：取消方块交互，避免触发配置页面/刷卡逻辑
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        MultiSession session = sessions.get(player.getUUID());
        if (session == null) {
            // 开始多选会话：记录起点门锁
            session = new MultiSession();
            session.source = event.getPos();
            sessions.put(player.getUUID(), session);
            player.displayClientMessage(Component.translatable("message.melonstools.reader_pairing_multi_start", event.getPos().toShortString()), true);
            return;
        }

        BlockPos pos = event.getPos();
        if (pos.equals(session.source)) {
            // 再次右键起点门锁 → 取消（撤销本次会话添加的配对）
            for (BlockPos t : new HashSet<>(session.targets)) {
                BlockEntity targetBE = ReaderPairingHelper.getReaderAt(event.getLevel(), t);
                if (targetBE != null) {
                    ReaderPairingHelper.removePair(be, targetBE);
                }
            }
            sessions.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.melonstools.reader_pairing_multi_cancel"), true);
            return;
        }

        if (pos.equals(session.lastTarget)) {
            // 连续右键两次同一目标 → 确认并结束多选
            sessions.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.melonstools.reader_pairing_multi_done", session.source.toShortString()), true);
            return;
        }

        // 新目标：尝试追加配对
        BlockEntity sourceBE = ReaderPairingHelper.getReaderAt(event.getLevel(), session.source);
        if (sourceBE == null) {
            sessions.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.melonstools.reader_pairing_lost", session.source.toShortString()), true);
            return;
        }

        if (ReaderPairingHelper.isPairedTo(sourceBE, pos)) {
            // 该目标已在之前配对过：仅设为"最后点击"，用于连续两次右键结束多选
            session.lastTarget = pos;
            return;
        }

        // 每个门锁最多连接 MAX_PARTNERS 个配对；达到上限则撤销本次会话并结束
        if (ReaderPairingHelper.getPairedPositions(sourceBE).size() >= MAX_PARTNERS
                || ReaderPairingHelper.getPairedPositions(be).size() >= MAX_PARTNERS) {
            for (BlockPos t : new HashSet<>(session.targets)) {
                BlockEntity targetBE = ReaderPairingHelper.getReaderAt(event.getLevel(), t);
                if (targetBE != null) {
                    ReaderPairingHelper.removePair(sourceBE, targetBE);
                }
            }
            sessions.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.melonstools.reader_pairing_limit_cancel"), true);
            return;
        }

        ReaderPairingHelper.addPair(sourceBE, be);
        session.targets.add(pos);
        session.lastTarget = pos;
        player.displayClientMessage(Component.translatable("message.melonstools.reader_pairing_multi_add", session.source.toShortString(), pos.toShortString()), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sessions.remove(player.getUUID());
        }
    }
}
