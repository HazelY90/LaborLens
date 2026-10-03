package com.hazely.laborlens.services;

import com.hazely.laborlens.dtos.DataDtos.*;
import com.hazely.laborlens.entities.enums.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

/** Dataset-specific allowlists shared by metadata, query validation and comparisons. */
public enum DataCatalog {
    ANNUAL("annual-employment-rate", "Annual Employment Rate", "%", "annual",
            age(false), sex(), education(), region()),
    MONTHLY("monthly-unemployment-rate", "Monthly Unemployment Rate", "%", "monthly",
            dimension("ageGroup", "Age", AgeGroup::getLabel, AgeGroup.AGE_15_74, AgeGroup.AGE_15_24,
            AgeGroup.AGE_25_74), sex()),
    QUARTERLY("quarterly-employment-rate", "Quarterly Employment Rate", "%", "quarterly",
            age(true), sex(), education()),
    COUNT("quarterly-employment-count", "Quarterly Employment Count", "thousand persons", "quarterly",
            dimension("citizenship", "Citizenship", Citizenship::getLabel, Citizenship.values()),
            dimension("economicSector", "Economic Sector", EconomicSector::getLabel, EconomicSector.values()));

    private final Dataset metadata;
    DataCatalog(String id, String label, String unit, String granularity, Dimension... dimensions) {
        Map<String, String> defaults = new LinkedHashMap<>();
        for (var dim : dimensions) defaults.put(dim.key(), dim.values().get(0).code());
        metadata = new Dataset(id, label, unit, granularity,
                granularity.equals("monthly") ? List.of("trend") : List.of("trend", "comparison"),
                IntStream.rangeClosed(2019, 2025).boxed().toList(),
                granularity.equals("quarterly") ? List.of(1, 2, 3, 4) : List.of(),
                Collections.unmodifiableMap(defaults), List.of(dimensions));
    }

    public Dataset metadata() {
        return metadata;
    }

    public int periods() {
        return this == MONTHLY ? 12 : (this == ANNUAL ? 1 : 4);
    }

    public String period(int year, int part) {
        return this == ANNUAL ? Integer.toString(year) : this == MONTHLY
                ? String.format(Locale.ROOT, "%04d-%02d", year, part) : year + "-Q" + part;
    }

    public static Metadata all() {
        return new Metadata(Arrays.stream(values()).map(DataCatalog::metadata).toList(),
                Arrays.stream(PolicyType.values()).map(type -> new Option(type.name(),
                type.getLabel())).toList());
    }

    private static Dimension age(boolean isQuarterly) {
        List<AgeGroup> ages = new ArrayList<>(List.of(AgeGroup.ALL, AgeGroup.AGE_20_24, AgeGroup.AGE_25_29));
        if (isQuarterly) ages.add(AgeGroup.AGE_25_54);
        ages.addAll(List.of(AgeGroup.AGE_30_34, AgeGroup.AGE_35_39, AgeGroup.AGE_40_44,
                AgeGroup.AGE_45_49, AgeGroup.AGE_50_54, AgeGroup.AGE_55_59, AgeGroup.AGE_60_64));
        return dimension("ageGroup", "Age", AgeGroup::getLabel, ages.toArray(AgeGroup[]::new));
    }

    private static Dimension sex() {
        return dimension("sex", "Sex", Sex::getLabel, Sex.values());
    }

    private static Dimension education() {
        return dimension("education", "Education", EducationLevel::getLabel, EducationLevel.values());
    }

    private static Dimension region() {
        return dimension("region", "Region", Region::getLabel, Region.values());
    }

    @SafeVarargs
    private static <E extends Enum<E>> Dimension dimension(String key, String label, Function<E,
            String> labels, E... values) {
        return new Dimension(key, label, Arrays.stream(values).map(v -> new Option(v.name(),
                labels.apply(v))).toList());
    }
}
