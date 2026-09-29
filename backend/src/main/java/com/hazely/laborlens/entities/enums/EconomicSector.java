package com.hazely.laborlens.entities.enums;

/** Stable storage names with English display labels. */
public enum EconomicSector {
    ALL("All NACE Economic Sectors (A-V)"),
    AGRICULTURE("Agriculture, Forestry and Fishing (A)"),
    INDUSTRY_CONSTRUCTION("Industry and Construction (B-F)"),
    SERVICES("Services (G-V)"),
    INFORMATION_COMMUNICATION("Information and Communication (J,K)");

    private final String label;

    EconomicSector(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
