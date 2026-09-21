package com.example.melonstools.client.gui;

import com.example.melonstools.network.StartQTEPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MemoryQTEScreen extends BaseQTEScreen {
    private final List<Integer> sequence = new ArrayList<>();
    private int playerIndex = 0;

    // States: 0 = showing sequence, 1 = player input
    private int state = 0;
    private int sequenceTick = 0; // ticks spent showing sequence
    private final int memSpeedTicks; // Ticks per show
    private int flashIndex = -1;
    private int errorTimer = 0;

    public MemoryQTEScreen(StartQTEPacket packet) {
        super(packet, Component.translatable("gui.melonstools.qte.memory.title"));

        int length = Math.max(3, 3 + (int) difficulty); // Diff 1: 4, Diff 5: 8
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sequence.add(random.nextInt(9)); // 9 buttons
        }
        this.memSpeedTicks = 14; // 700ms = 14 ticks
    }

    @Override
    public void tick() {
        super.tick();
        if (hasCompleted) return;

        if (errorTimer > 0) {
            errorTimer--;
            return;
        }

        if (state == 0) {
            sequenceTick++;
            int idx = sequenceTick / memSpeedTicks;
            if (idx >= sequence.size()) {
                state = 1;
                flashIndex = -1;
            } else {
                // Flash the button for half the duration
                if (sequenceTick % memSpeedTicks < memSpeedTicks / 2) {
                    flashIndex = sequence.get(idx);
                } else {
                    flashIndex = -1;
                }
            }
        } else {
            flashIndex = -1;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && state == 1 && errorTimer == 0 && !hasCompleted) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int startX = centerX - 66;
            int startY = centerY - 40;

            for (int i = 0; i < 9; i++) {
                int col = i % 3;
                int row = i / 3;
                int bx = startX + col * 46;
                int by = startY + row * 46;

                if (mouseX >= bx && mouseX < bx + 36 && mouseY >= by && mouseY < by + 36) {
                    if (sequence.get(playerIndex) == i) {
                        playerIndex++;
                        if (playerIndex >= sequence.size()) {
                            finishQTE(true, 1.0f);
                        }
                    } else {
                        // Fail
                        errorTimer = 20;
                        playerIndex = 0;
                        state = 0;
                        sequenceTick = 0;
                        if (this.minecraft != null) {
                            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS, 1.0F));
                        }
                        // Or just fail QTE entirely depending on config. Let's restart it for leniency
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startX = centerX - 66;
        int startY = centerY - 40;

        // 面板
        drawPanel(guiGraphics, startX - 12, startY - 34, 36 * 3 + 46 * 2 + 24, 36 * 3 + 46 * 2 + 70);

        for (int i = 0; i < 9; i++) {
            int col = i % 3;
            int row = i / 3;
            int bx = startX + col * 46;
            int by = startY + row * 46;

            int color = 0xFF334155; // Default idle

            if (state == 0 && flashIndex == i) {
                color = COLOR_YELLOW; // Flashing
            } else if (state == 1) {
                if (errorTimer > 0) {
                    color = 0xFF7F1D1D; // Error red
                } else if (mouseX >= bx && mouseX < bx + 36 && mouseY >= by && mouseY < by + 36) {
                    color = 0xFF475569; // Hover
                }
            }

            // 带边框和编号的按钮
            guiGraphics.fill(bx - 1, by - 1, bx + 37, by + 37, 0xFF0A0F1E);
            guiGraphics.fill(bx, by, bx + 36, by + 36, color);
            int numColor = (state == 0 && flashIndex == i) ? 0xFF1E293B : COLOR_TEXT;
            guiGraphics.drawCenteredString(this.font, String.valueOf(i + 1), bx + 18, by + 12, numColor);
        }

        Component instr;
        if (errorTimer > 0) {
            instr = Component.translatable("gui.melonstools.qte.memory.wrong");
        } else if (state == 0) {
            instr = Component.translatable("gui.melonstools.qte.memory.watch");
        } else {
            instr = Component.translatable("gui.melonstools.qte.memory.repeat");
        }
        drawInstruction(guiGraphics, instr, centerX, startY + 36 * 3 + 46 * 2 + 20);
    }
}