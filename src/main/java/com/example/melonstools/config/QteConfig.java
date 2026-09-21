package com.example.melonstools.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class QteConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue FISHING_TARGET_SIZE;
    public static final ForgeConfigSpec.DoubleValue DBD_SUCCESS_SIZE;
    public static final ForgeConfigSpec.IntValue MASHING_REQUIRED_CLICKS;
    public static final ForgeConfigSpec.IntValue SEQUENCE_BASE_LENGTH;

    static {
        BUILDER.push("Fishing QTE Settings");
        FISHING_TARGET_SIZE = BUILDER
                .comment("The size of the success zone in the Fishing QTE (0.0 to 1.0). Default is 0.1 (10% of the bar).")
                .defineInRange("fishingTargetSize", 0.1, 0.01, 1.0);
        BUILDER.pop();

        BUILDER.push("DBD Skill Check Settings");
        DBD_SUCCESS_SIZE = BUILDER
                .comment("The base size of the green success zone in the DBD Skill Check QTE (0.0 to 1.0). Default is 0.3.")
                .defineInRange("dbdSuccessSize", 0.3, 0.01, 1.0);
        BUILDER.pop();

        BUILDER.push("Mashing QTE Settings");
        MASHING_REQUIRED_CLICKS = BUILDER
                .comment("The base number of clicks required to succeed in the Mashing QTE. Default is 20.")
                .defineInRange("mashingRequiredClicks", 20, 1, 1000);
        BUILDER.pop();

        BUILDER.push("Sequence QTE Settings");
        SEQUENCE_BASE_LENGTH = BUILDER
                .comment("The base number of directional arrows required in the Sequence QTE. Default is 6.")
                .defineInRange("sequenceBaseLength", 6, 1, 50);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
