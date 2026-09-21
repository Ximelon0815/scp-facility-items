package com.example.melonstools.client.gui;

import com.example.melonstools.config.QteConfig;
import com.example.melonstools.network.StartQTEPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class MashingQTEScreen extends BaseQTEScreen {
    private float mashCount = 0;
    private final int requiredMashes;
    private int pulse = 0;

    public MashingQTEScreen(StartQTEPacket packet) {
        super(packet, Component.translatable("gui.melonstools.qte.mashing.title"));
        int baseClicks = QteConfig.MASHING_REQUIRED_CLICKS.get();
        this.requiredMashes = Math.max(1, (int) (baseClicks * difficulty));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int barWidth = 180;
        int barHeight = 14;

        // 面板
        drawPanel(guiGraphics, centerX - barWidth / 2 - 24, centerY - 50, barWidth + 48, 110);

        drawInstruction(guiGraphics, Component.translatable("gui.melonstools.qte.mashing.instruction"), centerX, centerY - 34);

        float progress = Math.min(1.0f, mashCount / requiredMashes);
        drawProgressBar(guiGraphics, centerX - barWidth / 2, centerY - 4, barWidth, barHeight, progress, pulse > 0 ? COLOR_YELLOW : COLOR_CYAN);

        // 计数
        String counter = (int) mashCount + " / " + requiredMashes;
        guiGraphics.drawCenteredString(this.font, counter, centerX, centerY + 16, COLOR_TEXT);
    }

    @Override
    public void tick() {
        super.tick();
        if (hasCompleted) return;
        if (pulse > 0) pulse--;

        float mashDecay = 0.2f + (0.2f * difficulty);
        mashCount = Math.max(0, mashCount - mashDecay);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            handleMash();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            handleMash();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleMash() {
        mashCount += 8;
        pulse = 4;
        if (mashCount >= requiredMashes) {
            finishQTE(true, 1.0f);
        }
    }
}





