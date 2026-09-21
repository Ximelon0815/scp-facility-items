package com.example.melonstools.idcard.client.screen;

import com.example.melonstools.idcard.network.ModNetworkHandler;
import com.example.melonstools.idcard.network.SaveIdentityCardPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class IdentityCardScreen extends Screen {
    private static final String[] DEPARTMENTS = {"未知", "管理部门", "科研部门", "安保部门", "后勤部门"};

    private String selectedName;
    private String selectedDepartment;
    private String selectedPosition;
    private int selectedAccessIndex;
    private String selectedBindName;
    private String selectedBankBindName;

    private EditBox nameEditBox;
    private Button deptButton;
    private EditBox posEditBox;
    private Button levelButton;
    private EditBox bindEditBox;
    private EditBox bankBindEditBox;

    public IdentityCardScreen(ItemStack itemStack) {
        super(Component.translatable("gui.melonstools.identity_card.title"));
        if (itemStack != null && itemStack.hasTag()) {
            this.selectedName = itemStack.getTag().getString("CardName");
            this.selectedDepartment = itemStack.getTag().getString("CardDepartment");
            this.selectedPosition = itemStack.getTag().getString("CardPosition");
            this.selectedAccessIndex = itemStack.getTag().getInt("CardLevel");
            this.selectedBindName = itemStack.getTag().getString("CardSkinName");
            this.selectedBankBindName = "";
        } else {
            this.selectedName = "";
            this.selectedDepartment = "未知";
            this.selectedPosition = "";
            this.selectedAccessIndex = 0;
            this.selectedBindName = "";
            this.selectedBankBindName = "";
        }
        if (this.selectedDepartment == null || this.selectedDepartment.isEmpty()) {
            this.selectedDepartment = "未知";
        }
        if (com.example.melonstools.department.DepartmentPolicy.isLegacyTemporaryDepartment(this.selectedDepartment)) {
            this.selectedDepartment = com.example.melonstools.department.DepartmentPolicy.DEPT_LOGISTICS;
            this.selectedPosition = com.example.melonstools.department.DepartmentPolicy.POS_TEMP;
            this.selectedAccessIndex = 0;
        }
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startY = centerY - 80;
        int boxX = centerX - 50;

        this.nameEditBox = new EditBox(this.font, boxX, startY, 150, 20, Component.translatable("gui.melonstools.identity_card.name"));
        this.nameEditBox.setValue(this.selectedName);
        this.addRenderableWidget(this.nameEditBox);

        this.deptButton = Button.builder(Component.literal(this.selectedDepartment), (button) -> {
            int currentIdx = 0;
            for (int i = 0; i < DEPARTMENTS.length; i++) {
                if (DEPARTMENTS[i].equals(this.selectedDepartment)) {
                    currentIdx = i;
                    break;
                }
            }
            this.selectedDepartment = DEPARTMENTS[(currentIdx + 1) % DEPARTMENTS.length];
            button.setMessage(Component.literal(this.selectedDepartment));
        }).bounds(boxX, startY + 30, 150, 20).build();
        this.addRenderableWidget(this.deptButton);

        this.posEditBox = new EditBox(this.font, boxX, startY + 60, 150, 20, Component.translatable("gui.melonstools.identity_card.position"));
        this.posEditBox.setValue(this.selectedPosition);
        this.addRenderableWidget(this.posEditBox);

        this.levelButton = Button.builder(Component.literal(levelText(this.selectedAccessIndex)), (button) -> {
            this.selectedAccessIndex = (this.selectedAccessIndex + 1) % 6; // 0-5，0 为无权限
            button.setMessage(Component.literal(levelText(this.selectedAccessIndex)));
        }).bounds(boxX, startY + 90, 150, 20).build();
        this.addRenderableWidget(this.levelButton);

        this.bindEditBox = new EditBox(this.font, boxX, startY + 120, 150, 20, Component.translatable("gui.melonstools.identity_card.bind"));
        this.bindEditBox.setValue(this.selectedBindName);
        this.addRenderableWidget(this.bindEditBox);

        this.bankBindEditBox = new EditBox(this.font, boxX, startY + 150, 150, 20, Component.translatable("gui.melonstools.identity_card.bank_bind"));
        this.bankBindEditBox.setValue(this.selectedBankBindName);
        this.addRenderableWidget(this.bankBindEditBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.melonstools.save"), (button) -> {
            this.selectedName = this.nameEditBox.getValue();
            this.selectedPosition = this.posEditBox.getValue();
            this.selectedBindName = this.bindEditBox.getValue();
            this.selectedBankBindName = this.bankBindEditBox.getValue();
            
            saveData();
            this.onClose();
        }).bounds(centerX - 50, startY + 180, 100, 20).build());
    }

    private void saveData() {
        ModNetworkHandler.CHANNEL.sendToServer(new SaveIdentityCardPacket(
            this.selectedName, this.selectedDepartment, this.selectedPosition, this.selectedAccessIndex, this.selectedBindName, this.selectedBankBindName
        ));
    }

    private static String levelText(int level) {
        if (level <= 0) {
            return "0 (" + Component.translatable("gui.melonstools.identity_card.no_access").getString() + ")";
        }
        return String.valueOf(level);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startY = centerY - 80;
        int textX = centerX - 60;

        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.melonstools.identity_card.title"), centerX, startY - 20, 0xFFFFFF);

        guiGraphics.drawString(this.font, Component.translatable("gui.melonstools.identity_card.name"), textX - this.font.width(Component.translatable("gui.melonstools.identity_card.name")), startY + 6, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.melonstools.identity_card.department"), textX - this.font.width(Component.translatable("gui.melonstools.identity_card.department")), startY + 36, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.melonstools.identity_card.position"), textX - this.font.width(Component.translatable("gui.melonstools.identity_card.position")), startY + 66, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.melonstools.identity_card.level"), textX - this.font.width(Component.translatable("gui.melonstools.identity_card.level")), startY + 96, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.melonstools.identity_card.bind"), textX - this.font.width(Component.translatable("gui.melonstools.identity_card.bind")), startY + 126, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.melonstools.identity_card.bank_bind"), textX - this.font.width(Component.translatable("gui.melonstools.identity_card.bank_bind")), startY + 156, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
