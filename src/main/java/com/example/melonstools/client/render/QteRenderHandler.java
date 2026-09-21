package com.example.melonstools.client.render;

import com.example.melonstools.MelonsTools;
import com.example.melonstools.item.ModItems;
import com.example.melonstools.qte.QTEBlockManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = MelonsTools.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class QteRenderHandler {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Player player = Minecraft.getInstance().player;
        if (player == null || !player.getMainHandItem().is(ModItems.QTE_BINDER.get())) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        VertexConsumer vertexConsumer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        List<BlockPos> boundBlocks = QTEBlockManager.getClientBoundBlocks();
        for (BlockPos pos : boundBlocks) {
            AABB aabb = new AABB(pos);
            LevelRenderer.renderLineBox(poseStack, vertexConsumer, aabb, 0.0f, 1.0f, 0.0f, 1.0f); // Green
        }

        poseStack.popPose();
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(RenderType.lines());
    }
}
