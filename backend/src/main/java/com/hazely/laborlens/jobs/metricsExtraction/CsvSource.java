package com.hazely.laborlens.jobs.metricsExtraction;

import java.util.List;
import com.hazely.laborlens.jobs.SourceSpec;

/** Fixed routing prevents source metadata from becoming executable SQL. */
public enum CsvSource implements SourceSpec {
    ALF01("annual_employment_rate", "ALF01C01", "%", "TLIST(A1)",
            List.of("Age Group", "Sex", "Education Attainment Level", "NUTS 2 Region"),
            "year,age_group,sex,education_attainment_level,nuts_2_region,employment_rate_percent"),
    MUM01("monthly_unemployment_rate", "MUM01C02", "%", "TLIST(M1)",
            List.of("Age Group", "Sex"), "year,month,age_group,sex,unemployment_rate_percent"),
    QLF50("quarterly_employment_rate", "QLF50C01", "%", "TLIST(Q1)",
            List.of("Age Group", "Sex", "Education Attainment Level"),
            "year,quarter,age_group,sex,education_attainment_level,employment_rate_percent"),
    QLF59("quarterly_employment_count", "QLF59C01", "Thousand", "TLIST(Q1)",
            List.of("Citizenship", "NACE Rev 2.1 Economic Sector"),
            "year,quarter,citizenship,economic_sector,employed_persons_thousands");

    final String table;
    final String statistic;
    final String unit;
    final String period;
    final List<String> dimensions;
    final String columns;

    CsvSource(String table, String statistic, String unit, String period,
            List<String> dimensions, String columns) {
        this.table = table;
        this.statistic = statistic;
        this.unit = unit;
        this.period = period;
        this.dimensions = dimensions;
        this.columns = columns;
    }

    public String table() {
        return table;
    }

    public String fileName() {
        return name() + ".csv";
    }

    public String path() {
        return "cso/" + fileName();
    }

    public String url() {
        return "https://ws.cso.ie/public/api.restful/PxStat.Data.Cube_API.ReadDataset/"
                + name() + "/CSV/1.0/en";
    }

    public String insertSql() {
        int size = columns.split(",").length;
        return "INSERT INTO " + table + " (" + columns + ") VALUES ("
                + String.join(",", java.util.Collections.nCopies(size, "?")) + ")";
    }
}
