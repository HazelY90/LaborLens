package com.hazely.laborlens.jobs.metricsExtraction;

import com.hazely.laborlens.entities.enums.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Applies the documented source filters before producing database-ready rows. */
public final class CsvParser {
    private static final Set<String> EDUCATION_EXCLUDED = Set.of(
            "Less than primary (Level 0)", "Level of education - not stated");
    private static final Set<String> CITIZENSHIP_EXCLUDED = Set.of(
            "EU14 excl Irl (countries in the EU pre 2004 excluding UK & Ireland)",
            "EU15 to EU27 (accession countries joined post 2004)", "United Kingdom");
    private static final Set<String> SECTOR_EXCLUDED = Set.of(
            "Construction (F)", "Wholesale and Retail Trade (G)", "Transportation and Storage (H)",
            "Accommodation and Food Service Activities (I)",
            "Professional, Scientific and Technical Activities (N)",
            "Administrative and Support Service Activities (O)",
            "Public Administration and Defence; Compulsory Social Security (P)",
            "Education (Q)", "Human Health and Social Work Activities (R)", "Industry (B-E)",
            "Financial, Insurance and Real Estate Activities (L,M)",
            "Other NACE Activities (S to V)", "NACE Unknown");
    private static final Set<String> MONTHLY_AGES = Set.of("AGE_15_24", "AGE_25_74", "AGE_15_74");
    private static final Set<String> ANNUAL_AGES = Set.of("ALL", "AGE_20_24", "AGE_25_29",
            "AGE_30_34", "AGE_35_39", "AGE_40_44", "AGE_45_49", "AGE_50_54", "AGE_55_59", "AGE_60_64");
    private static final Map<String, Map<String, String>> MAPPINGS = Map.of(
            "Age Group", labels(AgeGroup.values(), AgeGroup::getLabel),
            "Sex", labels(Sex.values(), Sex::getLabel),
            "Education Attainment Level", labels(EducationLevel.values(), EducationLevel::getLabel),
            "NUTS 2 Region", labels(Region.values(), Region::getLabel),
            "Citizenship", labels(Citizenship.values(), Citizenship::getLabel),
            "NACE Rev 2.1 Economic Sector", labels(EconomicSector.values(), EconomicSector::getLabel));

    public record Result(List<List<Object>> rows, long excluded, long missing) {
        public Result {
            rows = rows.stream().map(List::copyOf).toList();
        }
    }

    public Result parse(CsvSource source, byte[] bytes) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var csv = new CsvReader(new InputStreamReader(new ByteArrayInputStream(bytes), decoder))) {
            List<String> headers = csv.next();
            if (headers == null || headers.stream().anyMatch(String::isBlank)
                    || new HashSet<>(headers).size() != headers.size()) {
                throw new IllegalArgumentException("Empty or duplicate CSV headers");
            }
            List<String> required = new ArrayList<>(source.dimensions);
            required.addAll(List.of("STATISTIC", "UNIT", "VALUE", source.period));
            if (!headers.containsAll(required)) throw new IllegalArgumentException("Missing CSV headers");
            Map<String, Integer> indexes = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) indexes.put(headers.get(i), i);
            List<List<Object>> rows = new ArrayList<>();
            Set<List<Object>> keys = new HashSet<>();
            Set<String> periods = new HashSet<>();
            long excluded = 0;
            long missing = 0;
            long record = 1;
            List<String> cells;
            while ((cells = csv.next()) != null) {
                record++;
                if (cells.size() != headers.size()) throw invalid(record, "CSV column count");
                List<String> values = cells;
                Function<String, String> value = key -> values.get(indexes.get(key));
                String statistic = value.apply("STATISTIC");
                if (!source.statistic.equals(statistic)) {
                    if (source == CsvSource.MUM01 && statistic.equals("MUM01C01")) {
                        excluded++;
                        continue;
                    }
                    throw invalid(record, "Unknown statistic");
                }
                if (!source.unit.equals(value.apply("UNIT"))) throw invalid(record, "Unexpected unit");
                String period = value.apply(source.period);
                String pattern = source == CsvSource.ALF01 ? "[0-9]{4}"
                        : source == CsvSource.MUM01 ? "[0-9]{4}(0[1-9]|1[0-2])" : "[0-9]{4}[1-4]";
                if (!period.matches(pattern)) throw invalid(record, "Invalid period");
                int year = Integer.parseInt(period.substring(0, 4));
                if (year < 2019 || year > 2025) {
                    excluded++;
                    continue;
                }
                List<Object> row = new ArrayList<>();
                row.add(year);
                if (source != CsvSource.ALF01) row.add(Integer.parseInt(period.substring(4)));
                boolean isExcluded = false;
                for (String dimension : source.dimensions) {
                    String label = normalize(value.apply(dimension));
                    if (isExcluded(dimension, label)) {
                        isExcluded = true;
                        continue;
                    }
                    String mapped = MAPPINGS.get(dimension).get(label);
                    if (mapped == null) throw invalid(record, "Unknown " + dimension);
                    if (dimension.equals("Age Group") && !isAllowedAge(source, mapped)) {
                        throw invalid(record, "Unsupported age group");
                    }
                    row.add(mapped);
                }
                if (isExcluded) {
                    excluded++;
                    continue;
                }
                // Check every selected key, even when its measure is missing.
                if (!keys.add(List.copyOf(row))) throw invalid(record, "Duplicate observation key");
                periods.add(period);
                String raw = value.apply("VALUE");
                if (raw.isBlank()) {
                    missing++;
                    continue;
                }
                double measure;
                try {
                    if (!raw.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) {
                        throw new NumberFormatException();
                    }
                    measure = Double.parseDouble(raw);
                } catch (NumberFormatException error) {
                    throw invalid(record, "Invalid numeric value");
                }
                if (!Double.isFinite(measure) || measure < 0
                        || (source != CsvSource.QLF59 && measure > 100)) {
                    throw invalid(record, "Measure outside allowed bounds");
                }
                row.add(measure);
                rows.add(row);
            }
            // Coverage uses source periods, including missing values, rather than fixed row counts.
            int expected = source == CsvSource.ALF01 ? 7 : source == CsvSource.MUM01 ? 84 : 28;
            if (periods.size() != expected || rows.isEmpty()) {
                throw new IllegalArgumentException("Incomplete 2019-2025 period coverage or empty import");
            }
            return new Result(rows, excluded, missing);
        }
    }

    private static boolean isAllowedAge(CsvSource source, String age) {
        if (source == CsvSource.MUM01) return MONTHLY_AGES.contains(age);
        return ANNUAL_AGES.contains(age) || (source == CsvSource.QLF50 && age.equals("AGE_25_54"));
    }

    private static boolean isExcluded(String dimension, String label) {
        return switch (dimension) {
            case "Education Attainment Level" -> EDUCATION_EXCLUDED.contains(label);
            case "Citizenship" -> CITIZENSHIP_EXCLUDED.contains(label);
            case "NACE Rev 2.1 Economic Sector" -> SECTOR_EXCLUDED.contains(label);
            default -> false;
        };
    }

    private static IllegalArgumentException invalid(long record, String reason) {
        return new IllegalArgumentException(reason + " at CSV record " + record);
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static <E extends Enum<E>> Map<String, String> labels(E[] values, Function<E, String> label) {
        Map<String, String> result = new HashMap<>();
        for (E value : values) result.put(normalize(label.apply(value)), value.name());
        return Map.copyOf(result);
    }
}
