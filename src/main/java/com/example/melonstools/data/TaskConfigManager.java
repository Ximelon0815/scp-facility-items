package com.example.melonstools.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 任务模板配置系统(终端面板可编辑)。
 * 由管理员在终端中增/删/改任务模板, 并持久化到磁盘。
 * 取代 DailyTaskManager 里的硬编码模板, 作为「日常任务」「部门任务」的模板来源。
 *
 * <p>模板字段:
 * <ul>
 *   <li>id       - 唯一标识(用于删除/编辑定位)</li>
 *   <li>name     - 任务显示名</li>
 *   <li>desc     - 任务描述</li>
 *   <li>type     - 进度类型(qte/chat/friend)</li>
 *   <li>goal     - 目标次数</li>
 *   <li>reward   - 奖励描述(如 "30 金", 后续可接 LC 发钱)</li>
 *   <li>dept     - 适用部门(未知/管理/科研/安保/后勤 或 "ALL" 表示所有部门；0级临时/受试人员归后勤)</li>
 *   <li>daily    - 是否日常任务(true=每日生成给该部门玩家; false=部门指派任务, 手动发放)</li>
 *   <li>enabled  - 是否启用(禁用后不生成/不显示)</li>
 * </ul>
 */
public class TaskConfigManager extends SavedData {

    public static final String DEPT_ALL = "ALL";

    public static class TaskTemplate {
        public String id;
        public String name;
        public String desc;
        public String type;     // qte / chat / friend
        public int goal;
        public String reward;
        public String dept;     // 部门 或 ALL
        public boolean daily;   // true=日常任务; false=部门指派
        public boolean enabled;

        public TaskTemplate(String id, String name, String desc, String type, int goal, String reward, String dept, boolean daily, boolean enabled) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.type = type;
            this.goal = goal;
            this.reward = reward;
            this.dept = dept == null || dept.isEmpty() ? DEPT_ALL : dept;
            this.daily = daily;
            this.enabled = enabled;
        }

        public TaskTemplate copy() {
            return new TaskTemplate(this.id, this.name, this.desc, this.type, this.goal, this.reward, this.dept, this.daily, this.enabled);
        }

        public CompoundTag save() {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("Id", id);
            nbt.putString("Name", name);
            nbt.putString("Desc", desc);
            nbt.putString("Type", type);
            nbt.putInt("Goal", goal);
            nbt.putString("Reward", reward);
            nbt.putString("Dept", dept);
            nbt.putBoolean("Daily", daily);
            nbt.putBoolean("Enabled", enabled);
            return nbt;
        }

