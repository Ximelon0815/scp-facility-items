package com.example.melonstools.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** 全服小游戏排行榜:4 游戏 x 3 难度共 12 张榜 */
public class LeaderboardManager extends SavedData {

    public static class Entry {
        public final UUID uuid;
        public final String name;
        public final int score;

        public Entry(UUID uuid, String name, int score) {
            this.uuid = uuid;
            this.name = name;
            this.score = score;
        }
    }

    /** 排序规则:asc=true 越小越好(扫雷用时/翻牌步数),false 越高越好(2048/贪吃蛇分数) */
    public static boolean isAscending(String game) {
        return "minesweeper".equals(game) || "memory".equals(game);
    }

    private final Map<String, List<Entry>> boards = new HashMap<>();

    public LeaderboardManager() {
    }

    public static LeaderboardManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LeaderboardManager::load, LeaderboardManager::new, "melon_leaderboards");
    }

    private static String key(String game, String diff) {
        return game + "|" + diff;
    }

    public List<Entry> getBoard(String game, String diff) {
        return boards.computeIfAbsent(key(game, diff), k -> new ArrayList<>());
    }

    public void submit(String game, String diff, UUID uuid, String name, int score) {
        if (!"easy".equals(diff) && !"normal".equals(diff) && !"hard".equals(diff)) return;
        if (score < 0 || score > 1000000) return;
        List<Entry> list = getBoard(game, diff);
        list.removeIf(e -> e.uuid.equals(uuid));
        list.add(new Entry(uuid, name, score));
        boolean asc = isAscending(game);
        list.sort((a, b) -> asc ? Integer.compare(a.score, b.score) : Integer.compare(b.score, a.score));
        while (list.size() > 10) {
            list.remove(list.size() - 1);
        }
        setDirty();
    }

    /** 全量榜单 JSON(相对请求玩家标记 isMe) */
    public String toJson(UUID requester) {
        JsonObject root = new JsonObject();
        JsonObject boardsObj = new JsonObject();
        String[] games = {"minesweeper", "memory", "g2048", "snake"};
        String[] diffs = {"easy", "normal", "hard"};
        for (String game : games) {
            JsonObject gameObj = new JsonObject();
            for (String diff : diffs) {
                JsonArray arr = new JsonArray();
                for (Entry e : getBoard(game, diff)) {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", e.name);
                    o.addProperty("score", e.score);
                    o.addProperty("isMe", e.uuid.equals(requester));
                    arr.add(o);
                }
                gameObj.add(diff, arr);
            }
            boardsObj.add(game, gameObj);
        }
        root.add("leaderboards", boardsObj);
        return root.toString();
    }

    @Override
    public CompoundTag save(CompoundTag pCompoundTag) {
        ListTag boardList = new ListTag();
        for (Map.Entry<String, List<Entry>> e : boards.entrySet()) {
            CompoundTag b = new CompoundTag();
            b.putString("Key", e.getKey());
            ListTag entries = new ListTag();
            for (Entry entry : e.getValue()) {
                CompoundTag en = new CompoundTag();
                en.putUUID("Uuid", entry.uuid);
                en.putString("Name", entry.name);
                en.putInt("Score", entry.score);
                entries.add(en);
            }
            b.put("Entries", entries);
            boardList.add(b);
        }
        pCompoundTag.put("Boards", boardList);
        return pCompoundTag;
    }

    public static LeaderboardManager load(CompoundTag nbt) {
        LeaderboardManager manager = new LeaderboardManager();
        if (nbt.contains("Boards", Tag.TAG_LIST)) {
            ListTag boardList = nbt.getList("Boards", Tag.TAG_COMPOUND);
            for (int i = 0; i < boardList.size(); i++) {
                CompoundTag b = boardList.getCompound(i);
                String key = b.getString("Key");
                List<Entry> entries = new ArrayList<>();
                if (b.contains("Entries", Tag.TAG_LIST)) {
                    ListTag el = b.getList("Entries", Tag.TAG_COMPOUND);
                    for (int j = 0; j < el.size(); j++) {
                        CompoundTag en = el.getCompound(j);
                        entries.add(new Entry(en.getUUID("Uuid"), en.getString("Name"), en.getInt("Score")));
                    }
                }
                manager.boards.put(key, entries);
            }
        }
        return manager;
    }
}
