package com.example.melonstools.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public class ClientHooks {
    public static void openIdentityCardScreen(ItemStack itemStack) {
        Minecraft.getInstance().setScreen(new com.example.melonstools.idcard.client.screen.IdentityCardScreen(itemStack));
    }

    public static void openKeycardReaderScreen(BlockPos pos, int accessIndex, List<String> requiredDepartments, boolean latchMode, int openTicks, boolean pairingOnly) {
        Minecraft.getInstance().setScreen(new com.example.melonstools.idcard.client.screen.SCPKeycardReaderScreen(pos, accessIndex, requiredDepartments, latchMode, openTicks, pairingOnly));
    }

    public static void openPhoneScreen() {
        // 装有 AUI (ApricityUI) 时用 HTML 界面,否则回退旧空屏
        if (net.minecraftforge.fml.ModList.get().isLoaded("apricityui")) {
            // 难点(2026-08-22): ApricityScreen.init() 里 Document.create() 是同步的
            // (解析+内嵌脚本+首布局,实测 300ms+), 期间 MC 屏幕被清黑 → 打开手机短暂黑屏。
            // 方案: 先做完整预热(创建并移除 Document, 热起 blueprint/样式/字体/脚本缓存),
            // 再 setScreen; 预热耗时发生在游戏画面正常显示时(无黑屏), setScreen 后秒开。
            // 注: 预创建的 Overlay Document 会瞬间闪现, 故创建后立即 remove, 且窗口不在
            // Screen 状态下 Overlay 由持久绘制驱动, 同 tick 移除可忽略闪现。
            try {
                var docs = com.sighs.apricityui.ApricityUI.getDocument(com.example.melonstools.client.PhoneBridgeClient.PHONE_PATH);
                if (docs.isEmpty()) {
                    com.sighs.apricityui.init.Document pre = com.sighs.apricityui.ApricityUI.createDocument(com.example.melonstools.client.PhoneBridgeClient.PHONE_PATH);
                    if (pre != null) {
                        // 阻止 Overlay 绘制, 避免预热文档闪现一帧
                        pre.setManuallyRendered(true);
                        pre.remove();
                    }
                }
            } catch (Throwable ignored) {
            }
            Minecraft.getInstance().setScreen(new com.sighs.apricityui.screen.ApricityScreen("melonstools/phone/index.html"));
            // 客户端先从ID卡写入一份本地基础档案，保证单人世界玩家信息栏可立即显示；
            // 有网络时服务端全量数据到达后由 applyPhoneData 覆盖。
            com.example.melonstools.client.PhoneBridgeClient.ensureLocalPhoneData();
            // 向服务端请求全量手机数据(单人世界无连接则跳过, 用上面的本地档案)
            if (Minecraft.getInstance().getConnection() != null) {
                com.example.melonstools.network.NetworkManager.INSTANCE.sendToServer(new com.example.melonstools.network.RequestPhoneFullPacket());
            }
        } else {
            Minecraft.getInstance().setScreen(new com.example.melonstools.client.gui.PhoneScreen());
        }
    }

    /** 打开终端(AUI Windows 风格电脑界面)。由终端方块右键触发。 */
    public static void openTerminalScreen() {
        if (net.minecraftforge.fml.ModList.get().isLoaded("apricityui")) {
            Minecraft.getInstance().setScreen(new com.example.melonstools.client.gui.TerminalApricityScreen());
            com.example.melonstools.client.TerminalBridgeClient.requestTerminalData();
        } else {
            // 无 AUI 时无界面, 仅提示
            net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e需要 ApricityUI 才能使用终端."), true);
        }
    }

    public static void openBinderScreen(BlockPos pos, boolean isBound, com.example.melonstools.qte.QTEType type, int duration, float difficulty, String requiredItemId, int rounds,
                                        boolean redstoneOutput, int redstoneTicks, String successCommands, String failureCommands,
                                        String teamWhitelist, String tagWhitelist, boolean onlyNotCompleted, boolean bypassAfterComplete,
                                        List<String> completedPlayers, String bindingMode, String anomalyInstanceId, int anomalyRestoreAmount,
                                        int anomalyCooldownSeconds, int anomalyMinCardLevel, String anomalyRequiredDepartment) {
        Minecraft.getInstance().setScreen(new com.example.melonstools.client.gui.QteBinderScreen(
                pos, isBound, type, duration, difficulty, requiredItemId, rounds,
                redstoneOutput, redstoneTicks, successCommands, failureCommands,
                teamWhitelist, tagWhitelist, onlyNotCompleted, bypassAfterComplete, completedPlayers,
                bindingMode, anomalyInstanceId, anomalyRestoreAmount, anomalyCooldownSeconds, anomalyMinCardLevel, anomalyRequiredDepartment));
    }
}