        public static TaskTemplate load(CompoundTag nbt) {
            return new TaskTemplate(
                    nbt.getString("Id"),
                    nbt.getString("Name"),
                    nbt.getString("Desc"),
                    nbt.getString("Type"),
                    nbt.getInt("Goal"),
                    nbt.getString("Reward"),
                    nbt.getString("Dept"),
                    nbt.getBoolean("Daily"),
                    nbt.getBoolean("Enabled")
            );
        }

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("name", name);
            o.addProperty("desc", desc);
            o.addProperty("type", type);
            o.addProperty("goal", goal);
            o.addProperty("reward", reward);
            o.addProperty("dept", dept);
            o.addProperty("daily", daily);
            o.addProperty("enabled", enabled);
            return o;
        }
    }

    private final List<TaskTemplate> templates = new ArrayList<>();

    public TaskConfigManager() {
        // 若首次启动(空), 用默认模板种子填充(与旧 DailyTaskManager 模板对应)
        if (templates.isEmpty()) {
            seedDefaults();
            setDirty();
        }
    }

    private void seedDefaults() {
        templates.add(new TaskTemplate("m1", "主持部门会议", "在群聊频道发送 5 条消息", "chat", 5, "30 金", "管理部门", true, true));
        templates.add(new TaskTemplate("m2", "处理异常事件", "完成 2 次任意 QTE 挑战", "qte", 2, "50 金", "管理部门", true, true));
        templates.add(new TaskTemplate("m3", "联络新成员", "添加 1 位好友", "friend", 1, "经验奖励", "管理部门", true, true));
        templates.add(new TaskTemplate("r1", "完成校准 QTE", "完成 2 次任意 QTE 挑战", "qte", 2, "50 金", "科研部门", true, true));
        templates.add(new TaskTemplate("r2", "记录实验数据", "在群聊频道发送 10 条消息", "chat", 10, "30 金", "科研部门", true, true));
        templates.add(new TaskTemplate("s1", "完成战斗训练", "完成 4 次任意 QTE 挑战", "qte", 4, "70 金", "安保部门", true, true));
        templates.add(new TaskTemplate("s2", "上报巡逻情况", "在群聊频道发送 3 条消息", "chat", 3, "20 金", "安保部门", true, true));
        templates.add(new TaskTemplate("l1", "完成体能测试", "完成 2 次任意 QTE 挑战", "qte", 2, "30 金", "后勤部门", true, true));
        templates.add(new TaskTemplate("d1", "完成服从测试", "完成 2 次任意 QTE 挑战", "qte", 2, "20 金", "后勤部门", true, true));
    }

    public static TaskConfigManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TaskConfigManager::load, TaskConfigManager::new, "melon_task_config");
    }

    /** 全部模板(终端编辑用) */
    public List<TaskTemplate> getTemplates() {
        return templates;
    }

    /** 按 id 查找模板 */
    @Nullable
    public TaskTemplate findById(String id) {
        for (TaskTemplate t : templates) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    /** 生成新模板 id。编辑时传入 id 会复用; 为空则新生成递归唯一 id */
    public String nextId() {
        String base = "task_" + System.currentTimeMillis() % 1000000;
        String id = base;
        int i = 1;
        while (findById(id) != null) {
            id = base + "_" + i++;
        }
        return id;
    }

    /** 添加或更新(按 id)模板 */
    public void upsert(TaskTemplate t) {
        TaskTemplate existing = findById(t.id);
        if (existing != null) {
            int idx = templates.indexOf(existing);
            templates.set(idx, t);
        } else {
            templates.add(t);
        }
        setDirty();
    }

    /** 删除模板 */
    public boolean remove(String id) {
        TaskTemplate existing = findById(id);
        if (existing != null) {
            templates.remove(existing);
            setDirty();
            return true;
        }
        return false;
    }

    /** 启用/停用模板 */
    public boolean setEnabled(String id, boolean enabled) {
        TaskTemplate t = findById(id);
        if (t != null) {
            t.enabled = enabled;
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * 取某部门「启用的日常任务」模板(供 DailyTaskManager 生成今日任务)。
     * daily=true 且 (dept==ALL 或 dept==部门) 且 enabled。
     */
    public List<TaskTemplate> getDailyTemplatesFor(String dept) {
        List<TaskTemplate> result = new ArrayList<>();
        for (TaskTemplate t : templates) {
            if (!t.enabled || !t.daily) continue;
            if (t.dept.equals(DEPT_ALL) || t.dept.equals(dept)) {
                result.add(t);
            }
        }
        return result;
    }

    /** 取某部门「启用的部门指派任务」(manual). */
    public List<TaskTemplate> getManualTemplatesFor(String dept) {
        List<TaskTemplate> result = new ArrayList<>();
        for (TaskTemplate t : templates) {
            if (!t.enabled || t.daily) continue;
            if (t.dept.equals(DEPT_ALL) || t.dept.equals(dept)) {
                result.add(t);
            }
        }
        return result;
    }

    /** 终端显示的完整模板列表(含全部字段) JSON */
    public String toJson() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (TaskTemplate t : templates) {
            arr.add(t.toJson());
        }
        root.add("templates", arr);
        return root.toString();
    }

    @Override
    public CompoundTag save(CompoundTag pCompoundTag) {
        ListTag list = new ListTag();
        for (TaskTemplate t : templates) {
            list.add(t.save());
        }
        pCompoundTag.put("Templates", list);
        return pCompoundTag;
    }

    public static TaskConfigManager load(CompoundTag nbt) {
        TaskConfigManager manager = new TaskConfigManager();
        manager.templates.clear(); // 清掉 seedDefaults 生成的默认
        if (nbt.contains("Templates", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("Templates", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                manager.templates.add(TaskTemplate.load(list.getCompound(i)));
            }
        }
        // 若磁盘数据为空(全新), 重新种默认
        if (manager.templates.isEmpty()) {
            manager.seedDefaults();
        }
        return manager;
    }
}

