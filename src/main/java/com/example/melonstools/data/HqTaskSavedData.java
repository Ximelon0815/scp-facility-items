package com.example.melonstools.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 世界级总部任务数据。任务编辑 OP-only；普通玩家只看同步结果。 */
public class HqTaskSavedData extends SavedData {
    public static final String DATA_NAME = "melon_hq_tasks";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CLAIMABLE = "claimable";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    public static final String EVENT_MANUAL = "manual";
    public static final String EVENT_ANOMALY_MAINTENANCE = "anomaly_maintenance";
    public static final String EVENT_REPORT_SUBMITTED = "report_submitted";
    public static final String EVENT_REPORT_APPROVED = "report_approved";
    public static final String EVENT_SLOT_UNLOCKED = "slot_unlocked";
    public static final String EVENT_ANOMALY_CONTAINED = "anomaly_contained";
    public static final String EVENT_NOTICE_PUBLISHED = "notice_published";
    public static final String EVENT_FUND_REACHED = "fund_reached";

    private final List<HqTask> tasks = new ArrayList<>();

    public HqTaskSavedData() {
        seedDefaultsIfEmpty();
    }

    public static HqTaskSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HqTaskSavedData::load, HqTaskSavedData::new, DATA_NAME);
    }

    public List<HqTask> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    @Nullable
    public HqTask findById(String id) {
        if (id == null || id.isBlank()) return null;
        for (HqTask task : tasks) {
            if (id.equals(task.id)) return task;
        }
        return null;
    }

    public List<HqTask> getByStatus(String status) {
        List<HqTask> result = new ArrayList<>();
        for (HqTask task : tasks) {
            if (task.status.equals(status)) result.add(task);
        }
        return result;
    }

    public String nextId() {
        String base = "hq_" + (System.currentTimeMillis() % 1_000_000L);
        String id = base;
        int i = 1;
        while (findById(id) != null) {
            id = base + "_" + i++;
        }
        return id;
    }

    public void upsert(HqTask task) {
        task.normalizeFields();
        HqTask existing = findById(task.id);
        if (existing == null) {
            tasks.add(task);
        } else {
            tasks.set(tasks.indexOf(existing), task);
        }
        setDirty();
    }

    public boolean remove(String id) {
        HqTask existing = findById(id);
        if (existing == null) return false;
        tasks.remove(existing);
        setDirty();
        return true;
    }

    public boolean forceComplete(String id) {
        HqTask task = findById(id);
        if (task == null) return false;
        task.progress = Math.max(task.progress, task.goal);
        task.status = STATUS_COMPLETED;
        setDirty();
        return true;
    }

    public boolean forceFail(String id) {
        return setStatus(id, STATUS_FAILED);
    }

    /** 将指定任务进度补满并变为可结算；id 为空时补足默认任务池。 */
    public boolean fillProgress(String id) {
        if (id == null || id.isBlank()) {
            return refillDefaults();
        }
        HqTask task = findById(id);
        if (task == null) return false;
        task.progress = Math.max(task.progress, task.goal);
        if (STATUS_ACTIVE.equals(task.status)) task.status = STATUS_CLAIMABLE;
        setDirty();
        return true;
    }

    public boolean setStatus(String id, String status) {
        HqTask task = findById(id);
        if (task == null) return false;
        task.status = normalizeStatus(status);
        setDirty();
        return true;
    }

    /** 领取可结算任务奖励。返回奖励设施资金；-1 表示不可领取。 */
    public long claimReward(String id) {
        HqTask task = findById(id);
        if (task == null) return -1L;
        if (!STATUS_CLAIMABLE.equals(task.status)) return -1L;
        if (task.progress < task.goal) return -1L;
        task.status = STATUS_COMPLETED;
        setDirty();
        return task.rewardFunds;
    }

    /** 事件驱动推进：匹配 eventType 的 active 任务增加进度，满目标后进入 claimable。 */
    public int recordEvent(String eventType, int amount) {
        String event = normalizeEvent(eventType);
        int delta = Math.max(1, amount);
        int changed = 0;
        for (HqTask task : tasks) {
            if (!STATUS_ACTIVE.equals(task.status)) continue;
            if (!event.equals(task.eventType)) continue;
            task.progress = Math.min(task.goal, task.progress + delta);
            if (task.progress >= task.goal) task.status = STATUS_CLAIMABLE;
            changed++;
        }
        if (changed > 0) setDirty();
        return changed;
    }

    /** 资金达标类任务使用当前余额作为绝对进度，而不是递增。 */
    public int recordFundBalance(long balance) {
        int changed = 0;
        int current = (int)Math.max(0, Math.min(Integer.MAX_VALUE, balance));
        for (HqTask task : tasks) {
            if (!STATUS_ACTIVE.equals(task.status)) continue;
            if (!EVENT_FUND_REACHED.equals(task.eventType)) continue;
            int before = task.progress;
            task.progress = Math.min(task.goal, Math.max(task.progress, current));
            if (task.progress >= task.goal) task.status = STATUS_CLAIMABLE;
            if (task.progress != before) changed++;
        }
        if (changed > 0) setDirty();
        return changed;
    }

    public static int recordEvent(ServerLevel level, String eventType) {
        return recordEvent(level, eventType, 1);
    }

    public static int recordEvent(ServerLevel level, String eventType, int amount) {
        if (level == null) return 0;
        return get(level).recordEvent(eventType, amount);
    }

    public static int recordFundBalance(ServerLevel level, long balance) {
        if (level == null) return 0;
        return get(level).recordFundBalance(balance);
    }

    public JsonObject toJson(boolean includeEditor) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        int active = 0, claimable = 0, completed = 0, failed = 0;
        for (HqTask task : tasks) {
            arr.add(task.toJson(includeEditor));
            if (STATUS_ACTIVE.equals(task.status)) active++;
            else if (STATUS_CLAIMABLE.equals(task.status)) claimable++;
            else if (STATUS_COMPLETED.equals(task.status)) completed++;
            else if (STATUS_FAILED.equals(task.status)) failed++;
        }
        root.add("items", arr);
        root.add("tasks", arr.deepCopy());
        root.addProperty("activeCount", active + claimable);
        root.addProperty("claimableCount", claimable);
        root.addProperty("completedCount", completed);
        root.addProperty("failedCount", failed);
        root.addProperty("version", 2);
        return root;
    }

    public JsonObject editorStateJson(boolean isOp) {
        JsonObject editor = new JsonObject();
        editor.addProperty("enabled", isOp);
        editor.addProperty("canEdit", isOp);
        editor.addProperty("opOnly", true);
        editor.addProperty("actionPrefix", "hq_task_");
        return editor;
    }

    public boolean refillDefaults() {
        int before = tasks.size();
        addDefaultIfMissing(new HqTask("hq_seed_anomaly_maintenance", "异常物稳定维护", "完成一次异常物维护，让任意异常物恢复稳定度。", STATUS_ACTIVE, 0, 1, 500L, "科研部门", 2, EVENT_ANOMALY_MAINTENANCE));
        addDefaultIfMissing(new HqTask("hq_seed_report_submit", "提交实验报告", "科研部门提交一份异常物实验报告。", STATUS_ACTIVE, 0, 1, 300L, "科研部门", 2, EVENT_REPORT_SUBMITTED));
        addDefaultIfMissing(new HqTask("hq_seed_slot_unlock", "扩建收容隔间", "管理部门解锁一个新的收容隔间。", STATUS_ACTIVE, 0, 1, 1200L, "管理部门", 4, EVENT_SLOT_UNLOCKED));
        addDefaultIfMissing(new HqTask("hq_seed_notice_publish", "发布设施公告", "管理部门发布一次设施公告。", STATUS_ACTIVE, 0, 1, 200L, "管理部门", 3, EVENT_NOTICE_PUBLISHED));
        if (tasks.size() != before) {
            setDirty();
            return true;
        }
        return false;
    }

    private void seedDefaultsIfEmpty() {
        if (!tasks.isEmpty()) return;
        refillDefaults();
    }

    private void addDefaultIfMissing(HqTask task) {
        if (findById(task.id) == null) tasks.add(task);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (HqTask task : tasks) {
            list.add(task.save());
        }
        tag.put("Tasks", list);
        return tag;
    }

    public static HqTaskSavedData load(CompoundTag tag) {
        HqTaskSavedData data = new HqTaskSavedData();
        data.tasks.clear();
        if (tag.contains("Tasks", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Tasks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                data.tasks.add(HqTask.load(list.getCompound(i)));
            }
        }
        data.seedDefaultsIfEmpty();
        return data;
    }

    public static String normalizeStatus(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (STATUS_CLAIMABLE.equals(s) || STATUS_COMPLETED.equals(s) || STATUS_FAILED.equals(s)) return s;
        return STATUS_ACTIVE;
    }

    public static String normalizeEvent(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (s.isBlank()) return EVENT_MANUAL;
        return s;
    }

    public static class HqTask {
        public String id;
        public String title;
        public String description;
        public String status;
        public int progress;
        public int goal;
        public long rewardFunds;
        public String department;
        public int level;
        public String eventType;

        public HqTask(String id, String title, String description, String status, int progress, int goal, long rewardFunds, String department) {
            this(id, title, description, status, progress, goal, rewardFunds, department, 1, EVENT_MANUAL);
        }

        public HqTask(String id, String title, String description, String status, int progress, int goal, long rewardFunds, String department, int level) {
            this(id, title, description, status, progress, goal, rewardFunds, department, level, EVENT_MANUAL);
        }

        public HqTask(String id, String title, String description, String status, int progress, int goal, long rewardFunds, String department, int level, String eventType) {
            this.id = id == null || id.isBlank() ? "hq_missing" : id;
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            this.status = normalizeStatus(status);
            this.progress = Math.max(0, progress);
            this.goal = Math.max(1, goal);
            this.rewardFunds = Math.max(0L, rewardFunds);
            this.department = department == null || department.isBlank() ? "ALL" : department;
            this.level = Math.max(1, level);
            this.eventType = normalizeEvent(eventType);
        }

        public void normalizeFields() {
            if (id == null || id.isBlank()) id = "hq_missing";
            if (title == null) title = "";
            if (description == null) description = "";
            status = normalizeStatus(status);
            progress = Math.max(0, Math.min(progress, goal));
            goal = Math.max(1, goal);
            rewardFunds = Math.max(0L, rewardFunds);
            if (department == null || department.isBlank()) department = "ALL";
            level = Math.max(1, level);
            eventType = normalizeEvent(eventType);
            if (STATUS_ACTIVE.equals(status) && progress >= goal) status = STATUS_CLAIMABLE;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Title", title);
            tag.putString("Description", description);
            tag.putString("Status", status);
            tag.putInt("Progress", progress);
            tag.putInt("Goal", goal);
            tag.putLong("RewardFunds", rewardFunds);
            tag.putString("Department", department);
            tag.putInt("Level", level);
            tag.putString("EventType", eventType);
            return tag;
        }

        public static HqTask load(CompoundTag tag) {
            return new HqTask(
                    tag.getString("Id"),
                    tag.getString("Title"),
                    tag.getString("Description"),
                    tag.getString("Status"),
                    tag.getInt("Progress"),
                    tag.getInt("Goal"),
                    tag.getLong("RewardFunds"),
                    tag.getString("Department"),
                    tag.contains("Level", Tag.TAG_INT) ? tag.getInt("Level") : 1,
                    tag.contains("EventType", Tag.TAG_STRING) ? tag.getString("EventType") : EVENT_MANUAL
            );
        }

        public JsonObject toJson(boolean includeEditor) {
            normalizeFields();
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("title", title);
            o.addProperty("name", title);
            o.addProperty("desc", description);
            o.addProperty("status", status);
            o.addProperty("progress", progress);
            o.addProperty("goal", goal);
            o.addProperty("rewardFunds", rewardFunds);
            o.addProperty("department", department);
            o.addProperty("level", level);
            o.addProperty("eventType", eventType);
            if (includeEditor) {
                o.addProperty("canEdit", true);
            }
            return o;
        }
    }
}
