package com.example.melonstools.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class ChatRoomManager extends SavedData {

    public static class ChatMessage {
        public UUID sender;
        public String senderName;
        public long timestamp;
        public String content;

        public ChatMessage(UUID sender, String senderName, long timestamp, String content) {
            this.sender = sender;
            this.senderName = senderName;
            this.timestamp = timestamp;
            this.content = content;
        }

        public CompoundTag save() {
            CompoundTag nbt = new CompoundTag();
            if (sender != null) nbt.putUUID("Sender", sender);
            nbt.putString("SenderName", senderName);
            nbt.putLong("Timestamp", timestamp);
            nbt.putString("Content", content);
            return nbt;
        }

        public static ChatMessage load(CompoundTag nbt) {
            return new ChatMessage(
                nbt.contains("Sender") ? nbt.getUUID("Sender") : null,
                nbt.getString("SenderName"),
                nbt.getLong("Timestamp"),
                nbt.getString("Content")
            );
        }
    }

    public static class ChatRoom {
        public String roomId;
        public String roomName;
        public UUID owner;
        public String avatar = ""; // 群头像物品注册名(可为空)
        public Set<UUID> members = new HashSet<>();
        public LinkedList<ChatMessage> messages = new LinkedList<>();
        // 各玩家最后已读时间戳(用于主页重要消息去重:已读的 @ 不再提示)
        public Map<UUID, Long> lastRead = new HashMap<>();

        public ChatRoom(String roomId, String roomName, UUID owner) {
            this(roomId, roomName, owner, "");
        }

        public ChatRoom(String roomId, String roomName, UUID owner, String avatar) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.owner = owner;
            this.avatar = avatar == null ? "" : avatar;
            if (owner != null) {
                this.members.add(owner);
            }
        }

        public void addMessage(ChatMessage message) {
            messages.add(message);
            if (messages.size() > 50) {
                messages.removeFirst();
            }
        }

        public CompoundTag save() {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("RoomId", roomId);
            nbt.putString("RoomName", roomName);
            if (owner != null) nbt.putUUID("Owner", owner);

            ListTag membersList = new ListTag();
            for (UUID uuid : members) {
                membersList.add(StringTag.valueOf(uuid.toString()));
            }
            nbt.put("Members", membersList);

            nbt.putString("Avatar", avatar == null ? "" : avatar);

            ListTag messagesList = new ListTag();
            for (ChatMessage msg : messages) {
                messagesList.add(msg.save());
            }
            nbt.put("Messages", messagesList);

            ListTag lastReadList = new ListTag();
            for (Map.Entry<UUID, Long> e : lastRead.entrySet()) {
                CompoundTag lr = new CompoundTag();
                lr.putUUID("Player", e.getKey());
                lr.putLong("Time", e.getValue());
                lastReadList.add(lr);
            }
            nbt.put("LastRead", lastReadList);
            return nbt;
        }

        public static ChatRoom load(CompoundTag nbt) {
            String roomId = nbt.getString("RoomId");
            String roomName = nbt.getString("RoomName");
            UUID owner = nbt.contains("Owner") ? nbt.getUUID("Owner") : null;
            ChatRoom room = new ChatRoom(roomId, roomName, owner);
            room.avatar = nbt.getString("Avatar");

            if (nbt.contains("Members", Tag.TAG_LIST)) {
                ListTag membersList = nbt.getList("Members", Tag.TAG_STRING);
                for (int i = 0; i < membersList.size(); i++) {
                    room.members.add(UUID.fromString(membersList.getString(i)));
                }
            }

            if (nbt.contains("Messages", Tag.TAG_LIST)) {
                ListTag messagesList = nbt.getList("Messages", Tag.TAG_COMPOUND);
                for (int i = 0; i < messagesList.size(); i++) {
                    room.messages.add(ChatMessage.load(messagesList.getCompound(i)));
                }
            }

            if (nbt.contains("LastRead", Tag.TAG_LIST)) {
                ListTag lastReadList = nbt.getList("LastRead", Tag.TAG_COMPOUND);
                for (int i = 0; i < lastReadList.size(); i++) {
                    CompoundTag lr = lastReadList.getCompound(i);
                    room.lastRead.put(lr.getUUID("Player"), lr.getLong("Time"));
                }
            }

            return room;
        }
    }

    private final Map<String, ChatRoom> rooms = new HashMap<>();

    public ChatRoomManager() {}

    public ChatRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /** 返回指定玩家所在的所有聊天室 */
    public List<ChatRoom> getRoomsOfPlayer(UUID player) {
        List<ChatRoom> out = new ArrayList<>();
        for (ChatRoom room : rooms.values()) {
            if (room.members.contains(player)) {
                out.add(room);
            }
        }
        return out;
    }

    public Collection<ChatRoom> getAllRooms() {
        return rooms.values();
    }

    /** 按名称查找聊天室(忽略大小写) */
    public ChatRoom findRoomByName(String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase();
        for (ChatRoom room : rooms.values()) {
            if (room.roomName != null && room.roomName.trim().toLowerCase().equals(key)) {
                return room;
            }
        }
        return null;
    }

    public void addRoom(ChatRoom room) {
        rooms.put(room.roomId, room);
        setDirty();
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag pCompoundTag) {
        ListTag roomsList = new ListTag();
        for (ChatRoom room : rooms.values()) {
            roomsList.add(room.save());
        }
        pCompoundTag.put("ChatRooms", roomsList);
        return pCompoundTag;
    }

    public static ChatRoomManager load(CompoundTag pCompoundTag) {
        ChatRoomManager manager = new ChatRoomManager();
        if (pCompoundTag.contains("ChatRooms", Tag.TAG_LIST)) {
            ListTag roomsList = pCompoundTag.getList("ChatRooms", Tag.TAG_COMPOUND);
            for (int i = 0; i < roomsList.size(); i++) {
                ChatRoom room = ChatRoom.load(roomsList.getCompound(i));
                manager.rooms.put(room.roomId, room);
            }
        }
        return manager;
    }

    public static ChatRoomManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ChatRoomManager::load, ChatRoomManager::new, "qte_chat_rooms");
    }
}
