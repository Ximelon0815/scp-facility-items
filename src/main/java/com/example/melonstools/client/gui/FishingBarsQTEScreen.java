package com.example.melonstools.client.gui;

import com.example.melonstools.config.QteConfig;
import com.example.melonstools.network.StartQTEPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class FishingBarsQTEScreen extends BaseQTEScreen {
    private float fishPosition = 0.5f;
    private float fishTarget = 0.5f;
    private float playerPosition = 0.5f;
    private float playerVelocity = 0.0f;

    private float winProgress = 0.0f;
    private final float fishSize = 0.08f;
    private final float playerSize;

    public FishingBarsQTEScreen(StartQTEPacket packet) {
        super(packet, Component.translatable("gui.melonstools.qte.fishing.title"));

        float targetSizeBase = QteConfig.FISHING_TARGET_SIZE.get().floatValue();
        this.playerSize = Math.max(0.05f, targetSizeBase - (difficulty * 0.05f));
    }

    @Override
    public void tick() {
        super.tick();
        if (hasCompleted) return;

        // Fish AI
        if (Math.abs(fishPosition - fishTarget) < 0.05f || Math.random() < 0.02f) {
            fishTarget = (float) Math.random();
        }

        float fishSpeed = 0.02f + (difficulty * 0.02f);
        if (fishPosition < fishTarget) fishPosition = Math.min(fishTarget, fishPosition + fishSpeed);
        if (fishPosition > fishTarget) fishPosition = Math.max(fishTarget, fishPosition - fishSpeed);

        // Player physics
        playerPosition += playerVelocity;
        playerVelocity -= 0.005f; // Gravity

        if (playerPosition < 0.0f) {
            playerPosition = 0.0f;
            playerVelocity = 0.0f;
        }
        if (playerPosition > 1.0f) {
            playerPosition = 1.0f;
            playerVelocity = 0.0f;
        }

        // Check overlap
        boolean overlapping = Math.abs(fishPosition - playerPosition) < (playerSize / 2f + fishSize / 2f);

        if (overlapping) {
            winProgress += 0.005f;
            if (winProgress >= 1.0f) {
                finishQTE(true, 1.0f);
            }
        } else {
            winProgress = Math.max(0.0f, winProgress - 0.002f);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            playerVelocity = 0.03f; // Jump
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int barWidth = 24;
        int barHeight = 200;
        int startX = centerX - barWidth / 2;
        int startY = centerY - barHeight / 2;

        // 面板
        drawPanel(guiGraphics, startX - 20, startY - 10, barWidth + 40, barHeight + 70);

        // 主竖条背景
        guiGraphics.fill(startX, startY, startX + barWidth, startY + barHeight, COLOR_BAR_BG);
        guiGraphics.renderOutline(startX - 1, startY - 1, barWidth + 2, barHeight + 2, COLOR_PANEL_EDGE);

        // 鱼（红色目标）
        int fY = startY + barHeight - (int) (fishPosition * barHeight);
        int fH = (int) (fishSize * barHeight);
        guiGraphics.fill(startX + 2, fY - fH / 2, startX + barWidth - 2, fY + fH / 2, 0xFFEF4444);

        // 玩家游标（蓝色）
        int pY = startY + barHeight - (int) (playerPosition * barHeight);
        int pH = (int) (playerSize * barHeight);
        guiGraphics.fill(startX, pY - pH / 2, startX + barWidth, pY + pH / 2, COLOR_CYAN);
        guiGraphics.renderOutline(startX, pY - pH / 2, barWidth, pH, 0xFF0EA5E9);

        // 右侧进度条
        int progX = startX + barWidth + 16;
        drawProgressBar(guiGraphics, progX, startY, 12, barHeight, winProgress, COLOR_GREEN);
        guiGraphics.drawCenteredString(this.font, (int) (winProgress * 100) + "%", progX + 6, startY + barHeight + 6, COLOR_TEXT_DIM);

        drawInstruction(guiGraphics, Component.translatable("gui.melonstools.qte.fishing.instr1"), centerX, startY + barHeight + 26);
        drawInstruction(guiGraphics, Component.translatable("gui.melonstools.qte.fishing.instr2"), centerX, startY + barHeight + 40);
    }
}