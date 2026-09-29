package com.hazely.laborlens.entities;

import jakarta.persistence.*;
import lombok.Getter;
import com.hazely.laborlens.entities.enums.*;
import java.util.Map;

/** Read model for the published quarterly_employment_count snapshot. */
@Getter
@Entity
@Table(name = "quarterly_employment_count")
public class QuarterlyEmploymentCount extends Metric {
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "citizenship", nullable = false, length = 32)
    private Citizenship citizenship;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "economic_sector", nullable = false, length = 32)
    private EconomicSector economicSector;
    @Column(nullable = false)
    private int quarter;
    @Column(name = "employed_persons_thousands", nullable = false)
    private double value;

    public String period() { return getYear() + "-Q" + quarter; }

    public Map<String, String> dimensions() {
        return Map.of("citizenship", citizenship.name(), "economicSector", economicSector.name());
    }
}
