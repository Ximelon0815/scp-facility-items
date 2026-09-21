package com.example.melonstools.client;

import com.example.melonstools.MelonsTools;
import com.example.melonstools.network.NetworkManager;
import com.example.melonstools.network.SyncTerminalDataPacket;
import com.example.melonstools.network.TerminalActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** 终端页面与 Minecraft 之间的数据桥接 */
@Mod.EventBusSubscriber(modid = MelonsTools.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TerminalBridgeClient {

    public static final String TERMINAL_PATH = "melonstools/terminal/index.html";
    /** 终端 JSON 中总部任务窗口/编辑器所需字段名；页面结构由 C 阶段接入。 */
    public static final String FIELD_HQ_TASKS = "hqTasks";
    public static final String FIELD_HQ_TASK_EDITOR = "hqTaskEditor";
    public static final String FIELD_IS_OP = "isOp";
    private static int tickCounter = 0;

    /** 服务端数据下发: 写入页面 #terminalData 元素, 页面 JS 轮询读取 */
    public static void applyTerminalData(String json) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        List<Document> docs = com.sighs.apricityui.ApricityUI.getDocument(TERMINAL_PATH);
        for (Document doc : docs) {
            try {
                Element el = doc.getElementById("terminalData");
                if (el != null) {
                    el.setTextContent(json);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        List<Document> docs = com.sighs.apricityui.ApricityUI.getDocument(TERMINAL_PATH);
        if (docs.isEmpty()) return;
        tickCounter++;
        // 每 10 tick 低频处理时钟 + 动作队列轮询, 避免每 tick 扫 DOM 压 AUI 渲染线程
        if (tickCounter % 10 != 0) return;
        for (int i = 0; i < docs.size(); i++) {
            try {
                Document doc = docs.get(i);
                updateClock(doc);
                Element queue = doc.getElementById("terminalActionQueue");
                if (queue == null) continue;
                String value = queue.getValue();
                if (value == null || value.isBlank()) continue;
                queue.setValue("");
                handleActions(value);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void updateClock(Document doc) {
        try {
            java.time.LocalTime now = java.time.LocalTime.now();
            String t = String.format(java.util.Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute());
            Element clockEl = doc.getElementById("clockText");
            if (clockEl != null && !t.equals(clockEl.getTextContent())) {
                clockEl.setTextContent(t);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 解析页面动作并发送对应网络包 */
    private static void handleActions(String json) {
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            if (arr.size() == 0) return;
            // 批量打包转发给服务端处理(任务编辑等)
            NetworkManager.INSTANCE.sendToServer(new TerminalActionPacket(json));
        } catch (Throwable ignored) {
        }
    }

    /** 终端打开时向服务端请求全量数据 */
    public static void requestTerminalData() {
        net.minecraft.client.Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getConnection() != null) {
            NetworkManager.INSTANCE.sendToServer(new TerminalActionPacket("[{\"t\":\"terminal_request\"}]"));
        }
    }
}
