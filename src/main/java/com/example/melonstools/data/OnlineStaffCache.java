package com.example.melonstools.data;

import com.example.melonstools.idcard.item.custom.IdentityCardItem;
import com.example.melonstools.utils.PhoneUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线员工身份快照缓存。
 * <p>
 * 设施终端只读取这个缓存,不在每次同步时扫描所有玩家 ID 卡。
 * 刷新策略:登录/退出/保存卡即时刷新,服务端每 100 tick 低频兜底刷新一次。
 */
public class OnlineStaffCache {
    private static final Map<UUID, StaffSnapshot> ONLINE = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    private static final String[] DEPTS = new String[]{"未知", "管理部门", "科研部门", "安保部门", "后勤部门"};

    public static class StaffSnapshot {
        public final UUID uuid;
        public final String playerName;
        public final String cardName;
        public final String department;
        public final String position;
        public final int level;
        public final long updatedAt;

        public StaffSnapshot(UUID uuid, String playerName, String cardName, String department, String position, int level) {
            this.uuid = uuid;
            this.playerName = safe(playerName);
            this.cardName = safe(cardName);
            this.department = safe(department);
            this.position = safe(position);
            this.level = level;
            this.updatedAt = System.currentTimeMillis();
        }

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuid.toString());
            o.addProperty("playerName", playerName);
            o.addProperty("name", cardName.isEmpty() ? playerName : cardName);
            o.addProperty("cardName", cardName);
            o.addProperty("department", department);
            o.addProperty("position", position);
            o.addProperty("level", level);
            o.addProperty("updatedAt", updatedAt);
            return o;
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        tickCounter++;
        if (tickCounter >= 100) {
            tickCounter = 0;
            refreshAll(server);
        }
    }

    public static void refreshAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
        // 清理已离线但可能残留的 UUID
        ONLINE.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
    }

    public static void refresh(ServerPlayer player) {
        if (player == null) return;
        ItemStack card = PhoneUtils.findIdentityCard(player);
        // 身份系统规定玩家始终持有ID卡；若缓存刷新时卡暂不可读，则跳过本次快照而不生成额外身份状态。
        if (card.isEmpty()) {
            ONLINE.remove(player.getUUID());
            return;
        }
        String cardName = IdentityCardItem.getCardName(card);
        String rawDept = IdentityCardItem.getCardDepartment(card);
        String rawPos = IdentityCardItem.getCardPosition(card);
        int rawLevel = IdentityCardItem.getCardLevel(card);
        com.example.melonstools.department.DepartmentPolicy.Identity id = com.example.melonstools.department.DepartmentPolicy.identity(rawDept, rawPos, rawLevel);
        String dept = (rawDept == null || rawDept.isEmpty()) ? "未知" : id.department();
        String pos = (rawDept == null || rawDept.isEmpty()) ? safe(rawPos) : id.position();
        int level = (rawDept == null || rawDept.isEmpty()) ? 0 : id.level();
        ONLINE.put(player.getUUID(), new StaffSnapshot(
                player.getUUID(), player.getScoreboardName(), cardName, dept, pos, level
        ));
    }

    public static void remove(ServerPlayer player) {
        if (player != null) ONLINE.remove(player.getUUID());
    }

    public static JsonObject toJson() {
        JsonObject root = new JsonObject();
        Map<String, Integer> deptCounts = new LinkedHashMap<>();
        for (String d : DEPTS) deptCounts.put(d, 0);
        int total = 0;
        JsonArray players = new JsonArray();
        for (StaffSnapshot s : ONLINE.values()) {
            total++;
            if (!s.department.isEmpty()) {
                deptCounts.put(s.department, deptCounts.getOrDefault(s.department, 0) + 1);
            }
            players.add(s.toJson());
        }
        root.addProperty("total", total);
        JsonObject depts = new JsonObject();
        for (Map.Entry<String, Integer> e : deptCounts.entrySet()) {
            depts.addProperty(e.getKey(), e.getValue());
        }
        root.add("departments", depts);
        root.add("players", players);
        return root;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
