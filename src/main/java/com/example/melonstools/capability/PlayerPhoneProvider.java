package com.example.melonstools.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerPhoneProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static final Capability<PlayerPhone> PLAYER_PHONE = CapabilityManager.get(new CapabilityToken<PlayerPhone>() { });

    private PlayerPhone phone = null;
    private final LazyOptional<PlayerPhone> optional = LazyOptional.of(this::createPhone);

    private PlayerPhone createPhone() {
        if (this.phone == null) {
            this.phone = new PlayerPhone();
        }
        return this.phone;
    }

    /** 用当前玩家的 UUID 初始化 capability(自我加好友防御需要知道"我"是谁) */
    public void setSelfUuid(java.util.UUID uuid) {
        createPhone().setSelfUuid(uuid);
    }

    public static LazyOptional<PlayerPhone> getPhone(net.minecraft.world.entity.player.Player player) {
        return player.getCapability(PLAYER_PHONE);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == PLAYER_PHONE) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        createPhone().saveNBTData(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createPhone().loadNBTData(nbt);
    }
}
