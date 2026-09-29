package com.hazely.laborlens.services;

import com.hazely.laborlens.dtos.DataDtos.*;
import com.hazely.laborlens.entities.*;
import com.hazely.laborlens.entities.enums.*;
import com.hazely.laborlens.repositories.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.util.*;

/** Reads stored observations and fills display slots without estimating missing values. */
@Service
@Transactional(readOnly = true)
public class MetricService {
    private final AnnualEmploymentRateRepository annual;
    private final MonthlyUnemploymentRateRepository monthly;
    private final QuarterlyEmploymentRateRepository quarterly;
    private final QuarterlyEmploymentCountRepository count;

    public MetricService(AnnualEmploymentRateRepository annual, MonthlyUnemploymentRateRepository monthly,
                         QuarterlyEmploymentRateRepository quarterly, QuarterlyEmploymentCountRepository count) {
        this.annual = annual;
        this.monthly = monthly;
        this.quarterly = quarterly;
        this.count = count;
    }
    public MetricResult query(DataCatalog data, MultiValueMap<String, String> params) {
        MetricFilter filter = DataFilters.metrics(data, params);
        List<? extends Metric> rows = switch (data) {
            case ANNUAL -> read(annual, filter);
            case MONTHLY -> read(monthly, filter);
            case QUARTERLY -> read(quarterly, filter);
            case COUNT -> read(count, filter);
        };
        return filter.view().equals("trend") ? trend(data, filter, rows) : comparison(data, filter, rows);
    }
    private <T extends Metric> List<T> read(JpaSpecificationExecutor<T> repo, MetricFilter filter) {
        Specification<T> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.between(root.get("year"), 2019, 2025));
            if (filter.view().equals("trend")) {
                filter.dimensions().forEach((key, code) -> predicates.add(cb.equal(root.get(key), enumValue(key, code))));
            } else {
                predicates.add(cb.equal(root.get("year"), filter.year()));
                if (filter.quarter() != null) predicates.add(cb.equal(root.get("quarter"), filter.quarter()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repo.findAll(spec);
    }
    private Enum<?> enumValue(String key, String code) {
        return switch (key) {
            case "ageGroup" -> AgeGroup.valueOf(code);
            case "sex" -> Sex.valueOf(code);
            case "education" -> EducationLevel.valueOf(code);
            case "region" -> Region.valueOf(code);
            case "citizenship" -> Citizenship.valueOf(code);
            case "economicSector" -> EconomicSector.valueOf(code);
            default -> throw new IllegalStateException("Unmapped dimension");
        };
    }
    private Trend trend(DataCatalog data, MetricFilter filter, List<? extends Metric> rows) {
        Map<String, Double> values = new HashMap<>();
        for (Metric row : rows) values.put(row.period(), row.getValue());
        List<Point> points = new ArrayList<>();
        for (int year : data.metadata().years()) {
            for (int part = 1; part <= data.periods(); part++) {
                String period = data.period(year, part);
                points.add(new Point(period, values.get(period)));
            }
        }
        return new Trend(data.metadata().id(), "trend", data.metadata().unit(), filter.dimensions(), List.copyOf(points));
    }
    private Comparison comparison(DataCatalog data, MetricFilter filter, List<? extends Metric> rows) {
        List<Chart> charts = new ArrayList<>();
        for (Dimension dim : data.metadata().dimensions()) {
            Map<String, String> fixed = new LinkedHashMap<>(data.metadata().defaults());
            fixed.remove(dim.key());
            Map<String, Double> values = new HashMap<>();
            for (Metric row : rows) {
                Map<String, String> dimensions = row.dimensions();
                if (fixed.entrySet().stream().allMatch(e -> e.getValue().equals(dimensions.get(e.getKey())))) {
                    values.put(dimensions.get(dim.key()), row.getValue());
                }
            }
            List<Bar> bars = dim.values().stream().map(v -> new Bar(v.code(), values.get(v.code()))).toList();
            charts.add(new Chart(dim.key(), Collections.unmodifiableMap(fixed), bars));
        }
        return new Comparison(data.metadata().id(), "comparison", data.metadata().unit(),
                data.period(filter.year(), filter.quarter() == null ? 1 : filter.quarter()), List.copyOf(charts));
    }
}
