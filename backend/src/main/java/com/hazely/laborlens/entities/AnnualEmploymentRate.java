package com.hazely.laborlens.entities;

import jakarta.persistence.*;
import lombok.Getter;
import com.hazely.laborlens.entities.enums.*;
import java.util.Map;

/** Read model for the published annual_employment_rate snapshot. */
@Getter
@Entity
@Table(name = "annual_employment_rate")
public class AnnualEmploymentRate extends Metric {
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false, length = 32)
    private AgeGroup ageGroup;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 32)
    private Sex sex;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "education_attainment_level", nullable = false, length = 32)
    private EducationLevel education;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "nuts_2_region", nullable = false, length = 32)
    private Region region;
    @Column(name = "employment_rate_percent", nullable = false)
    private double value;

    public String period() { return Integer.toString(getYear()); }

    public Map<String, String> dimensions() {
        return Map.of("ageGroup", ageGroup.name(), "sex", sex.name(), "education", education.name(), "region", region.name());
    }
}
