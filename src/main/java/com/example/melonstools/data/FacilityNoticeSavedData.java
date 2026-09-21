package com.example.melonstools.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 世界级设施公告数据：校验、状态变更、过期标记与 SavedData 持久化。 */
public class FacilityNoticeSavedData extends SavedData {
    public static final String DATA_NAME = "melon_facility_notices";
    public static final int MAX_TITLE_LENGTH = 80;
    public static final int MAX_CONTENT_LENGTH = 4000;
    public static final int MAX_DEPARTMENT_LENGTH = 40;
    public static final int MAX_HISTORY = 200;

    public static final String TARGET_ALL = "ALL";
    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_DEPARTMENT = "DEPARTMENT";

    public static final String NOTICE_ORDINARY = com.example.melonstools.department.DepartmentPolicy.NOTICE_ORDINARY;
    public static final String NOTICE_DEPARTMENT = com.example.melonstools.department.DepartmentPolicy.NOTICE_DEPARTMENT;
    public static final String NOTICE_FACILITY = com.example.melonstools.department.DepartmentPolicy.NOTICE_FACILITY;
    public static final String NOTICE_EMERGENCY = com.example.melonstools.department.DepartmentPolicy.NOTICE_EMERGENCY;

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String STATUS_DELETED = "deleted";

    private final List<Notice> notices = new ArrayList<>();
    private int sequence;

