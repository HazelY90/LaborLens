package com.hazely.laborlens.entities;

import jakarta.persistence.*;
import lombok.Getter;
import com.hazely.laborlens.entities.enums.*;
import java.util.Map;

/** Read model for the published monthly_unemployment_rate snapshot. */
@Getter
@Entity
@Table(name = "monthly_unemployment_rate")
public class MonthlyUnemploymentRate extends Metric {
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false, length = 32)
    private AgeGroup ageGroup;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 32)
    private Sex sex;
    @Column(nullable = false)
    private int month;
    @Column(name = "unemployment_rate_percent", nullable = false)
    private double value;

    public String period() { return String.format(java.util.Locale.ROOT, "%04d-%02d", getYear(), month); }

    public Map<String, String> dimensions() {
        return Map.of("ageGroup", ageGroup.name(), "sex", sex.name());
    }
}
