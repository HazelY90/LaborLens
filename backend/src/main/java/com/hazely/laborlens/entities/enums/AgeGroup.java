package com.hazely.laborlens.entities.enums;

/** Stable storage names with English display labels. */
public enum AgeGroup {
    ALL("All ages"),
    AGE_15_24("15 - 24 years"),
    AGE_15_74("15 - 74 years"),
    AGE_20_24("20 - 24 years"),
    AGE_25_29("25 - 29 years"),
    AGE_25_54("25 - 54 years"),
    AGE_25_74("25 - 74 years"),
    AGE_30_34("30 - 34 years"),
    AGE_35_39("35 - 39 years"),
    AGE_40_44("40 - 44 years"),
    AGE_45_49("45 - 49 years"),
    AGE_50_54("50 - 54 years"),
    AGE_55_59("55 - 59 years"),
    AGE_60_64("60 - 64 years");

    private final String label;

    AgeGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