    public static FacilityNoticeSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FacilityNoticeSavedData::load, FacilityNoticeSavedData::new, DATA_NAME);
    }

    public static FacilityNoticeSavedData load(CompoundTag tag) {
        FacilityNoticeSavedData data = new FacilityNoticeSavedData();
        data.sequence = tag.getInt("Sequence");
        ListTag list = tag.getList("Notices", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Notice notice = Notice.load(list.getCompound(i));
            if (!notice.id.isEmpty()) data.notices.add(notice);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Sequence", sequence);
        ListTag list = new ListTag();
        for (Notice notice : notices) list.add(notice.save());
        tag.put("Notices", list);
        return tag;
    }

    public List<Notice> getNotices() { return Collections.unmodifiableList(notices); }

    @Nullable
    public Notice findById(String id) {
        String clean = safe(id).trim();
        if (clean.isEmpty()) return null;
        for (Notice notice : notices) if (clean.equals(notice.id)) return notice;
        return null;
    }

    @Nullable
    public Notice publish(ServerPlayer publisher, String title, String content, String targetDepartment, long expiresAt, boolean pinned) {
        return publish(publisher, title, content, targetDepartment, expiresAt, pinned, NOTICE_ORDINARY);
    }

    @Nullable
    public Notice publish(ServerPlayer publisher, String title, String content, String targetDepartment, long expiresAt, boolean pinned, String noticeLevel) {
        markExpired(System.currentTimeMillis());
        if (publisher == null) return null;
        String cleanTitle = cleanTitle(title);
        String cleanContent = cleanContent(content);
        if (cleanTitle.isEmpty() || cleanContent.isEmpty()) return null;
        Notice notice = new Notice();
        notice.id = nextId();
        notice.title = cleanTitle;
        notice.content = cleanContent;
        notice.authorUuid = publisher.getUUID();
        notice.authorName = limit(safe(publisher.getScoreboardName()).trim(), 40);
        notice.publishedAt = System.currentTimeMillis();
        notice.targetDepartment = normalizeTargetDepartment(targetDepartment);
        notice.expiresAt = Math.max(0L, expiresAt);
        notice.pinned = pinned;
        notice.noticeLevel = com.example.melonstools.department.DepartmentPolicy.normalizeNoticeLevel(noticeLevel);
        notice.ownerUuid = publisher.getUUID();
        com.example.melonstools.department.DepartmentPolicy.Identity issuer = com.example.melonstools.department.DepartmentPolicy.identity(publisher);
        notice.issuerPosition = issuer.position();
        notice.issuerLevel = issuer.level();
        notice.status = STATUS_ACTIVE;
        notices.add(notice);
        pruneDeletedOverflow();
        setDirty();
        broadcastPublishedNotice(publisher, notice);
        return notice;
    }

    private static void broadcastPublishedNotice(ServerPlayer publisher, Notice notice) {
        if (publisher == null || publisher.server == null || notice == null) return;
        String prefix = switch (notice.noticeLevel) {
            case NOTICE_DEPARTMENT -> "§b[部门通告]";
            case NOTICE_FACILITY -> "§e[一级广播]";
            case NOTICE_EMERGENCY -> "§c[紧急广播]";
            default -> "§6[行政通知]";
        };
        String scope = TARGET_ALL.equals(notice.targetDepartment) ? "全设施" : notice.targetDepartment;
        String body = safe(notice.content).replace('\r', ' ').replace('\n', ' ').trim();
        boolean truncated = body.length() > 240;
        if (truncated) body = body.substring(0, 240).trim() + "……";
        Component header = Component.literal(prefix + " §f" + notice.title + " §7[" + scope + "] §8发布人：" + notice.authorName);
        Component detail = Component.literal("§7" + body + (truncated ? " §8（请在个人终端查看全文）" : ""));
        for (ServerPlayer online : publisher.server.getPlayerList().getPlayers()) {
            online.sendSystemMessage(header);
            online.sendSystemMessage(detail);
        }
    }

    public boolean update(String id, String title, String content, String targetDepartment, long expiresAt, boolean pinned) {
        return update(id, title, content, targetDepartment, expiresAt, pinned, null);
    }

    public boolean update(String id, String title, String content, String targetDepartment, long expiresAt, boolean pinned, @Nullable String noticeLevel) {
        markExpired(System.currentTimeMillis());
        Notice notice = findById(id);
        if (notice == null || STATUS_DELETED.equals(notice.status)) return false;
        String cleanTitle = cleanTitle(title);
        String cleanContent = cleanContent(content);
        if (cleanTitle.isEmpty() || cleanContent.isEmpty()) return false;
        boolean changed = false;
        String dept = normalizeTargetDepartment(targetDepartment);
        long exp = Math.max(0L, expiresAt);
        if (!cleanTitle.equals(notice.title)) { notice.title = cleanTitle; changed = true; }
        if (!cleanContent.equals(notice.content)) { notice.content = cleanContent; changed = true; }
        if (!dept.equals(notice.targetDepartment)) { notice.targetDepartment = dept; changed = true; }
        if (notice.expiresAt != exp) { notice.expiresAt = exp; changed = true; }
        if (notice.pinned != pinned) { notice.pinned = pinned; changed = true; }
        if (noticeLevel != null) {
            String nl = com.example.melonstools.department.DepartmentPolicy.normalizeNoticeLevel(noticeLevel);
            if (!nl.equals(notice.noticeLevel)) { notice.noticeLevel = nl; changed = true; }
        }
        if (STATUS_EXPIRED.equals(notice.status) && (exp <= 0L || exp > System.currentTimeMillis())) { notice.status = STATUS_ACTIVE; changed = true; }
        if (changed) setDirty();
        return true;
    }

    /** OP 硬删除：从 SavedData 列表移除。 */
    public boolean delete(String id) {
        Notice notice = findById(id);
        if (notice == null) return false;
        notices.remove(notice);
        setDirty();
        return true;
    }
    public boolean archive(String id) { return setStatus(id, STATUS_ARCHIVED); }
    public boolean expire(String id) { return setStatus(id, STATUS_EXPIRED); }
    public boolean pin(String id) { return setPinned(id, true); }
    public boolean unpin(String id) { return setPinned(id, false); }

    public boolean setPinned(String id, boolean pinned) {
        markExpired(System.currentTimeMillis());
        Notice notice = findById(id);
        if (notice == null || STATUS_DELETED.equals(notice.status)) return false;
        if (notice.pinned == pinned) return true;
        notice.pinned = pinned;
        setDirty();
        return true;
    }

    public boolean setStatus(String id, String status) {
        markExpired(System.currentTimeMillis());
        Notice notice = findById(id);
        if (notice == null || STATUS_DELETED.equals(notice.status)) return false;
        String s = normalizeStatus(status);
        if (STATUS_ACTIVE.equals(s) && notice.expiresAt > 0L && notice.expiresAt <= System.currentTimeMillis()) s = STATUS_EXPIRED;
        if (notice.status.equals(s)) return true;
        notice.status = s;
        setDirty();
        return true;
    }

    public int markExpired(long now) {
        int changed = 0;
        for (Notice notice : notices) {
            if (STATUS_ACTIVE.equals(notice.status) && notice.expiresAt > 0L && notice.expiresAt <= now) {
                notice.status = STATUS_EXPIRED;
                changed++;
            }
        }
        if (changed > 0) setDirty();
        return changed;
    }

    public JsonObject toTerminalJson(boolean canPublish, boolean canManage, boolean canHardDelete) {
        markExpired(System.currentTimeMillis());
        JsonObject root = new JsonObject();
        JsonArray items = new JsonArray();
        int active = 0, archived = 0, expired = 0;
        for (Notice notice : sortedSnapshot()) {
            if (STATUS_DELETED.equals(notice.status)) continue;
            if (STATUS_ACTIVE.equals(notice.status)) active++;
            else if (STATUS_ARCHIVED.equals(notice.status)) archived++;
            else if (STATUS_EXPIRED.equals(notice.status)) expired++;
            items.add(notice.toJson());
        }
        root.add("items", items);
        root.addProperty("activeCount", active);
        root.addProperty("archivedCount", archived);
        root.addProperty("expiredCount", expired);
        root.addProperty("canPublish", canPublish);
        root.addProperty("canManage", canManage);
        root.addProperty("canHardDelete", canHardDelete);
        return root;
    }

    public JsonArray toVisibleJsonForPlayer(ServerPlayer player) {
        markExpired(System.currentTimeMillis());
        String department = player == null ? null : com.example.melonstools.utils.PhoneUtils.getPlayerDepartment(player);
        JsonArray arr = new JsonArray();
        for (Notice notice : sortedSnapshot()) if (notice.isVisibleToDepartment(department)) arr.add(notice.toJson());
        return arr;
    }

    private String nextId() { return "notice_" + System.currentTimeMillis() + "_" + (++sequence); }

    private List<Notice> sortedSnapshot() {
        List<Notice> copy = new ArrayList<>(notices);
        copy.sort(Comparator.comparing((Notice n) -> !n.pinned).thenComparing((Notice n) -> -n.publishedAt));
        return copy;
    }

    private void pruneDeletedOverflow() {
        if (notices.size() <= MAX_HISTORY) return;
        notices.sort(Comparator.comparingLong(n -> n.publishedAt));
        Iterator<Notice> it = notices.iterator();
        while (notices.size() > MAX_HISTORY && it.hasNext()) {
            Notice n = it.next();
            if (!STATUS_ACTIVE.equals(n.status)) it.remove();
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String limit(String value, int max) { return value.length() > max ? value.substring(0, max) : value; }
    private static String cleanTitle(String value) { return limit(safe(value).trim(), MAX_TITLE_LENGTH); }
    private static String cleanContent(String value) { return limit(safe(value).trim(), MAX_CONTENT_LENGTH); }
    private static String normalizeTargetDepartment(String value) {
        String v = limit(safe(value).trim(), MAX_DEPARTMENT_LENGTH);
        return (v.isEmpty() || "全部门".equals(v) || TARGET_ALL.equalsIgnoreCase(v)) ? TARGET_ALL : v;
    }
    private static String normalizeStatus(String value) {
        String v = safe(value).trim().toLowerCase(Locale.ROOT);
        if (STATUS_EXPIRED.equals(v) || STATUS_ARCHIVED.equals(v) || STATUS_DELETED.equals(v)) return v;
        return STATUS_ACTIVE;
    }
    private static String fmt(long ts) {
        if (ts <= 0L) return "永久";
        try { return new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(new Date(ts)); } catch (Throwable ignored) { return "-"; }
    }

    public static class Notice {
        public String id = "";
        public String title = "";
        public String content = "";
        @Nullable public UUID authorUuid;
        public String authorName = "";
        public long publishedAt;
        public String targetDepartment = TARGET_ALL;
        public long expiresAt;
        public boolean pinned;
        public String noticeLevel = NOTICE_ORDINARY;
        @Nullable public UUID ownerUuid;
        public String issuerPosition = "";
        public int issuerLevel;
        public String status = STATUS_ACTIVE;

        public static Notice load(CompoundTag tag) {
            Notice notice = new Notice();
            notice.id = safe(tag.getString("Id")).trim();
            notice.title = cleanTitle(tag.getString("Title"));
            notice.content = cleanContent(tag.getString("Content"));
            if (tag.hasUUID("AuthorUuid")) notice.authorUuid = tag.getUUID("AuthorUuid");
            notice.authorName = limit(safe(tag.getString("AuthorName")).trim(), 40);
            notice.publishedAt = tag.getLong("PublishedAt");
            notice.targetDepartment = normalizeTargetDepartment(tag.getString("TargetDepartment"));
            notice.expiresAt = Math.max(0L, tag.getLong("ExpiresAt"));
            notice.pinned = tag.getBoolean("Pinned");
            notice.noticeLevel = tag.contains("NoticeLevel") ? com.example.melonstools.department.DepartmentPolicy.normalizeNoticeLevel(tag.getString("NoticeLevel")) : NOTICE_ORDINARY;
            if (tag.hasUUID("OwnerUuid")) notice.ownerUuid = tag.getUUID("OwnerUuid");
            else notice.ownerUuid = null; // legacy: missing owner is not claimable by ordinary clerks.
            notice.issuerPosition = limit(safe(tag.getString("IssuerPosition")).trim(), 40);
            notice.issuerLevel = Math.max(0, tag.getInt("IssuerLevel"));
            notice.status = normalizeStatus(tag.getString("Status"));
            return notice;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Title", title);
            tag.putString("Content", content);
            if (authorUuid != null) tag.putUUID("AuthorUuid", authorUuid);
            tag.putString("AuthorName", authorName);
            tag.putLong("PublishedAt", publishedAt);
            tag.putString("TargetDepartment", targetDepartment);
            tag.putLong("ExpiresAt", expiresAt);
            tag.putBoolean("Pinned", pinned);
            tag.putString("NoticeLevel", noticeLevel);
            if (ownerUuid != null) tag.putUUID("OwnerUuid", ownerUuid);
            tag.putString("IssuerPosition", issuerPosition);
            tag.putInt("IssuerLevel", issuerLevel);
            tag.putString("Status", status);
            return tag;
        }

        public boolean isVisibleToDepartment(@Nullable String department) {
            if (!STATUS_ACTIVE.equals(status)) return false;
            long now = System.currentTimeMillis();
            if (expiresAt > 0L && expiresAt <= now) return false;
            if (TARGET_ALL.equalsIgnoreCase(targetDepartment) || "全部门".equals(targetDepartment)) return true;
            return department != null && !department.isBlank() && targetDepartment.equals(department);
        }

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("title", title);
            o.addProperty("content", content);
            o.addProperty("body", content);
            o.addProperty("authorUuid", authorUuid == null ? "" : authorUuid.toString());
            o.addProperty("ownerUuid", ownerUuid == null ? "" : ownerUuid.toString());
            o.addProperty("authorName", authorName);
            o.addProperty("publisherName", authorName);
            o.addProperty("publishedAt", publishedAt);
            o.addProperty("publishedAtText", fmt(publishedAt));
            o.addProperty("targetDepartment", TARGET_ALL.equals(targetDepartment) ? "全部门" : targetDepartment);
            o.addProperty("scope", TARGET_ALL.equals(targetDepartment) ? SCOPE_ALL : SCOPE_DEPARTMENT);
            o.addProperty("expiresAt", expiresAt);
            o.addProperty("expiresAtText", fmt(expiresAt));
            o.addProperty("pinned", pinned);
            o.addProperty("important", pinned || NOTICE_EMERGENCY.equals(noticeLevel));
            o.addProperty("noticeLevel", noticeLevel);
            o.addProperty("issuerPosition", issuerPosition);
            o.addProperty("issuerLevel", issuerLevel);
            o.addProperty("status", status);
            return o;
        }
    }
}
