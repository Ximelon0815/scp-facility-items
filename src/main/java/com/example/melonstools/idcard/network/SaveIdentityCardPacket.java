package com.example.melonstools.idcard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import com.example.melonstools.idcard.item.ModItems;
import net.minecraft.nbt.CompoundTag;

import java.util.function.Supplier;

public class SaveIdentityCardPacket {
    private final String name;
    private final String department;
    private final String position;
    private final int cardLevel;
    private final String bindName;
    private final String bankBindName;

    public SaveIdentityCardPacket(String name, String department, String position, int cardLevel, String bindName) {
        this(name, department, position, cardLevel, bindName, "");
    }

    public SaveIdentityCardPacket(String name, String department, String position, int cardLevel, String bindName, String bankBindName) {
        this.name = name;
        this.department = department;
        this.position = position;
        this.cardLevel = cardLevel;
        this.bindName = bindName;
        this.bankBindName = bankBindName;
    }

    public SaveIdentityCardPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf(256);
        this.department = buf.readUtf(256);
        this.position = buf.readUtf(256);
        this.cardLevel = buf.readInt();
        this.bindName = buf.readUtf(16);
        this.bankBindName = buf.readUtf(16);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.name, 256);
        buf.writeUtf(this.department, 256);
        buf.writeUtf(this.position, 256);
        buf.writeInt(this.cardLevel);
        buf.writeUtf(this.bindName, 16);
        buf.writeUtf(this.bankBindName, 16);
    }

    public static SaveIdentityCardPacket decode(FriendlyByteBuf buf) {
        return new SaveIdentityCardPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) { // Only OP can save
                ItemStack mainHandItem = player.getMainHandItem();
                // Check if the item in hand is our Identity Card
                if (mainHandItem.getItem() == ModItems.IDENTITY_CARD.get()) {
                    CompoundTag tag = mainHandItem.getOrCreateTag();
                    if (this.name != null && !this.name.isBlank()) {
                        tag.putString("CardName", this.name);
                    } else {
                        tag.remove("CardName");
                    }
                    String requestedDept = this.department == null || this.department.isBlank() ? "未知" : this.department;
                    String requestedPos = this.position;
                    int requestedLevel = this.cardLevel;
                    if (com.example.melonstools.department.DepartmentPolicy.isLegacyTemporaryDepartment(requestedDept)) {
                        requestedDept = com.example.melonstools.department.DepartmentPolicy.DEPT_LOGISTICS;
                        requestedPos = com.example.melonstools.department.DepartmentPolicy.POS_TEMP;
                        requestedLevel = 0;
                    }
                    tag.putString("CardDepartment", requestedDept);
                    if (requestedPos != null && !requestedPos.isBlank()) {
                        tag.putString("CardPosition", requestedPos);
                    } else {
                        tag.remove("CardPosition");
                    }
                    tag.putInt("CardLevel", requestedLevel);
                    if (this.bindName != null && !this.bindName.isEmpty()) {
                        tag.putString("CardSkinName", this.bindName);
                    } else {
                        tag.remove("CardSkinName");
                    }

                    // 银行卡绑定(与皮肤绑定 CardSkinName 独立)
                    // 仅在 LC 加载时写入; 未加载则清除绑定, 避免残留脏数据
                    if (com.example.melonstools.idcard.LCIntegration.isLCLoaded()) {
                        if (this.bankBindName != null && !this.bankBindName.isEmpty()) {
                            // 手动绑定: 解析输入玩家名为 UUID
                            java.util.UUID bound = com.example.melonstools.idcard.LCIntegration.resolvePlayerUUID(player.getServer(), this.bankBindName);
                            if (bound != null) {
                                tag.putString("CardBankUUID", bound.toString());
                            } else {
                                // 玩家名解析失败: 回退自动绑定当前持卡人
                                tag.putString("CardBankUUID", player.getUUID().toString());
                            }
                        } else {
                            // 未填: 自动绑定当前持卡人账户
                            tag.putString("CardBankUUID", player.getUUID().toString());
                        }
                    } else {
                        tag.remove("CardBankUUID");
                    }

                    mainHandItem.setTag(tag);
                    com.example.melonstools.data.OnlineStaffCache.refresh(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}