package com.example.melonstools.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerPhone implements IPlayerPhone {
    private final List<UUID> friends = new ArrayList<>();
    private final List<UUID> pendingRequests = new ArrayList<>();
    /** 当前玩家自己的 UUID(attach 时回填), 用于自我加好友防御 */
    private java.lang.ref.WeakReference<UUID> selfUuidRef = null;

    @Override
    public List<UUID> getFriends() {
        return friends;
    }

    @Override
    public void addFriend(UUID uuid) {
        // 防御: 不能把自己加为好友(单人世界/异常请求路径会触发, 且玩家 NBT 跨世界共享, 脏数据会残留)
        if (uuid == null || uuid.equals(selfUuid())) return;
        if (!friends.contains(uuid)) {
            friends.add(uuid);
        }
    }

    @Override
    public void addPendingRequest(UUID uuid) {
        // 防御: 不能向自己发起好友请求
        if (uuid == null || uuid.equals(selfUuid())) return;
        if (!pendingRequests.contains(uuid)) {
            pendingRequests.add(uuid);
        }
    }

    /** 当前玩家自己的 UUID(capability 挂载在玩家上时通过 provider 回填; 未知时返回 null) */
    private UUID selfUuid() {
        return selfUuidRef == null ? null : selfUuidRef.get();
    }

    /** 由 attach 时回填当前玩家 UUID, 用于自我加好友防御 */
    public void setSelfUuid(UUID uuid) {
        this.selfUuidRef = uuid == null ? null : new java.lang.ref.WeakReference<>(uuid);
    }

    @Override
    public void removeFriend(UUID uuid) {
        friends.remove(uuid);
    }

    @Override
    public boolean isFriend(UUID uuid) {
        return friends.contains(uuid);
    }

    @Override
    public List<UUID> getPendingRequests() {
        return pendingRequests;
    }

    @Override
    public void removePendingRequest(UUID uuid) {
        pendingRequests.remove(uuid);
    }

    public void saveNBTData(CompoundTag nbt) {
        ListTag friendsList = new ListTag();
        for (UUID uuid : friends) {
            friendsList.add(StringTag.valueOf(uuid.toString()));
        }
        nbt.put("Friends", friendsList);

        ListTag pendingList = new ListTag();
        for (UUID uuid : pendingRequests) {
            pendingList.add(StringTag.valueOf(uuid.toString()));
        }
        nbt.put("PendingRequests", pendingList);
    }

    public void loadNBTData(CompoundTag nbt) {
        friends.clear();
        UUID self = selfUuid();
        if (nbt.contains("Friends", Tag.TAG_LIST)) {
            ListTag friendsList = nbt.getList("Friends", Tag.TAG_STRING);
            for (int i = 0; i < friendsList.size(); i++) {
                try {
                    UUID u = UUID.fromString(friendsList.getString(i));
                    // 防御: 加载时也过滤掉"自己"(清理历史脏数据)
                    if (u == null || u.equals(self)) continue;
                    friends.add(u);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        pendingRequests.clear();
        if (nbt.contains("PendingRequests", Tag.TAG_LIST)) {
            ListTag pendingList = nbt.getList("PendingRequests", Tag.TAG_STRING);
            for (int i = 0; i < pendingList.size(); i++) {
                try {
                    UUID u = UUID.fromString(pendingList.getString(i));
                    if (u == null || u.equals(self)) continue;
                    pendingRequests.add(u);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }
}
