package com.example.melonstools.client.gui;

import com.example.melonstools.network.StartQTEPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class WireConnectQTEScreen extends BaseQTEScreen {
    private final int[] ALL_COLORS = {
        0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00,
        0xFFFF00FF, 0xFF00FFFF, 0xFFFFA500, 0xFF800080,
        0xFF008080, 0xFFFFC0CB, 0xFF8B4513, 0xFFFFFFFF
    };

    private final int nodeCount;
    private final List<Integer> leftNodes = new ArrayList<>();
    private final List<Integer> rightNodes = new ArrayList<>();

    private final int[] connections;
    private int draggingNode = -1;
    private int mouseX, mouseY;

    public WireConnectQTEScreen(StartQTEPacket packet) {
        super(packet, Component.translatable("gui.melonstools.qte.wire.title"));

        int diff = Math.max(1, Math.min(5, (int) difficulty));
        this.nodeCount = 4 + (diff - 1) * 2;
        this.connections = new int[this.nodeCount];

        for (int i = 0; i < this.nodeCount; i++) {
            this.connections[i] = -1;
            leftNodes.add(i);
            rightNodes.add(i);
        }
        Collections.shuffle(leftNodes, new Random());
        Collections.shuffle(rightNodes, new Random());
    }

    private int getNodeSpacing() {
        return Math.min(40, (this.height - 80) / nodeCount);
    }

    private int getStartY() {
        return this.height / 2 - (nodeCount * getNodeSpacing()) / 2;
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 0 && !hasCompleted) {
            int startX = this.width / 2 - 100;
            int startY = getStartY();
            int spacing = getNodeSpacing();

            for (int i = 0; i < nodeCount; i++) {
                int nodeY = startY + i * spacing + spacing / 2;
                if (Math.abs(pMouseX - startX) < 20 && Math.abs(pMouseY - nodeY) < 20) {
                    draggingNode = i;
                    connections[i] = -1; // Detach existing
                    return true;
                }
            }
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 0 && draggingNode != -1) {
            int endX = this.width / 2 + 100;
            int startY = getStartY();
            int spacing = getNodeSpacing();

            for (int i = 0; i < nodeCount; i++) {
                int nodeY = startY + i * spacing + spacing / 2;
                if (Math.abs(pMouseX - endX) < 20 && Math.abs(pMouseY - nodeY) < 20) {
                    // Check if already connected by something else, then swap or overwrite
                    for (int j = 0; j < nodeCount; j++) {
                        if (connections[j] == i) connections[j] = -1;
                    }
                    connections[draggingNode] = i;
                    checkWin();
                    break;
                }
            }
            draggingNode = -1;
            return true;
        }
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    @Override
    public void mouseMoved(double pMouseX, double pMouseY) {
        this.mouseX = (int) pMouseX;
        this.mouseY = (int) pMouseY;
        super.mouseMoved(pMouseX, pMouseY);
    }

    private void checkWin() {
        for (int i = 0; i < nodeCount; i++) {
            if (connections[i] == -1) return;
            int leftColorId = leftNodes.get(i);
            int rightColorId = rightNodes.get(connections[i]);
            if (leftColorId != rightColorId) return;
        }
        finishQTE(true, 1.0f);
    }

    private void drawThickLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        Matrix4f matrix = guiGraphics.pose().last().pose();
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();

        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return;
        float nx = -dy / length * 3.0f; // half thickness 3.0f -> 6px line
        float ny = dx / length * 3.0f;

        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(matrix, x1 + nx, y1 + ny, 0.0F).color(r, g, b, a).endVertex();
        bufferbuilder.vertex(matrix, x1 - nx, y1 - ny, 0.0F).color(r, g, b, a).endVertex();
        bufferbuilder.vertex(matrix, x2 - nx, y2 - ny, 0.0F).color(r, g, b, a).endVertex();
        bufferbuilder.vertex(matrix, x2 + nx, y2 + ny, 0.0F).color(r, g, b, a).endVertex();
        tesselator.end();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int pMouseX, int pMouseY, float partialTick) {
        this.mouseX = pMouseX;
        this.mouseY = pMouseY;
        super.render(guiGraphics, pMouseX, pMouseY, partialTick);

        int centerX = this.width / 2;
        int startX = centerX - 100;
        int endX = centerX + 100;
        int startY = getStartY();
        int spacing = getNodeSpacing();

        // 面板
        drawPanel(guiGraphics, startX - 30, startY - 20, (endX - startX) + 60, nodeCount * spacing + 40);

        // Draw connections
        for (int i = 0; i < nodeCount; i++) {
            int leftY = startY + i * spacing + spacing / 2;
            if (connections[i] != -1) {
                int rightY = startY + connections[i] * spacing + spacing / 2;
                int color = ALL_COLORS[leftNodes.get(i)];
                drawThickLine(guiGraphics, startX + 10, leftY, endX - 10, rightY, color);
            } else if (draggingNode == i) {
                int color = ALL_COLORS[leftNodes.get(i)];
                drawThickLine(guiGraphics, startX + 10, leftY, this.mouseX, this.mouseY, color);
            }
        }

        // Draw nodes
        for (int i = 0; i < nodeCount; i++) {
            int nodeY = startY + i * spacing + spacing / 2;
            int boxSize = spacing / 2 - 2; // Dynamic box size

            // Left node
            int leftColor = ALL_COLORS[leftNodes.get(i)];
            guiGraphics.fill(startX - boxSize - 1, nodeY - boxSize - 1, startX + boxSize + 1, nodeY + boxSize + 1, 0xFF0A0F1E);
            guiGraphics.fill(startX - boxSize, nodeY - boxSize, startX + boxSize, nodeY + boxSize, leftColor);

            // Right node
            int rightColor = ALL_COLORS[rightNodes.get(i)];
            guiGraphics.fill(endX - boxSize - 1, nodeY - boxSize - 1, endX + boxSize + 1, nodeY + boxSize + 1, 0xFF0A0F1E);
            guiGraphics.fill(endX - boxSize, nodeY - boxSize, endX + boxSize, nodeY + boxSize, rightColor);
        }

        drawInstruction(guiGraphics, Component.translatable("gui.melonstools.qte.wire.instr"), centerX, startY + nodeCount * spacing + 12);
    }
}
