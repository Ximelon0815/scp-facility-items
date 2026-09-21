package com.example.melonstools.client.gui;

import com.example.melonstools.network.StartQTEPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class BalanceQTEScreen extends BaseQTEScreen {
    private float cursorPosition = 0.5f; // 0.0 to 1.0
    private float cursorVelocity = 0.0f;
    private float winProgress = 0.0f; // 0.0 to 1.0
    private float balWindForce;

    private final float safeZoneWidth;
    private final float safeZoneMin;
    private final float safeZoneMax;

    public BalanceQTEScreen(StartQTEPacket packet) {
        super(packet, Component.translatable("gui.melonstools.qte.balance.title"));

        this.balWindForce = 0.5f + difficulty * 0.1f;
        this.safeZoneWidth = Math.max(0.1f, 0.35f - (difficulty - 1) * 0.06f);
        this.safeZoneMin = 0.5f - (safeZoneWidth / 2f);
        this.safeZoneMax = 0.5f + (safeZoneWidth / 2f);
    }

    @Override
    public void tick() {
        super.tick();
        if (hasCompleted) return;

        float timeScale = 0.05f;
        cursorVelocity += (Math.random() - 0.5f) * balWindForce * timeScale;

        cursorVelocity *= 0.9f;

        cursorPosition += cursorVelocity;

        if (cursorPosition < 0.0f || cursorPosition > 1.0f) {
            cursorPosition = 0.5f;
            cursorVelocity = 0.0f;
            winProgress = 0.0f;
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS, 1.0F));
            }
            return;
        }

        if (cursorPosition >= safeZoneMin && cursorPosition <= safeZoneMax) {
            winProgress += 0.02f;
            if (winProgress >= 1.0f) {
                finishQTE(true, 1.0f);
            }
        } else {
            winProgress = Math.max(0.0f, winProgress - 0.01f);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_A || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            cursorVelocity -= 0.05f;
            return true;
        } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_D || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            cursorVelocity += 0.05f;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int barWidth = 220;
        int barHeight = 22;
        int startX = centerX - barWidth / 2;
        int startY = centerY - 11;

        // 面板
        drawPanel(guiGraphics, startX - 24, startY - 40, barWidth + 48, 120);

        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.melonstools.qte.balance.progress", (int) (winProgress * 100)), centerX, startY - 30, COLOR_TEXT);

        guiGraphics.fill(startX, startY, startX + barWidth, startY + barHeight, 0xFF0F172A);
        guiGraphics.renderOutline(startX - 1, startY - 1, barWidth + 2, barHeight + 2, COLOR_PANEL_EDGE);

        // 安全区（暗绿）+ 中心线（亮绿）
        int safeX = startX + (int) (safeZoneMin * barWidth);
        int safeWidth = (int) (safeZoneWidth * barWidth);
        guiGraphics.fill(safeX, startY, safeX + safeWidth, startY + barHeight, 0xFF166534);
        int midX = startX + (int) (0.5f * barWidth);
        guiGraphics.fill(midX - 1, startY, midX + 1, startY + barHeight, COLOR_GREEN);

        // 游标
        int cursorX = startX + (int) (cursorPosition * barWidth);
        guiGraphics.fill(cursorX - 2, startY - 6, cursorX + 2, startY + barHeight + 6, 0xFFFFFFFF);

        // 下方进度条
        drawProgressBar(guiGraphics, centerX - 80, startY + barHeight + 12, 160, 8, winProgress, COLOR_GREEN);

        drawInstruction(guiGraphics, Component.translatable("gui.melonstools.qte.balance.instr"), centerX, startY + barHeight + 30);
    }
}