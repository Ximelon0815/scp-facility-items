package com.example.melonstools.client.gui;

import com.example.melonstools.config.QteConfig;
import com.example.melonstools.network.StartQTEPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Random;

public class DBDSkillCheckQTEScreen extends BaseQTEScreen {
    private final float successZoneStart;
    private final float successZoneEnd;
    private final float greatZoneStart;
    private final float greatZoneEnd;
    private final Random random = new Random();
    private float cursorPosition = 0.0f;
    private final float cursorSpeed;

    public DBDSkillCheckQTEScreen(StartQTEPacket packet) {
        super(packet, Component.translatable("gui.melonstools.qte.dbd.title"));

        float baseSuccessSize = QteConfig.DBD_SUCCESS_SIZE.get().floatValue();
        float successSize = Math.max(0.05f, baseSuccessSize - (difficulty * 0.05f));
        float greatSize = successSize * 0.2f;

        this.successZoneStart = 0.2f + random.nextFloat() * 0.5f;
        this.successZoneEnd = this.successZoneStart + successSize;

        this.greatZoneStart = this.successZoneStart + (successSize / 2f) - (greatSize / 2f);
        this.greatZoneEnd = this.greatZoneStart + greatSize;

        float baseSpeed = 0.02f;
        this.cursorSpeed = baseSpeed + (difficulty * 0.005f);
    }

    @Override
    public void tick() {
        super.tick();
        if (hasCompleted) return;

        cursorPosition += cursorSpeed;
        if (cursorPosition > 1.0f) {
            cursorPosition = 0.0f;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            if (cursorPosition >= greatZoneStart && cursorPosition <= greatZoneEnd) {
                finishQTE(true, 1.0f);
            } else if (cursorPosition >= successZoneStart && cursorPosition <= successZoneEnd) {
                finishQTE(true, 0.5f);
            } else {
                cursorPosition = 0.0f;
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

        int barWidth = 300;
        int barHeight = 24;
        int startX = centerX - barWidth / 2;
        int startY = centerY - 12;

        // 面板
        drawPanel(guiGraphics, startX - 24, startY - 40, barWidth + 48, barHeight + 90);

        // 条背景
        guiGraphics.fill(startX, startY, startX + barWidth, startY + barHeight, 0xFF0F172A);
        guiGraphics.renderOutline(startX - 1, startY - 1, barWidth + 2, barHeight + 2, COLOR_PANEL_EDGE);

        // 普通成功区（暗绿）
        int sx1 = startX + (int) (successZoneStart * barWidth);
        int sx2 = startX + (int) (successZoneEnd * barWidth);
        guiGraphics.fill(sx1, startY, sx2, startY + barHeight, 0xFF166534);

        // 完美区（亮绿）
        int gx1 = startX + (int) (greatZoneStart * barWidth);
        int gx2 = startX + (int) (greatZoneEnd * barWidth);
        guiGraphics.fill(gx1, startY, gx2, startY + barHeight, 0xFF22C55E);

        // 每 10% 刻度线
        for (int i = 1; i < 10; i++) {
            int tx = startX + (int) (barWidth * i / 10f);
            guiGraphics.fill(tx, startY, tx + 1, startY + barHeight, 0x33000000);
        }

        // 指针
        int cursorX = startX + (int) (cursorPosition * barWidth);
        guiGraphics.fill(cursorX - 6, startY - 12, cursorX + 6, startY - 8, 0xFFFFFFFF);
        guiGraphics.fill(cursorX - 2, startY - 6, cursorX + 2, startY + barHeight + 6, 0xFFFFFFFF);

        drawInstruction(guiGraphics, Component.translatable("gui.melonstools.qte.dbd.instr"), centerX, startY + barHeight + 22);
    }
}