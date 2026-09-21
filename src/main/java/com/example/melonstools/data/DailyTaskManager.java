package com.example.melonstools.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.text.SimpleDateFormat;
import java.util.*;

/** 每日任务系统:按玩家 ID 卡部门生成每日任务,记录进度与领取状态 */
public class DailyTaskManager extends SavedData {

    public static class TaskTemplate {
        public final String id;
        public final String name;
        public final String desc;
        public final String type;
        public final int goal;
        public final String reward;

        public TaskTemplate(String id, String name, String desc, String type, int goal, String reward) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.type = type;
            this.goal = goal;
            this.reward = reward;
        }
    }

    public static class PlayerTask {
        public String id;
        public String name;
        public String desc;
        public String type;
        public String reward;
        public int cur;
        public int goal;
        public String state; // active / claimable / claimed

        public PlayerTask(TaskTemplate t) {
            this.id = t.id + "_" + UUID.randomUUID().toString().substring(0, 6);
            this.name = t.name;
            this.desc = t.desc;
            this.type = t.type;
            this.reward = t.reward;
            this.cur = 0;
            this.goal = t.goal;
            this.state = "active";
        }

        public CompoundTag save() {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("Id", id);
            nbt.putString("Name", name);
            nbt.putString("Desc", desc);
            nbt.putString("Type", type);
            nbt.putString("Reward", reward);
            nbt.putInt("Cur", cur);
            nbt.putInt("Goal", goal);
            nbt.putString("State", state);
            return nbt;
        }

        public static PlayerTask load(CompoundTag nbt) {
            PlayerTask t = new PlayerTask(new TaskTemplate(nbt.getString("Id"), nbt.getString("Name"), nbt.getString("Desc"), nbt.getString("Type"), nbt.getInt("Goal"), nbt.getString("Reward")));
            t.id = nbt.getString("Id");
            t.cur = nbt.getInt("Cur");
            t.state = nbt.getString("State");
            return t;
        }
    }

    private static class PlayerTaskData {
        public final String day;
        public final List<PlayerTask> tasks;

        public PlayerTaskData(String day, List<PlayerTask> tasks) {
            this.day = day;
            this.tasks = tasks;
        }
    }

    /** 各部门任务模板(与页面文案对应) */
    private static Map<String, List<TaskTemplate>> TEMPLATES = null;

    private static Map<String, List<TaskTemplate>> templates() {
        if (TEMPLATES == null) {
            TEMPLATES = new LinkedHashMap<>();
            TEMPLATES.put("管理部门", Arrays.asList(
                    new TaskTemplate("m1", "主持部门会议", "在群聊频道发送 5 条消息", "chat", 5, "30 金"),
                    new TaskTemplate("m2", "处理异常事件", "完成 2 次任意 QTE 挑战", "qte", 2, "50 金"),
                    new TaskTemplate("m3", "联络新成员", "添加 1 位好友", "friend", 1, "经验奖励"),
                    new TaskTemplate("m4", "组织例会", "在群聊频道发送 3 条消息", "chat", 3, "25 金"),
                    new TaskTemplate("m5", "完成战备训练", "完成 3 次任意 QTE 挑战", "qte", 3, "60 金"),
                    new TaskTemplate("m6", "招募协作者", "添加 2 位好友", "friend", 2, "40 金"),
                    new TaskTemplate("m7", "发布工作通报", "在群聊频道发送 2 条消息", "chat", 2, "20 金"),
                    new TaskTemplate("m8", "应急响应演练", "完成 1 次任意 QTE 挑战", "qte", 1, "30 金"),
                    new TaskTemplate("m9", "拓展团队人脉", "添加 1 位好友", "friend", 1, "15 金"),
                    new TaskTemplate("m10", "主持大型会议", "在群聊频道发送 8 条消息", "chat", 8, "70 金")
            ));
            TEMPLATES.put("科研部门", Arrays.asList(
                    new TaskTemplate("r1", "完成校准 QTE", "完成 2 次任意 QTE 挑战", "qte", 2, "50 金"),
                    new TaskTemplate("r2", "记录实验数据", "在群聊频道发送 10 条消息", "chat", 10, "30 金"),
                    new TaskTemplate("r3", "合作用户测试", "添加 1 位好友", "friend", 1, "经验奖励"),
                    new TaskTemplate("r4", "完成进阶实验", "完成 3 次任意 QTE 挑战", "qte", 3, "60 金"),
                    new TaskTemplate("r5", "学术交流", "在群聊频道发送 5 条消息", "chat", 5, "35 金"),
                    new TaskTemplate("r6", "招募实验志愿者", "添加 2 位好友", "friend", 2, "45 金"),
                    new TaskTemplate("r7", "整理数据", "在群聊频道发送 4 条消息", "chat", 4, "25 金"),
                    new TaskTemplate("r8", "极限实验", "完成 4 次任意 QTE 挑战", "qte", 4, "80 金"),
                    new TaskTemplate("r9", "大型实验", "在群聊频道发送 12 条消息", "chat", 12, "65 金"),
                    new TaskTemplate("r10", "招募协作成员", "添加 1 位好友", "friend", 1, "20 金")
            ));
            TEMPLATES.put("安保部门", Arrays.asList(
                    new TaskTemplate("s1", "完成战斗训练", "完成 4 次任意 QTE 挑战", "qte", 4, "70 金"),
                    new TaskTemplate("s2", "上报巡逻情况", "在群聊频道发送 3 条消息", "chat", 3, "20 金"),
                    new TaskTemplate("s3", "人员排查", "添加 1 位好友", "friend", 1, "30 金"),
                    new TaskTemplate("s4", "反恐演练", "完成 2 次任意 QTE 挑战", "qte", 2, "55 金"),
                    new TaskTemplate("s5", "通讯联络", "在群聊频道发送 5 条消息", "chat", 5, "30 金"),
                    new TaskTemplate("s6", "招募安保新人", "添加 2 位好友", "friend", 2, "40 金"),
                    new TaskTemplate("s7", "应急出动", "完成 1 次任意 QTE 挑战", "qte", 1, "35 金"),
                    new TaskTemplate("s8", "汇报值守", "在群聊频道发送 2 条消息", "chat", 2, "15 金"),
                    new TaskTemplate("s9", "外部协作", "添加 1 位好友", "friend", 1, "20 金"),
                    new TaskTemplate("s10", "高强度训练", "完成 5 次任意 QTE 挑战", "qte", 5, "90 金")
            ));
            TEMPLATES.put("后勤部门", Arrays.asList(
                    new TaskTemplate("l1", "完成体能测试", "完成 2 次任意 QTE 挑战", "qte", 2, "30 金"),
                    new TaskTemplate("l2", "工作沟通", "在群聊频道发送 3 条消息", "chat", 3, "20 金"),
                    new TaskTemplate("l3", "协作搬运", "添加 1 位好友", "friend", 1, "20 金"),
                    new TaskTemplate("l4", "物资调度", "完成 1 次任意 QTE 挑战", "qte", 1, "25 金"),
                    new TaskTemplate("l5", "团队会议", "在群聊频道发送 5 条消息", "chat", 5, "30 金"),
                    new TaskTemplate("l6", "招募后勤人员", "添加 2 位好友", "friend", 2, "35 金"),
                    new TaskTemplate("l7", "体能强化", "完成 3 次任意 QTE 挑战", "qte", 3, "50 金"),
                    new TaskTemplate("l8", "发布通知", "在群聊频道发送 2 条消息", "chat", 2, "15 金"),
                    new TaskTemplate("l9", "部门协作", "添加 1 位好友", "friend", 1, "15 金"),
                    new TaskTemplate("l10", "大型调度", "在群聊频道发送 8 条消息", "chat", 8, "55 金")
            ));
        }
        return TEMPLATES;
    }

    private final Map<UUID, PlayerTaskData> data = new HashMap<>();

    public DailyTaskManager() {
    }

    public static DailyTaskManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DailyTaskManager::load, DailyTaskManager::new, "melon_daily_tasks");
    }

    public static String dayKey() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    /** 每日任务上限:按 id 卡等级(1级3 / 2级4 / 3级6 / 4级8 / 5级10) */
    private static int taskLimitByLevel(int level) {
        switch (level) {
            case 2: return 4;
            case 3: return 6;
            case 4: return 8;
            case 5: return 10;
            case 1:
            default: return 3;
        }
    }

    /** 获取玩家今日任务,跨日自动重新生成 */
    public List<PlayerTask> getTasksFor(ServerPlayer player) {
        String day = dayKey();
        UUID uuid = player.getUUID();
        PlayerTaskData d = data.get(uuid);
        if (d == null || !d.day.equals(day)) {
            String dept = com.example.melonstools.utils.PhoneUtils.getPlayerDepartment(player);
            // 从可配置的任务模板库(TaskConfigManager)取该部门启用的日常任务模板
            List<TaskConfigManager.TaskTemplate> tpl = com.example.melonstools.data.TaskConfigManager.get(player.serverLevel()).getDailyTemplatesFor(dept);
            List<PlayerTask> tasks = new ArrayList<>();
            if (tpl != null) {
                // 按 id 卡等级截取任务数量上限
                int limit = taskLimitByLevel(com.example.melonstools.utils.PhoneUtils.getPlayerIdCardLevel(player));
                int count = 0;
                for (TaskConfigManager.TaskTemplate t : tpl) {
                    if (count >= limit) { break; }
                    tasks.add(new PlayerTask(new TaskTemplate(t.id, t.name, t.desc, t.type, t.goal, t.reward)));
                    count++;
                }
            }
            d = new PlayerTaskData(day, tasks);
            data.put(uuid, d);
            setDirty();
        }
        return d.tasks;
    }

    /** 事件进度推进:type = qte / chat / friend */
    public void onEvent(ServerPlayer player, String type) {
        List<PlayerTask> tasks = getTasksFor(player);
        boolean changed = false;
        for (PlayerTask t : tasks) {
            if (t.state.equals("active") && t.type.equals(type)) {
                t.cur++;
                if (t.cur >= t.goal) {
                    t.cur = t.goal;
                    t.state = "claimable";
                }
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    /** 领取奖励(发钱逻辑后续接入 Lightman's Currency) */
    public boolean claim(ServerPlayer player, String taskId) {
        List<PlayerTask> tasks = getTasksFor(player);
        for (PlayerTask t : tasks) {
            if (t.id.equals(taskId) && t.state.equals("claimable")) {
                t.state = "claimed";
                setDirty();
                return true;
            }
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag pCompoundTag) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, PlayerTaskData> e : data.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Uuid", e.getKey());
            p.putString("Day", e.getValue().day);
            ListTag tasks = new ListTag();
            for (PlayerTask t : e.getValue().tasks) {
                tasks.add(t.save());
            }
            p.put("Tasks", tasks);
            players.add(p);
        }
        pCompoundTag.put("Players", players);
        return pCompoundTag;
    }

    public static DailyTaskManager load(CompoundTag nbt) {
        DailyTaskManager manager = new DailyTaskManager();
        if (nbt.contains("Players", Tag.TAG_LIST)) {
            ListTag players = nbt.getList("Players", Tag.TAG_COMPOUND);
            for (int i = 0; i < players.size(); i++) {
                CompoundTag p = players.getCompound(i);
                UUID uuid = p.getUUID("Uuid");
                String day = p.getString("Day");
                List<PlayerTask> tasks = new ArrayList<>();
                if (p.contains("Tasks", Tag.TAG_LIST)) {
                    ListTag tl = p.getList("Tasks", Tag.TAG_COMPOUND);
                    for (int j = 0; j < tl.size(); j++) {
                        tasks.add(PlayerTask.load(tl.getCompound(j)));
                    }
                }
                manager.data.put(uuid, new PlayerTaskData(day, tasks));
            }
        }
        return manager;
    }
}

