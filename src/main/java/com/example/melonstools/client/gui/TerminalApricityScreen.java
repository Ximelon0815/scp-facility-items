package com.example.melonstools.client.gui;

import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;

/**
 * 设施终端专用 AUI Screen。
 *
 * <p>AUI 的 fixed/fit 画布在非 16:9 Minecraft 窗口中会产生少量留白；普通
 * {@link ApricityScreen} 默认不绘制 Screen 背景，因此留白会直接透出游戏画面。
 * 本屏幕在 AUI 文档之前绘制完全不透明的终端底色，保证整个 Minecraft GUI 区域被覆盖。
 */
public final class TerminalApricityScreen extends ApricityScreen {
    public static final String TEMPLATE_PATH = "melonstools/terminal/index.html";
    private static final int BACKGROUND_COLOR = 0xFF0D0D0E;

    public TerminalApricityScreen() {
        super(TEMPLATE_PATH);
        setPauseGame(false);
        setShowDefaultBackground(false);
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
