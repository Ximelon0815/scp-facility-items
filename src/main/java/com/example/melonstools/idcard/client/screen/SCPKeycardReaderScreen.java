package com.example.melonstools.idcard.client.screen;

import com.example.melonstools.idcard.network.ModNetworkHandler;
import com.example.melonstools.idcard.network.SaveReaderConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SCPKeycardReaderScreen extends Screen {
    private static final String[] DEPARTMENTS = {"未知", "管理部门", "科研部门", "安保部门", "后勤部门"};

    private final BlockPos pos;
    private int selectedAccessIndex;
    private List<String> selectedDepartments;
    private boolean selectedLatchMode;
    private int selectedOpenTicks;
    private boolean selectedPairingOnly;
    private EditBox openTicksEditBox;

    public SCPKeycardReaderScreen(BlockPos pos, int accessIndex, List<String> departments, boolean latchMode, int openTicks, boolean pairingOnly) {
        super(Component.translatable("gui.melonstools.card_reader.title"));
        this.pos = pos;
        this.selectedAccessIndex = accessIndex;
        this.selectedDepartments = new ArrayList<>(departments);
        this.selectedLatchMode = latchMode;
        this.selectedOpenTicks = Math.max(1, openTicks);
        this.selectedPairingOnly = pairingOnly;
        for (int i = 0; i < this.selectedDepartments.size(); i++) {
            if (com.example.melonstools.department.DepartmentPolicy.isLegacyTemporaryDepartment(this.selectedDepartments.get(i))) {
                this.selectedDepartments.set(i, com.example.melonstools.department.DepartmentPolicy.DEPT_LOGISTICS);
            }
        }
        if (this.selectedDepartments.isEmpty()) {
            this.selectedDepartments.add("未知");
        }
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startY = centerY - 40;
        int boxX = centerX - 50;

        this.addRenderableWidget(Button.builder(Component.literal(String.valueOf(this.selectedAccessIndex < 1 ? 1 : this.selectedAccessIndex)), (button) -> {
            this.selectedAccessIndex = this.selectedAccessIndex < 1 ? 1 : this.selectedAccessIndex;
            this.selectedAccessIndex = (this.selectedAccessIndex % 5) + 1;
            button.setMessage(Component.literal(String.valueOf(this.selectedAccessIndex)));
        }).bounds(boxX, startY, 150, 20).build());

        for (int i = 0; i < this.selectedDepartments.size(); i++) {
            final int index = i;
            String currentDept = this.selectedDepartments.get(index);
            int rowY = startY + 30 + (i * 25);

            this.addRenderableWidget(Button.builder(Component.literal(currentDept), (button) -> {
                String dept = this.selectedDepartments.get(index);
                int currentIdx = 0;
                for (int d = 0; d < DEPARTMENTS.length; d++) {
                    if (DEPARTMENTS[d].equals(dept)) {
                        currentIdx = d;
                        break;
                    }
                }
                String nextDept = DEPARTMENTS[(currentIdx + 1) % DEPARTMENTS.length];
                this.selectedDepartments.set(index, nextDept);
                button.setMessage(Component.literal(nextDept));
            }).bounds(boxX, rowY, 150, 20).build());

            if (i == 0) {
                this.addRenderableWidget(Button.builder(Component.literal("+"), (button) -> {
                    this.selectedDepartments.add("未知");
                    this.clearWidgets();
                    this.init();
                }).bounds(boxX + 155, rowY, 20, 20).build());
            } else {
                this.addRenderableWidget(Button.builder(Component.literal("-"), (button) -> {
                    this.selectedDepartments.remove(index);
                    this.clearWidgets();
                    this.init();
                }).bounds(boxX + 155, rowY, 20, 20).build());
            }
        }

        int saveY = startY + 30 + (this.selectedDepartments.size() * 25) + 10;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.melonstools.save"), (button) -> {
            saveData();
            this.onClose();
        }).bounds(centerX - 50, saveY, 100, 20).build());

        // 持续激活（锁存）模式：刷开后保持开启，再刷一次才关闭
        int latchY = saveY + 30;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.melonstools.card_reader.latch").append(" " + selectedLatchMode), (button) -> {
            this.selectedLatchMode = !this.selectedLatchMode;
            button.setMessage(Component.translatable("gui.melonstools.card_reader.latch").append(" " + selectedLatchMode));
        }).bounds(boxX, latchY, 150, 20).build());

        // 普通模式下的自动关闭时长
        this.openTicksEditBox = new EditBox(this.font, boxX, latchY + 30, 150, 20, Component.translatable("gui.melonstools.card_reader.open_ticks"));
        this.openTicksEditBox.setValue(String.valueOf(this.selectedOpenTicks));
        this.openTicksEditBox.setMaxLength(5);
        this.addRenderableWidget(this.openTicksEditBox);

        // 仅配对模式：不接受刷卡，只能由已配对的门锁开启
        this.addRenderableWidget(Button.builder(Component.translatable("gui.melonstools.card_reader.pairing_only").append(" " + selectedPairingOnly), (button) -> {
            this.selectedPairingOnly = !this.selectedPairingOnly;
            button.setMessage(Component.translatable("gui.melonstools.card_reader.pairing_only").append(" " + selectedPairingOnly));
        }).bounds(boxX, latchY + 60, 150, 20).build());
    }

    private void saveData() {
        try {
            this.selectedOpenTicks = Math.max(1, Integer.parseInt(this.openTicksEditBox.getValue()));
        } catch (NumberFormatException ignored) {}
        ModNetworkHandler.CHANNEL.sendToServer(new SaveReaderConfigPacket(this.pos, this.selectedAccessIndex, this.selectedDepartments, this.selectedLatchMode, this.selectedOpenTicks, this.selectedPairingOnly));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startY = centerY - 40;
        int textX = centerX - 60; 

        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.melonstools.card_reader.title"), centerX, startY - 20, 0xFFFFFF);

        guiGraphics.drawString(this.font, Component.translatable("gui.melonstools.card_reader.level"), textX - this.font.width(Component.translatable("gui.melonstools.card_reader.level")), startY + 6, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.melonstools.card_reader.departments"), textX - this.font.width(Component.translatable("gui.melonstools.card_reader.departments")), startY + 36, 0xFFFFFF);

        int saveY = startY + 30 + (this.selectedDepartments.size() * 25) + 10;
        int latchY = saveY + 30;
        guiGraphics.drawString(this.font, Component.translatable("gui.melonstools.card_reader.open_ticks"), textX - this.font.width(Component.translatable("gui.melonstools.card_reader.open_ticks")), latchY + 36, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
