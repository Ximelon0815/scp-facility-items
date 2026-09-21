package com.example.melonstools.capability;

import net.minecraft.nbt.CompoundTag;
import java.util.List;
import java.util.UUID;

public interface IPlayerPhone {
    List<UUID> getFriends();
    void addFriend(UUID uuid);
    void removeFriend(UUID uuid);
    boolean isFriend(UUID uuid);

    List<UUID> getPendingRequests();
    void addPendingRequest(UUID uuid);
    void removePendingRequest(UUID uuid);

    void saveNBTData(CompoundTag nbt);
    void loadNBTData(CompoundTag nbt);
}
