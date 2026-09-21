package com.example.melonstools.idcard.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public class IdentityCardCurioRenderer implements ICurioRenderer {

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack matrixStack, RenderLayerParent<T, M> renderLayerParent, MultiBufferSource renderTypeBuffer, int light, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity entity = slotContext.entity();
        
        if (!(renderLayerParent.getModel() instanceof HumanoidModel<?> humanoidModel)) {
            return;
        }

        matrixStack.pushPose();
        
        // 鐠虹喖娈㈤悳鈺侇啀闊垰鍏辨潪顒€濮?
        humanoidModel.body.translateAndRotate(matrixStack);

        // 楠炲磭些閸掓澘涔忛懗闀愮秴缂? X(瀹革箑褰?, Y(娑撳﹣绗?, Z(閸撳秴鎮?
        matrixStack.translate(0.12, 0.175, -0.16); 
        
        // 缂傗晛鐨崚鏉夸紣閻楀苯銇囩亸?
        matrixStack.scale(0.25f, 0.25f, 0.25f);
        
        // 閺冨娴嗛悧鈺佹惂娴ｅ灝鍙惧锝夋桨閺堟繂顦?
        matrixStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        matrixStack.mulPose(Axis.ZP.rotationDegrees(180.0F));

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, matrixStack, renderTypeBuffer, entity.level(), 0);

        matrixStack.popPose();
    }
}