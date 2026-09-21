package com.example.melonstools.client.gui;

import com.example.melonstools.MelonsTools;
import com.example.melonstools.network.StartQTEPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SequenceQTEScreen extends BaseQTEScreen {
    private enum Direction {
        UP("UP", org.lwjgl.glfw.GLFW.GLFW_KEY_W, org.lwjgl.glfw.GLFW.GLFW_KEY_UP, new ResourceLocation(MelonsTools.MODID, "textures/gui/w.png")),
        DOWN("DOWN", org.lwjgl.glfw.GLFW.GLFW_KEY_S, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, new ResourceLocation(MelonsTools.MODID, "textures/gui/s.png")),
        LEFT("LEFT", org.lwjgl.glfw.GLFW.GLFW_KEY_A, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, new ResourceLocation(MelonsTools.MODID, "textures/gui/a.png")),
        RIGHT("RIGHT", org.lwjgl.glfw.GLFW.GLFW_KEY_D, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, new ResourceLocation(MelonsTools.MODID, "textures/gui/d.png"));

        final String symbol;
        final int key1;
        final int key2;
        final ResourceLocation texture;

        Direction(String symbol, int key1, int key2, ResourceLocation texture) {
            this.symbol = symbol;
            this.key1 = key1;
            this.key2 = key2;
            this.texture = texture;
        }

        boolean matches(int keyCode) {
            return keyCode == key1 || keyCode == key2;
        }
    }

    private final List<Direction> sequence = new ArrayList<>();
    private int currentIndex = 0;
    private int errorTimer = 0;

    public SequenceQTEScreen(StartQTEPacket packet) {
        super(packet, Component.translatable("gui.melonstools.qte.sequence.title"));

        int diff = Math.max(1, Math.min(5, (int) difficulty));
        int[] lengths = {0, 5, 10, 15, 21, 27};
        int length = lengths[diff];

        Random random = new Random();
        Direction[] dirs = Direction.values();
        for (int i = 0; i < length; i++) {
            sequence.add(dirs[random.nextInt(dirs.length)]);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (hasCompleted) return;

        if (errorTimer > 0) {
            errorTimer--;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasCompleted) return super.keyPressed(keyCode, scanCode, modifiers);

        if (errorTimer > 0) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        Direction pressedDir = null;
        for (Direction d : Direction.values()) {
            if (d.matches(keyCode)) {
                pressedDir = d;
                break;
            }
        }

        if (pressedDir == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        Direction expected = sequence.get(currentIndex);

        if (pressedDir == expected) {
            currentIndex++;
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            if (currentIndex >= sequence.size()) {
                finishQTE(true, 1.0f);
            }
            return true;
        } else {
            // Wrong key!
            errorTimer = 20; // 1 second penalty
            currentIndex = (currentIndex / 7) * 7;
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS, 1.0F));
            return true;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int roundStartIndex = (currentIndex / 7) * 7;
        int roundEndIndex = Math.min(roundStartIndex + 7, sequence.size());
        int currentRoundSize = roundEndIndex - roundStartIndex;

        int totalWidth = currentRoundSize * 32 + (currentRoundSize - 1) * 8;
        int startX = centerX - totalWidth / 2;
        int startY = centerY - 16;

        // 面板
        drawPanel(guiGraphics, startX - 12, startY - 36, totalWidth + 24, 92);

        // 轮次标题
        int currentRound = (currentIndex / 7) + 1;
        int totalRounds = (int) Math.ceil(sequence.size() / 7.0);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.melonstools.qte.sequence.round", currentRound, totalRounds), centerX, startY - 30, COLOR_TEXT);

        for (int i = roundStartIndex; i < roundEndIndex; i++) {
            Direction dir = sequence.get(i);
            int displayIdx = i - roundStartIndex;
            int x = startX + displayIdx * 40;

            int bgColor;
            if (i < currentIndex) {
                bgColor = 0xFF14532D; // 已完成
            } else if (i == currentIndex) {
                bgColor = errorTimer > 0 ? 0xFF7F1D1D : 0xFF0C4A6E; // 当前
            } else {
                bgColor = 0xFF334155; // 待输入
            }

            guiGraphics.fill(x - 1, startY - 1, x + 33, startY + 33, 0xFF0A0F1E);
            guiGraphics.fill(x, startY, x + 32, startY + 32, bgColor);
            guiGraphics.blit(dir.texture, x, startY, 0, 0, 32, 32, 32, 32);
        }

        drawInstruction(guiGraphics, Component.translatable("gui.melonstools.qte.sequence.instr"), centerX, startY + 46);
    }
}
