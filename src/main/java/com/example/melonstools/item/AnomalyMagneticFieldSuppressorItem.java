package com.example.melonstools.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 异常磁场抑制器。
 * <p>
 * 玩法定位: 玩家扮演型异常物的身份饰品/项圈。当前阶段先作为可注册、可贴图、可记录绑定 NBT 的物品落地;
 * 后续接 Curios 槽位、快捷键界面、服务端稳定度/好感度骰子系统。
 */
public class AnomalyMagneticFieldSuppressorItem extends Item {
    public static final String TAG_ANOMALY_ID = "AnomalyId";
    public static final String TAG_ANOMALY_INSTANCE_ID = "AnomalyInstanceId";
    public static final String TAG_ROOM_ID = "RoomId";
    public static final String TAG_BOUND_POINT_ID = "BoundPointId";

    public AnomalyMagneticFieldSuppressorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.melonstools.anomaly_magnetic_field_suppressor.desc").withStyle(ChatFormatting.DARK_GRAY));
        if (stack.hasTag()) {
            var tag = stack.getTag();
            if (tag.contains(TAG_ANOMALY_ID)) {
                tooltip.add(Component.translatable("tooltip.melonstools.anomaly_magnetic_field_suppressor.anomaly", tag.getString(TAG_ANOMALY_ID)).withStyle(ChatFormatting.GRAY));
            }
            if (tag.contains(TAG_ANOMALY_INSTANCE_ID)) {
                tooltip.add(Component.translatable("tooltip.melonstools.anomaly_magnetic_field_suppressor.instance", tag.getString(TAG_ANOMALY_INSTANCE_ID)).withStyle(ChatFormatting.GRAY));
            }
            if (tag.contains(TAG_ROOM_ID)) {
                tooltip.add(Component.translatable("tooltip.melonstools.anomaly_magnetic_field_suppressor.room", tag.getString(TAG_ROOM_ID)).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.melonstools.anomaly_magnetic_field_suppressor.unbound").withStyle(ChatFormatting.RED));
        }
    }
}
