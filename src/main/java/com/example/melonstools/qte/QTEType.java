package com.example.melonstools.qte;

public enum QTEType {
    FISHING_BARS,
    DBD_SKILL_CHECK,
    MASHING,
    SEQUENCE,
    BALANCE,
    MEMORY,
    WIRE_CONNECT,
    CHARGE_RELEASE,
    CIPHER,
    CALIBRATE;

    public static QTEType fromId(int id) {
        if (id >= 0 && id < values().length) {
            return values()[id];
        }
        return FISHING_BARS;
    }
}
