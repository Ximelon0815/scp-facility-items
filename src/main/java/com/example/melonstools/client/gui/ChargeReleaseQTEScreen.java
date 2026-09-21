package com.example.melonstools.client.gui;

import com.example.melonstools.network.StartQTEPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class ChargeReleaseQTEScreen extends BaseQTEScreen {
    private float charge = 0.0f; // 0.0 to 1.0
    private boolean isCharging = false;
    private final float chargeRate;

    private final float successMin;
    private final float successMax;
    private final float targetSize;

    public ChargeReleaseQTEScreen(StartQTEPacket packet) {
        super(packet, Component.translatable("gui.melonstools.qte.charge.title"));

        this.chargeRate = 1.0f + (difficulty * 0.5f);
        this.targetSize = Math.max(5.0f, 20.0f - (difficulty - 1) * 3.0f);
        float center = 85.0f;
        this.successMin = (center - targetSize / 2f) / 100f;
        this.successMax = (center + targetSize / 2f) / 100f;
    }

    @Override
    public void tick() {
        super.tick();
        if (hasCompleted) return;

        if (isCharging) {
            charge += (chargeRate / 100f); // Adjust speed
            if (charge >= 1.0f) {
                charge = 1.0f;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            if (!isCharging) {
                isCharging = true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            if (isCharging) {
                isCharging = false;
                if (charge >= successMin && charge <= successMax) {
                    finishQTE(true, 1.0f);
                } else {
                    charge = 0.0f;
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS, 1.0F));
                    }
                }
            }
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int barWidth = 40;
        int barHeight = 200;
        int startX = centerX - barWidth / 2;
        int startY = centerY - barHeight / 2;

        // 面板
        drawPanel(guiGraphics, startX - 20, startY - 10, barWidth + 40, barHeight + 70);

        guiGraphics.fill(startX, startY, startX + barWidth, startY + barHeight, 0xFF0F172A);
        guiGraphics.renderOutline(startX - 1, startY - 1, barWidth + 2, barHeight + 2, COLOR_PANEL_EDGE);

        // 成功区域
        int safeY1 = startY + barHeight - (int) (successMax * barHeight);
        int safeY2 = startY + barHeight - (int) (successMin * barHeight);
        guiGraphics.fill(startX, safeY1, startX + barWidth, safeY2, 0xFF166534);
        guiGraphics.renderOutline(startX, safeY1, barWidth, safeY2 - safeY1, COLOR_GREEN);

        // 充能填充
        int chargeY = startY + barHeight - (int) (charge * barHeight);
        guiGraphics.fill(startX + 4, chargeY, startX + barWidth - 4, startY + barHeight, COLOR_CYAN);

        // 百分比
        guiGraphics.drawCenteredString(this.font, (int) (charge * 100) + "%", centerX, startY + barHeight + 8, isCharging ? COLOR_CYAN : COLOR_TEXT_DIM);

        drawInstruction(guiGraphics, Component.translatable("gui.melonstools.qte.charge.instr1"), centerX, startY + barHeight + 26);
        drawInstruction(guiGraphics, Component.translatable("gui.melonstools.qte.charge.instr2"), centerX, startY + barHeight + 40);
    }
}