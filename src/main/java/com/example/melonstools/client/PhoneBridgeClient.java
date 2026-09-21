package com.example.melonstools.client;

import com.example.melonstools.MelonsTools;
import com.example.melonstools.network.ChatActionPacket;
import com.example.melonstools.network.ClaimTaskPacket;
import com.example.melonstools.network.FriendActionPacket;
import com.example.melonstools.network.NetworkManager;
import com.example.melonstools.network.RequestLeaderboardPacket;
import com.example.melonstools.network.RequestNearbyPlayersPacket;
import com.example.melonstools.network.SubmitReportPacket;
import com.example.melonstools.network.SubmitScorePacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/** 手机页面与 Minecraft 之间的数据桥接 */
@Mod.EventBusSubscriber(modid = MelonsTools.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PhoneBridgeClient {

    public static final String PHONE_PATH = "melonstools/phone/index.html";

    /**
     * 内容缩放比例(与 index.html 的 aui-viewport zoom=0.667 对应,内容缩到 2/3)。
     * 难点(2026-08-21): AUI 的 States 是进程级静态缓存,页面首次创建后 meta 的 zoom
     * 改动不会生效(updateSpec 用旧 zoom clamp,不重置为 initialZoom),导致 viewport
     * 仍是 640x360、tablet 750x450 整体放大。必须 Java 侧强制 setViewportZoom 覆盖。
     */
    public static final double PHONE_ZOOM = 0.667d;

    private static int tickCounter = 0;
    private static boolean phoneWarmupDone = false;

    /**
     * 预热手机页面(手持手机物品时提前执行, 避免打开 Screen 时同步解析阻塞导致黑屏)。
     * 难点(2026-08-22): ApricityScreen.init() 里 Document.create() 是同步的(解析+内嵌脚本
     * +首布局, 实测 300ms+), 期间 MC 屏幕是黑的。手持手机时先跑一次预热, 让模板 blueprint、
     * 样式/字体缓存、脚本编译全部就绪; 真正打开时第二次 create 大幅加快, 黑屏缩短到无感。
     */
    public static void warmupPhone() {
        if (phoneWarmupDone) return;
        try {
            // 预热模板解析 blueprint(缓存 HTML 解析结果, 纯解析无副作用, 不创建/不显示 Document)。
            // 难点(2026-08-22): ApricityScreen.init() 里 Document.create() 同步解析+脚本+布局
            // (实测 300ms+), 期间 MC 屏幕是黑的。先预热 blueprint, 打开时解析部分大幅加快。
            com.sighs.apricityui.parser.HTML.prepareTemplates();
            phoneWarmupDone = true;
            MelonsTools.LOGGER.info("[Phone] warmup done path={}", PHONE_PATH);
        } catch (Throwable ignored) {
        }
    }

    /** 检查玩家主手/副手是否手持手机物品 */
    private static boolean isHoldingPhone(Minecraft mc) {
        try {
            if (mc.player == null) return false;
            net.minecraft.world.item.ItemStack main = mc.player.getMainHandItem();
            net.minecraft.world.item.ItemStack off = mc.player.getOffhandItem();
            net.minecraft.world.item.Item phone = com.example.melonstools.item.ModItems.MOBILE_PHONE.get();
            if (phone == null) return false;
            return main.is(phone) || off.is(phone);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Java 驱动页面时钟(替代 JS tickClock 的 setTimeout 递归, 减少渲染线程定时器压力) */
    private static void updateClock(Document doc) {
        try {
            java.time.LocalTime now = java.time.LocalTime.now();
            String t = String.format(java.util.Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute());
            Element sys = doc.getElementById("sysTime");
            if (sys != null && !t.equals(sys.getTextContent())) {
                sys.setTextContent(t);
            }
            Element home = doc.getElementById("homeTime");
            if (home != null && !t.equals(home.getTextContent())) {
                home.setTextContent(t);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 服务端数据下发:写入页面 #phoneData 元素,页面 JS 轮询读取 */
    public static void applyPhoneData(String json) {
        Minecraft mc = Minecraft.getInstance();
        List<Document> docs = com.sighs.apricityui.ApricityUI.getDocument(PHONE_PATH);
        for (Document doc : docs) {
            try {
                Element el = doc.getElementById("phoneData");
                if (el != null) {
                    el.setTextContent(json);
                }
            } catch (Throwable ignored) {
            }
        }
        requestAvatars(json);
    }

    /**
     * 客户端本地构建玩家基础档案 JSON(仅 player 段)。
     * 场景: 单人世界没有网络连接(openPhoneScreen 不发 RequestPhoneFullPacket),
     * 服务端数据永远不会推送到 #phoneData, 玩家信息栏只能靠兜底 → 空白。
     * 此方法从玩家 ID 卡提供本地可读的基础档案，服务端数据到达后由 applyPhoneData 覆盖。
     * 仅客户端，只含主玩家自身。
     */
    public static void ensureLocalPhoneData() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        try {
            JsonObject profile = new JsonObject();
            profile.addProperty("id", mc.player.getScoreboardName());
            // 名字: 客户端可直接读主手 ID 卡(与服务端 getPlayerName 同源), 拿不到则用玩家名
            String cardName = com.example.melonstools.utils.PhoneUtils.getPlayerName(mc.player);
            profile.addProperty("name", cardName != null ? cardName : mc.player.getScoreboardName());
            String rawDept = com.example.melonstools.utils.PhoneUtils.getPlayerDepartment(mc.player);
            String rawPosition = com.example.melonstools.utils.PhoneUtils.getPlayerPosition(mc.player);
            int rawLevel = com.example.melonstools.utils.PhoneUtils.getPlayerIdCardLevel(mc.player);
            com.example.melonstools.department.DepartmentPolicy.Identity identity =
                    com.example.melonstools.department.DepartmentPolicy.identity(rawDept, rawPosition, rawLevel);
            profile.addProperty("dept", identity.department());
            profile.addProperty("position", identity.position());
            profile.addProperty("level", identity.level());
            // 余额: 仅服务端能读取 Lightman's Currency, 客户端拿不到则置 0
            profile.addProperty("balance", "0");
            JsonObject root = new JsonObject();
            root.add("player", profile);
            applyPhoneData(root.toString());
        } catch (Throwable ignored) {
        }
    }

    /** 解析下发 JSON,为好友/请求/附近/群成员请求皮肤头像(动态纹理注册) */
    private static void requestAvatars(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray groups = new JsonArray();
            if (root.has("friends")) groups.addAll(root.getAsJsonArray("friends"));
            if (root.has("pending")) groups.addAll(root.getAsJsonArray("pending"));
            if (root.has("nearby")) groups.addAll(root.getAsJsonArray("nearby"));
            if (root.has("rooms")) {
                JsonArray rooms = root.getAsJsonArray("rooms");
                for (JsonElement e : rooms) {
                    JsonObject room = e.getAsJsonObject();
                    if (room.has("members")) groups.addAll(room.getAsJsonArray("members"));
                    // 房间头像(物品图标)由页面 <texture src="minecraft:textures/item/...">
                    // 直接引用, 无需 Java 侧渲染
                }
            }
            for (JsonElement e : groups) {
                JsonObject o = e.getAsJsonObject();
                if (!o.has("uuid")) continue;
                try {
                    UUID uuid = UUID.fromString(o.get("uuid").getAsString());
                    String name = o.has("name") ? o.get("name").getAsString() : "";
                    AvatarRenderer.ensureAvatar(uuid, name);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 服务端榜单下发:写入页面 #leaderboardData 元素 */
    public static void applyLeaderboardData(String json) {
        List<Document> docs = com.sighs.apricityui.ApricityUI.getDocument(PHONE_PATH);
        for (Document doc : docs) {
            try {
                Element el = doc.getElementById("leaderboardData");
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
        tickCounter++;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        // 难点(2026-08-22): 不能因 mc.screen != null 提前 return——手机 Screen 打开时
        // tick 必须继续跑, 否则 PhoneScrollController 的 ensureBound 不会执行,
        // wheel 监听器永不绑定, 滚轮失效。之前版本在此 return 导致滚轮完全无响应。
        List<Document> docs = com.sighs.apricityui.ApricityUI.getDocument(PHONE_PATH);
        if (!docs.isEmpty()) {
            for (Document doc : docs) {
                try {
                    // 滚动控制(Java 侧接管: 滚轮缓动 + 自动滚底), 每 tick 轻量检测
                    PhoneScrollController.tick(doc);
                } catch (Throwable ignored) {
                }
            }
        }
        // 手持手机物品时预热页面(每 20 tick 检测一次, 避免每 tick 查物品)
        if (!phoneWarmupDone && tickCounter % 20 == 0 && mc.screen == null) {
            if (isHoldingPhone(mc)) {
                warmupPhone();
            }
        }
        // 每 10 tick(0.5 秒)低频任务: 时钟刷新 + viewport zoom 校正 + 页面动作队列轮询
        if (tickCounter % 10 != 0) return;
        for (Document doc : docs) {
            try {
                updateClock(doc);
                // 难点(2026-08-21): AUI States 进程级缓存导致 meta zoom 改动不生效,
                // 定期强制 setViewportZoom(幂等,已是目标值直接返回 false 不重排)
                doc.setViewportZoom(PHONE_ZOOM);
                // 单人世界(无网络)或服务端数据未到: 若 #phoneData 为空且手机 Screen 打开,
                // 补写一份客户端本地基础档案, 否则玩家信息栏永久空白。
                // 服务端全量数据到达后 applyPhoneData 会覆盖为真实数据。
                try {
                    Element pd = doc.getElementById("phoneData");
                    if (pd != null && (pd.getTextContent() == null || pd.getTextContent().isEmpty())) {
                        ensureLocalPhoneData();
                    }
                } catch (Throwable ignored) {
                }
                Element queue = doc.getElementById("melonActionQueue");
                if (queue == null) continue;
                String value = queue.getValue();
                if (value == null || value.isBlank()) continue;
                queue.setValue("");
                handleActions(value);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 解析页面动作队列并发送对应网络包 */
    private static void handleActions(String json) {
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                String t = o.get("t").getAsString();
                switch (t) {
                    case "friend_add":
                        NetworkManager.INSTANCE.sendToServer(new FriendActionPacket(FriendActionPacket.Action.ADD, UUID.fromString(o.get("u").getAsString())));
                        break;
                    case "friend_accept":
                        NetworkManager.INSTANCE.sendToServer(new FriendActionPacket(FriendActionPacket.Action.ACCEPT, UUID.fromString(o.get("u").getAsString())));
                        break;
                    case "friend_reject":
                        NetworkManager.INSTANCE.sendToServer(new FriendActionPacket(FriendActionPacket.Action.REJECT, UUID.fromString(o.get("u").getAsString())));
                        break;
                    case "friend_remove":
                        NetworkManager.INSTANCE.sendToServer(new FriendActionPacket(FriendActionPacket.Action.REMOVE, UUID.fromString(o.get("u").getAsString())));
                        break;
                    case "nearby_refresh":
                        NetworkManager.INSTANCE.sendToServer(new RequestNearbyPlayersPacket());
                        break;
                    case "room_create":
                        String avatar = o.has("avatar") ? o.get("avatar").getAsString() : "";
                        NetworkManager.INSTANCE.sendToServer(new ChatActionPacket(ChatActionPacket.Action.CREATE_ROOM, "", o.get("name").getAsString(), avatar));
                        break;
                    case "chat_send":
                        NetworkManager.INSTANCE.sendToServer(new ChatActionPacket(ChatActionPacket.Action.SEND_MESSAGE, o.get("roomId").getAsString(), o.get("content").getAsString()));
                        break;
                    case "room_kick":
                        NetworkManager.INSTANCE.sendToServer(new ChatActionPacket(ChatActionPacket.Action.KICK_MEMBER, o.get("roomId").getAsString(), o.get("u").getAsString()));
                        break;
                    case "room_rename":
                        NetworkManager.INSTANCE.sendToServer(new ChatActionPacket(ChatActionPacket.Action.RENAME_ROOM, o.get("roomId").getAsString(), o.get("name").getAsString()));
                        break;
                    case "room_join":
                        NetworkManager.INSTANCE.sendToServer(new ChatActionPacket(ChatActionPacket.Action.JOIN_ROOM, "", o.get("name").getAsString()));
                        break;
                    case "room_invite":
                        NetworkManager.INSTANCE.sendToServer(new ChatActionPacket(ChatActionPacket.Action.INVITE_MEMBER, o.get("roomId").getAsString(), o.get("u").getAsString()));
                        break;
                    case "room_dissolve":
                        NetworkManager.INSTANCE.sendToServer(new ChatActionPacket(ChatActionPacket.Action.DISSOLVE_ROOM, o.get("roomId").getAsString(), ""));
                        break;
                    case "room_read":
                        NetworkManager.INSTANCE.sendToServer(new ChatActionPacket(ChatActionPacket.Action.MARK_READ, o.get("roomId").getAsString(), ""));
                        break;
                    case "task_claim":
                        NetworkManager.INSTANCE.sendToServer(new ClaimTaskPacket(o.get("id").getAsString()));
                        break;
                    case "score_submit":
                        NetworkManager.INSTANCE.sendToServer(new SubmitScorePacket(o.get("game").getAsString(), o.get("diff").getAsString(), o.get("score").getAsInt()));
                        break;
                    case "report_submit":
                        NetworkManager.INSTANCE.sendToServer(new SubmitReportPacket(
                                o.has("anomalyInstanceId") ? o.get("anomalyInstanceId").getAsString() : "",
                                o.has("title") ? o.get("title").getAsString() : "",
                                o.has("content") ? o.get("content").getAsString() : ""
                        ));
                        break;
                    case "lb_request":
                        NetworkManager.INSTANCE.sendToServer(new RequestLeaderboardPacket());
                        break;
                    default:
                        // Department/business actions share the terminal server handler.
                        // Forward the original object instead of silently dropping phone actions.
                        NetworkManager.INSTANCE.sendToServer(new com.example.melonstools.network.TerminalActionPacket("[" + o + "]"));
                        break;
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
