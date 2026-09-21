package com.example.melonstools.client.gui;

import com.example.melonstools.network.SaveBinderConfigPacket;
import com.example.melonstools.network.NetworkManager;
import com.example.melonstools.qte.QTEType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class QteBinderScreen extends Screen {
    private final BlockPos pos;
    private boolean initiallyBound;
    private QTEType selectedType;
    private int initDuration;
    private float initDifficulty;
    private String initRequiredItemId;
    private int initRounds;

    private boolean initRedstoneOutput;
    private int initRedstoneTicks;
    private String initSuccessCommands;
    private String initFailureCommands;
    private String initTeamWhitelist;
    private String initTagWhitelist;
    private boolean initOnlyNotCompleted;
    private boolean initBypassAfterComplete;
    private List<String> completedPlayers;

    private EditBox durationEditBox;
    private EditBox difficultyEditBox;
    private EditBox requiredItemEditBox;
    private EditBox roundsEditBox;
    private EditBox redstoneTicksEditBox;
    private EditBox successCommandsEditBox;
    private EditBox failureCommandsEditBox;
    private EditBox teamWhitelistEditBox;
    private EditBox tagWhitelistEditBox;
    private Button typeButton;
    private Button boundButton;
    private Button redstoneButton;
    private Button onlyNotCompletedButton;
    private Button bypassButton;
    private Button bindingModeButton;
    private EditBox anomalyInstanceEditBox;
    private EditBox anomalyRestoreEditBox;
    private EditBox anomalyMinLevelEditBox;
    private String initBindingMode;
    private String initAnomalyInstanceId;
    private int initAnomalyRestoreAmount;
    private int initAnomalyCooldownSeconds;
    private int initAnomalyMinCardLevel;
    private String initAnomalyRequiredDepartment;

    public QteBinderScreen(BlockPos pos, boolean isBound, QTEType type, int duration, float difficulty, String requiredItemId, int rounds,
                           boolean redstoneOutput, int redstoneTicks, String successCommands, String failureCommands,
                           String teamWhitelist, String tagWhitelist, boolean onlyNotCompleted, boolean bypassAfterComplete,
                           List<String> completedPlayers, String bindingMode, String anomalyInstanceId, int anomalyRestoreAmount,
                           int anomalyCooldownSeconds, int anomalyMinCardLevel, String anomalyRequiredDepartment) {
        super(Component.translatable("gui.melonstools.binder.title"));
        this.pos = pos;
        this.initiallyBound = isBound;
        this.selectedType = type != null ? type : QTEType.FISHING_BARS;
        this.initDuration = duration;
        this.initDifficulty = difficulty;
        this.initRequiredItemId = requiredItemId == null ? "" : requiredItemId;
        this.initRounds = rounds;
        this.initRedstoneOutput = redstoneOutput;
        this.initRedstoneTicks = redstoneTicks;
        this.initSuccessCommands = successCommands == null ? "" : successCommands;
        this.initFailureCommands = failureCommands == null ? "" : failureCommands;
        this.initTeamWhitelist = teamWhitelist == null ? "" : teamWhitelist;
        this.initTagWhitelist = tagWhitelist == null ? "" : tagWhitelist;
        this.initOnlyNotCompleted = onlyNotCompleted;
        this.initBypassAfterComplete = bypassAfterComplete;
        this.completedPlayers = completedPlayers == null ? new ArrayList<>() : completedPlayers;
        this.initBindingMode = bindingMode == null || bindingMode.isBlank() ? "normal" : bindingMode;
        this.initAnomalyInstanceId = anomalyInstanceId == null ? "" : anomalyInstanceId;
        this.initAnomalyRestoreAmount = Math.max(1, anomalyRestoreAmount);
        this.initAnomalyCooldownSeconds = Math.max(0, anomalyCooldownSeconds);
        this.initAnomalyMinCardLevel = Math.max(0, anomalyMinCardLevel);
        this.initAnomalyRequiredDepartment = anomalyRequiredDepartment == null || anomalyRequiredDepartment.isBlank() ? "科研部门" : anomalyRequiredDepartment;
    }

    @Override
    protected void init() {
        super.init();

        int cx = this.width / 2;
        int y0 = this.height / 2 - 135;
        int leftX = cx - 165;
        int rightX = cx + 15;
        int w = 150;

        this.boundButton = Button.builder(Component.translatable("gui.melonstools.binder.bound").append(" " + initiallyBound), (button) -> {
            this.initiallyBound = !this.initiallyBound;
            button.setMessage(Component.translatable("gui.melonstools.binder.bound").append(" " + initiallyBound));
        }).bounds(leftX, y0, w, 20).build();
        this.addRenderableWidget(this.boundButton);

        this.typeButton = Button.builder(Component.translatable("gui.melonstools.binder.type").append(" ").append(Component.translatable("gui.melonstools.qte." + selectedType.name().toLowerCase() + ".title")), (button) -> {
            QTEType[] types = QTEType.values();
            int nextIdx = (this.selectedType.ordinal() + 1) % types.length;
            this.selectedType = types[nextIdx];
            button.setMessage(Component.translatable("gui.melonstools.binder.type").append(" ").append(Component.translatable("gui.melonstools.qte." + selectedType.name().toLowerCase() + ".title")));
        }).bounds(leftX, y0 + 24, w, 20).build();
        this.addRenderableWidget(this.typeButton);

        this.redstoneButton = Button.builder(Component.translatable("gui.melonstools.binder.redstone").append(" " + initRedstoneOutput), (button) -> {
            this.initRedstoneOutput = !this.initRedstoneOutput;
            button.setMessage(Component.translatable("gui.melonstools.binder.redstone").append(" " + initRedstoneOutput));
        }).bounds(rightX, y0, w, 20).build();
        this.addRenderableWidget(this.redstoneButton);

        this.durationEditBox = new EditBox(this.font, leftX, y0 + 48, w, 20, Component.translatable("gui.melonstools.binder.duration"));
        this.durationEditBox.setValue(String.valueOf(this.initDuration));
        this.addRenderableWidget(this.durationEditBox);

        this.difficultyEditBox = new EditBox(this.font, leftX, y0 + 72, w, 20, Component.translatable("gui.melonstools.binder.difficulty"));
        this.difficultyEditBox.setValue(String.valueOf(this.initDifficulty));
        this.addRenderableWidget(this.difficultyEditBox);

        this.requiredItemEditBox = new EditBox(this.font, leftX, y0 + 96, w, 20, Component.translatable("gui.melonstools.binder.item"));
        this.requiredItemEditBox.setValue(this.initRequiredItemId);
        this.addRenderableWidget(this.requiredItemEditBox);

        this.roundsEditBox = new EditBox(this.font, leftX, y0 + 120, w, 20, Component.translatable("gui.melonstools.binder.rounds"));
        this.roundsEditBox.setValue(String.valueOf(this.initRounds));
        this.addRenderableWidget(this.roundsEditBox);

        this.redstoneTicksEditBox = new EditBox(this.font, rightX, y0 + 24, w, 20, Component.translatable("gui.melonstools.binder.redstone_ticks"));
        this.redstoneTicksEditBox.setValue(String.valueOf(this.initRedstoneTicks));
        this.addRenderableWidget(this.redstoneTicksEditBox);

        this.successCommandsEditBox = new EditBox(this.font, rightX, y0 + 48, w, 20, Component.translatable("gui.melonstools.binder.success_commands"));
        this.successCommandsEditBox.setValue(this.initSuccessCommands);
        this.successCommandsEditBox.setMaxLength(512);
        this.addRenderableWidget(this.successCommandsEditBox);

        this.failureCommandsEditBox = new EditBox(this.font, rightX, y0 + 72, w, 20, Component.translatable("gui.melonstools.binder.failure_commands"));
        this.failureCommandsEditBox.setValue(this.initFailureCommands);
        this.failureCommandsEditBox.setMaxLength(512);
        this.addRenderableWidget(this.failureCommandsEditBox);

        this.teamWhitelistEditBox = new EditBox(this.font, rightX, y0 + 96, w, 20, Component.translatable("gui.melonstools.binder.team_whitelist"));
        this.teamWhitelistEditBox.setValue(this.initTeamWhitelist);
        this.teamWhitelistEditBox.setMaxLength(256);
        this.addRenderableWidget(this.teamWhitelistEditBox);

        this.tagWhitelistEditBox = new EditBox(this.font, rightX, y0 + 120, w, 20, Component.translatable("gui.melonstools.binder.tag_whitelist"));
        this.tagWhitelistEditBox.setValue(this.initTagWhitelist);
        this.tagWhitelistEditBox.setMaxLength(256);
        this.addRenderableWidget(this.tagWhitelistEditBox);

        this.onlyNotCompletedButton = Button.builder(Component.translatable("gui.melonstools.binder.only_not_completed").append(" " + initOnlyNotCompleted), (button) -> {
            this.initOnlyNotCompleted = !this.initOnlyNotCompleted;
            button.setMessage(Component.translatable("gui.melonstools.binder.only_not_completed").append(" " + initOnlyNotCompleted));
        }).bounds(leftX, y0 + 144, w, 20).build();
        this.addRenderableWidget(this.onlyNotCompletedButton);

        this.bypassButton = Button.builder(Component.translatable("gui.melonstools.binder.bypass_after_complete").append(" " + initBypassAfterComplete), (button) -> {
            this.initBypassAfterComplete = !this.initBypassAfterComplete;
            button.setMessage(Component.translatable("gui.melonstools.binder.bypass_after_complete").append(" " + initBypassAfterComplete));
        }).bounds(rightX, y0 + 144, w, 20).build();
        this.addRenderableWidget(this.bypassButton);

        this.bindingModeButton = Button.builder(Component.translatable("gui.melonstools.binder.mode").append(" " + modeText()), (button) -> {
            this.initBindingMode = "anomaly_maintenance".equals(this.initBindingMode) ? "normal" : "anomaly_maintenance";
            button.setMessage(Component.translatable("gui.melonstools.binder.mode").append(" " + modeText()));
        }).bounds(leftX, y0 + 168, w, 20).build();
        this.addRenderableWidget(this.bindingModeButton);

        this.anomalyInstanceEditBox = new EditBox(this.font, rightX, y0 + 168, w, 20, Component.translatable("gui.melonstools.binder.anomaly_instance"));
        this.anomalyInstanceEditBox.setValue(this.initAnomalyInstanceId);
        this.anomalyInstanceEditBox.setMaxLength(128);
        this.addRenderableWidget(this.anomalyInstanceEditBox);

        this.anomalyRestoreEditBox = new EditBox(this.font, leftX, y0 + 192, w, 20, Component.translatable("gui.melonstools.binder.anomaly_restore"));
        this.anomalyRestoreEditBox.setValue(String.valueOf(this.initAnomalyRestoreAmount));
        this.addRenderableWidget(this.anomalyRestoreEditBox);

        this.anomalyMinLevelEditBox = new EditBox(this.font, rightX, y0 + 192, w, 20, Component.translatable("gui.melonstools.binder.anomaly_min_level"));
        this.anomalyMinLevelEditBox.setValue(String.valueOf(this.initAnomalyMinCardLevel));
        this.addRenderableWidget(this.anomalyMinLevelEditBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.melonstools.binder.reset_completions"), (button) -> {
            this.completedPlayers = new ArrayList<>();
            this.initSuccessCommands = this.successCommandsEditBox.getValue();
            sendClear();
        }).bounds(leftX, y0 + 222, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.melonstools.save"), (button) -> {
            try {
                this.initDuration = Integer.parseInt(this.durationEditBox.getValue());
                this.initDifficulty = Float.parseFloat(this.difficultyEditBox.getValue());
                this.initRounds = Integer.parseInt(this.roundsEditBox.getValue());
                this.initRedstoneTicks = Integer.parseInt(this.redstoneTicksEditBox.getValue());
                this.initAnomalyRestoreAmount = Integer.parseInt(this.anomalyRestoreEditBox.getValue());
                this.initAnomalyMinCardLevel = Integer.parseInt(this.anomalyMinLevelEditBox.getValue());
            } catch (NumberFormatException ignored) {}
            this.initAnomalyInstanceId = this.anomalyInstanceEditBox.getValue();
            this.initRequiredItemId = this.requiredItemEditBox.getValue();
            this.initSuccessCommands = this.successCommandsEditBox.getValue();
            this.initFailureCommands = this.failureCommandsEditBox.getValue();
            this.initTeamWhitelist = this.teamWhitelistEditBox.getValue();
            this.initTagWhitelist = this.tagWhitelistEditBox.getValue();

            saveData(false);
            this.onClose();
        }).bounds(rightX, y0 + 222, w, 20).build());
    }

    private void saveData(boolean clearCompletions) {
        NetworkManager.INSTANCE.sendToServer(new SaveBinderConfigPacket(
                this.pos, this.initiallyBound, this.selectedType, this.initDuration, this.initDifficulty, this.initRequiredItemId, this.initRounds,
                this.initRedstoneOutput, this.initRedstoneTicks, this.initSuccessCommands, this.initFailureCommands,
                this.initTeamWhitelist, this.initTagWhitelist, this.initOnlyNotCompleted, this.initBypassAfterComplete,
                clearCompletions, this.initBindingMode, this.initAnomalyInstanceId, this.initAnomalyRestoreAmount,
                this.initAnomalyCooldownSeconds, this.initAnomalyMinCardLevel, this.initAnomalyRequiredDepartment
        ));
    }

    private void sendClear() {
        try {
            this.initDuration = Integer.parseInt(this.durationEditBox.getValue());
            this.initDifficulty = Float.parseFloat(this.difficultyEditBox.getValue());
            this.initRounds = Integer.parseInt(this.roundsEditBox.getValue());
            this.initRedstoneTicks = Integer.parseInt(this.redstoneTicksEditBox.getValue());
            this.initAnomalyRestoreAmount = Integer.parseInt(this.anomalyRestoreEditBox.getValue());
            this.initAnomalyMinCardLevel = Integer.parseInt(this.anomalyMinLevelEditBox.getValue());
        } catch (NumberFormatException ignored) {}
        this.initAnomalyInstanceId = this.anomalyInstanceEditBox.getValue();
        this.initRequiredItemId = this.requiredItemEditBox.getValue();
        this.initFailureCommands = this.failureCommandsEditBox.getValue();
        this.initTeamWhitelist = this.teamWhitelistEditBox.getValue();
        this.initTagWhitelist = this.tagWhitelistEditBox.getValue();
        saveData(true);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int y0 = this.height / 2 - 135;

        // 字段标签
        drawLabel(guiGraphics, this.durationEditBox.getMessage(), cx - 165, y0 + 40);
        drawLabel(guiGraphics, this.difficultyEditBox.getMessage(), cx - 165, y0 + 64);
        drawLabel(guiGraphics, this.requiredItemEditBox.getMessage(), cx - 165, y0 + 88);
        drawLabel(guiGraphics, this.roundsEditBox.getMessage(), cx - 165, y0 + 112);
        drawLabel(guiGraphics, this.redstoneTicksEditBox.getMessage(), cx + 15, y0 + 16);
        drawLabel(guiGraphics, this.successCommandsEditBox.getMessage(), cx + 15, y0 + 40);
        drawLabel(guiGraphics, this.failureCommandsEditBox.getMessage(), cx + 15, y0 + 64);
        drawLabel(guiGraphics, this.teamWhitelistEditBox.getMessage(), cx + 15, y0 + 88);
        drawLabel(guiGraphics, this.tagWhitelistEditBox.getMessage(), cx + 15, y0 + 112);
        drawLabel(guiGraphics, this.anomalyInstanceEditBox.getMessage(), cx + 15, y0 + 160);
        drawLabel(guiGraphics, this.anomalyRestoreEditBox.getMessage(), cx - 165, y0 + 184);
        drawLabel(guiGraphics, this.anomalyMinLevelEditBox.getMessage(), cx + 15, y0 + 184);

        // 指令占位符提示
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.melonstools.binder.cmd_placeholder"), cx, y0 + 72 + 12, 0xFF64748B);

        // 完成者列表
        String completedText;
        if (this.completedPlayers.isEmpty()) {
            completedText = Component.translatable("gui.melonstools.binder.completed_players_empty").getString();
        } else {
            String joined = String.join(", ", this.completedPlayers);
            if (joined.length() > 60) {
                joined = joined.substring(0, 60) + "...";
            }
            completedText = Component.translatable("gui.melonstools.binder.completed_players", joined).getString();
        }
        guiGraphics.drawCenteredString(this.font, completedText, cx, y0 + 246, 0xFFA7F3D0);
    }

    private Component modeText() {
        return "anomaly_maintenance".equals(this.initBindingMode)
                ? Component.translatable("gui.melonstools.binder.mode.anomaly")
                : Component.translatable("gui.melonstools.binder.mode.normal");
    }

    private void drawLabel(GuiGraphics guiGraphics, Component text, int x, int y) {
        guiGraphics.drawString(this.font, text, x, y, 0xFFCBD5E1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
