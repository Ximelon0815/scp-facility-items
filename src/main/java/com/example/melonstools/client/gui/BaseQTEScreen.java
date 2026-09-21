package com.example.melonstools.client.gui;

import com.example.melonstools.network.QTEResultPacket;
import com.example.melonstools.network.StartQTEPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * 所有 QTE 小游戏界面的共享基类。
 * 提供：半透明游戏内背景、标题 + 倒计时进度条、成功/失败反馈层、常用绘制助手。
 */
public abstract class BaseQTEScreen extends Screen {
    // ===================== 共享主题色 =====================
    protected static final int COLOR_TEXT       = 0xFFE2E8F0;
    protected static final int COLOR_TEXT_DIM   = 0xFF94A3B8;
    protected static final int COLOR_GREEN      = 0xFF22C55E;
    protected static final int COLOR_YELLOW     = 0xFFFACC15;
    protected static final int COLOR_RED        = 0xFFEF4444;
    protected static final int COLOR_CYAN       = 0xFF38BDF8;
    protected static final int COLOR_PANEL      = 0xF01B2438; // 面板背景
    protected static final int COLOR_PANEL_EDGE = 0xFF3B4C6E; // 面板边框
    protected static final int COLOR_BAR_BG     = 0xFF1E293B; // 进度条空槽

    protected final int qteId;
    protected final int durationTicks;
    protected final float difficulty;

    protected int currentTicks = 0;
    protected boolean hasCompleted = false;

    // ===================== 反馈状态 =====================
    private static final int FEEDBACK_TICKS = 26;
    protected boolean feedbackSuccess = false;
    private int feedbackTicks = 0;

    protected BaseQTEScreen(StartQTEPacket packet, Component title) {
        super(title);
        this.qteId = packet.getQteId();
        this.durationTicks = packet.getDurationTicks();
        this.difficulty = packet.getDifficulty();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !hasCompleted;
    }

    @Override
    public void onClose() {
        if (!hasCompleted) {
            finishQTE(false, 0f);
        } else {
            super.onClose();
        }
    }

    /** 用半透明暗色遮罩代替原版菜单背景，让游戏世界保持可见。 */
    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x9A0A0F1E);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderHeader(guiGraphics);

        if (hasCompleted) {
            renderFeedback(guiGraphics);
        }
    }

    // ===================== 顶部标题 + 倒计时条 =====================
    protected void renderHeader(GuiGraphics guiGraphics) {
        int centerX = this.width / 2;
        int barWidth = Math.min(240, this.width - 80);
        int barX = centerX - barWidth / 2;
        int barY = 26;

        guiGraphics.drawCenteredString(this.font, this.title, centerX, 6, COLOR_TEXT);

        // 进度条边框与背景
        guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + 7, 0xFF000000);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + 6, COLOR_BAR_BG);

        int remaining = Math.max(0, durationTicks - currentTicks);
        float ratio = (float) remaining / Math.max(1, durationTicks);
        int fillWidth = (int) (barWidth * ratio);
        // 颜色随剩余时间变化：绿 -> 黄 -> 红
        int barColor = ratio > 0.5f ? COLOR_GREEN : (ratio > 0.25f ? COLOR_YELLOW : COLOR_RED);
        if (fillWidth > 0) {
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + 6, barColor);
        }

        // 剩余时间文字，快超时闪烁红色
        boolean urgent = ratio <= 0.25f && (currentTicks / 4) % 2 == 0;
        int labelColor = urgent ? COLOR_RED : COLOR_TEXT_DIM;
        guiGraphics.drawCenteredString(this.font, String.format("%.1fs", remaining / 20.0f), centerX, barY + 9, labelColor);
    }

    // ===================== 成功 / 失败反馈层 =====================
    protected void renderFeedback(GuiGraphics guiGraphics) {
        int tint = feedbackSuccess ? 0x3022C55E : 0x40EF4444;
        guiGraphics.fill(0, 0, this.width, this.height, tint);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        Component msg = feedbackSuccess
                ? Component.translatable("gui.melonstools.qte.success")
                : Component.translatable("gui.melonstools.qte.fail");
        int color = feedbackSuccess ? COLOR_GREEN : COLOR_RED;

        guiGraphics.drawCenteredString(this.font, msg, centerX + 1, centerY - 7, 0x66000000);
        guiGraphics.drawCenteredString(this.font, msg, centerX, centerY - 8, color);
    }

    @Override
    public void tick() {
        super.tick();

        if (hasCompleted) {
            if (feedbackTicks > 0) {
                feedbackTicks--;
                if (feedbackTicks <= 0 && this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
            }
            return;
        }

        currentTicks++;
        if (currentTicks > durationTicks) {
            onTimeout();
        }
    }

    protected void onTimeout() {
        finishQTE(false, 0f);
    }

    /** 完成 QTE：向服务器发送结果，并短暂显示成功/失败反馈再关闭界面。 */
    protected void finishQTE(boolean success, float score) {
        if (hasCompleted) return;
        hasCompleted = true;
        feedbackSuccess = success;
        feedbackTicks = FEEDBACK_TICKS;

        com.example.melonstools.network.NetworkManager.INSTANCE.sendToServer(
                new QTEResultPacket(qteId, success, score));

        if (this.minecraft != null) {
            net.minecraft.client.KeyMapping.releaseAll();
            if (success) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
            } else {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 1.0F));
            }
        }
    }

    // ===================== 绘制助手 =====================

    /** 绘制带边框的面板。 */
    protected void drawPanel(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xC80A0F1E);
        guiGraphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, COLOR_PANEL_EDGE);
        guiGraphics.fill(x, y, x + w, y + h, COLOR_PANEL);
    }

    /** 绘制带边框的进度条。 */
    protected void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int w, int h, float progress, int fillColor) {
        guiGraphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0A0F1E);
        guiGraphics.fill(x, y, x + w, y + h, COLOR_BAR_BG);
        int fw = (int) (w * Math.max(0f, Math.min(1f, progress)));
        if (fw > 0) {
            guiGraphics.fill(x, y, x + fw, y + h, fillColor);
        }
    }

    /** 绘制居中的提示文字。 */
    protected void drawInstruction(GuiGraphics guiGraphics, Component text, int centerX, int y) {
        guiGraphics.drawCenteredString(this.font, text, centerX, y, COLOR_TEXT_DIM);
    }
}



