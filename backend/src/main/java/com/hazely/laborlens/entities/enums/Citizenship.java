package com.hazely.laborlens.entities.enums;

/** Stable storage names with English display labels. */
public enum Citizenship {
    ALL("All Countries"),
    IRELAND("Ireland"),
    EXCLUDING_IRELAND("All countries excluding Ireland"),
    OUTSIDE_EU_UK("All countries excluding Ireland,United Kingdom and EU272020");

    private final String label;

    Citizenship(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
