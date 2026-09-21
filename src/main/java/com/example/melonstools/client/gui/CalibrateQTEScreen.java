package com.example.melonstools.client.gui;

import com.example.melonstools.network.StartQTEPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class CalibrateQTEScreen extends BaseQTEScreen {
    private float targetPosition = 0.5f;
    private float cursorPosition = 0.0f;
    private float cursorSpeed;
    private int direction = 1;

    private final float successZoneWidth;
    private int errorTimer = 0;

    public CalibrateQTEScreen(StartQTEPacket packet) {
        super(packet, Component.translatable("gui.melonstools.qte.calibrate.title"));

        this.cursorSpeed = 0.02f + (difficulty * 0.015f);
        this.successZoneWidth = Math.max(0.05f, 0.3f - (difficulty * 0.05f));

        this.targetPosition = 0.2f + (float) Math.random() * 0.6f;
    }

    @Override
    public void tick() {
        super.tick();
        if (hasCompleted) return;

        if (errorTimer > 0) {
            errorTimer--;
            return;
        }

        cursorPosition += cursorSpeed * direction;
        if (cursorPosition >= 1.0f) {
            cursorPosition = 1.0f;
            direction = -1;
        } else if (cursorPosition <= 0.0f) {
            cursorPosition = 0.0f;
            direction = 1;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE && errorTimer == 0 && !hasCompleted) {
            float dist = Math.abs(cursorPosition - targetPosition);
            if (dist <= successZoneWidth / 2f) {
                finishQTE(true, 1.0f);
            } else {
                errorTimer = 20;
                cursorPosition = 0.0f;
                direction = 1;
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS, 1.0F));
                }
            }
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
        drawPanel(guiGraphics, startX - 24, startY - 40, barWidth + 48, 110);

        guiGraphics.fill(startX, startY, startX + barWidth, startY + barHeight, 0xFF0F172A);
        guiGraphics.renderOutline(startX - 1, startY - 1, barWidth + 2, barHeight + 2, COLOR_PANEL_EDGE);

        // 目标区
        int targetX = startX + (int) (targetPosition * barWidth);
        int targetW = (int) (successZoneWidth * barWidth);
        guiGraphics.fill(targetX - targetW / 2, startY, targetX + targetW / 2, startY + barHeight, 0xFF166534);
        guiGraphics.fill(targetX - 1, startY - 3, targetX + 1, startY + barHeight + 3, COLOR_GREEN);

        // 游标
        int cursorX = startX + (int) (cursorPosition * barWidth);
        int color = errorTimer > 0 ? COLOR_RED : COLOR_TEXT;
        guiGraphics.fill(cursorX - 2, startY - 6, cursorX + 2, startY + barHeight + 6, color);

        Component instr = errorTimer > 0
                ? Component.translatable("gui.melonstools.qte.calibrate.fail")
                : Component.translatable("gui.melonstools.qte.calibrate.instr");
        drawInstruction(guiGraphics, instr, centerX, startY + barHeight + 24);
    }
}