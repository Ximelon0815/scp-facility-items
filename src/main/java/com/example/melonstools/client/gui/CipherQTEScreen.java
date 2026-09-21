package com.example.melonstools.client.gui;

import com.example.melonstools.network.StartQTEPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Random;

public class CipherQTEScreen extends BaseQTEScreen {
    private final int[] targetCipher;
    private final float[] currentScroll;
    private final float scrollSpeed;
    private int lockedColumns = 0;
    private final int cipherCount;

    private int errorTimer = 0;

    public CipherQTEScreen(StartQTEPacket packet) {
        super(packet, Component.translatable("gui.melonstools.qte.cipher.title"));

        int diff = Math.max(1, Math.min(5, (int) difficulty));
        this.cipherCount = 3 + (diff - 1) * 2;

        targetCipher = new int[cipherCount];
        currentScroll = new float[cipherCount];

        Random rand = new Random();
        for (int i = 0; i < cipherCount; i++) {
            targetCipher[i] = rand.nextInt(10); // 0-9
            currentScroll[i] = rand.nextFloat() * 10;
        }

        // 0.7 seconds per number (14 ticks per number change)
        this.scrollSpeed = 1.0f / 14.0f;
    }

    @Override
    public void tick() {
        super.tick();
        if (hasCompleted) return;

        if (errorTimer > 0) {
            errorTimer--;
            return;
        }

        for (int i = lockedColumns; i < cipherCount; i++) {
            currentScroll[i] = (currentScroll[i] + scrollSpeed) % 10.0f;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE && errorTimer == 0 && !hasCompleted && lockedColumns < cipherCount) {
            int currentNum = (int) currentScroll[lockedColumns];
            if (currentNum == targetCipher[lockedColumns]) {
                lockedColumns++;
                if (lockedColumns >= cipherCount) {
                    finishQTE(true, 1.0f);
                }
            } else {
                errorTimer = 20;
                lockedColumns = 0; // Restart
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

        int totalWidth = cipherCount * 40 - 10;
        int startX = centerX - totalWidth / 2;
        int startY = centerY - 20;

        // 面板
        drawPanel(guiGraphics, startX - 12, startY - 36, totalWidth + 24, 96);

        // 目标密码
        StringBuilder targetStr = new StringBuilder();
        for (int i = 0; i < cipherCount; i++) {
            if (i > 0) targetStr.append(" ");
            targetStr.append(targetCipher[i]);
        }
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.melonstools.qte.cipher.target", targetStr.toString()), centerX, startY - 30, COLOR_TEXT);

        for (int i = 0; i < cipherCount; i++) {
            int x = startX + i * 40;
            // 单元
            guiGraphics.fill(x - 1, startY - 1, x + 31, startY + 41, 0xFF0A0F1E);
            int cellColor = i < lockedColumns ? 0xFF14532D : (i == lockedColumns ? 0xFF0C4A6E : 0xFF1E293B);
            guiGraphics.fill(x, startY, x + 30, startY + 40, cellColor);

            if (i < lockedColumns) {
                guiGraphics.drawCenteredString(this.font, String.valueOf(targetCipher[i]), x + 15, startY + 16, COLOR_GREEN);
            } else if (i == lockedColumns && errorTimer > 0) {
                guiGraphics.drawCenteredString(this.font, "X", x + 15, startY + 16, COLOR_RED);
            } else {
                int displayNum = (int) currentScroll[i];
                guiGraphics.drawCenteredString(this.font, String.valueOf(displayNum), x + 15, startY + 16, i == lockedColumns ? COLOR_TEXT : COLOR_TEXT_DIM);
            }
        }

        Component instr = errorTimer > 0
                ? Component.translatable("gui.melonstools.qte.cipher.fail")
                : Component.translatable("gui.melonstools.qte.cipher.instr");
        drawInstruction(guiGraphics, instr, centerX, startY + 52);
    }
}