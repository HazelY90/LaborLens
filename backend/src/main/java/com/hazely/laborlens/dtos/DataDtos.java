package com.hazely.laborlens.dtos;

import java.util.List;
import java.util.Map;

/** Public response contracts; database entities are never serialized directly. */
public final class DataDtos {
    private DataDtos() {}
    public record Option(String code, String label) {}
    public record Dimension(String key, String label, List<Option> values) {}
    public record Dataset(String id, String label, String unit, String granularity,
                          List<String> views, List<Integer> years, List<Integer> quarters,
                          Map<String, String> defaults, List<Dimension> dimensions) {}
    public record Metadata(List<Dataset> datasets, List<Option> policyTypes) {}
    public record MetricFilter(String view, Integer year, Integer quarter, Map<String, String> dimensions) {}
    public sealed interface MetricResult permits Trend, Comparison {}
    public record Point(String period, Double value) {}
    public record Trend(String dataset, String view, String unit, Map<String, String> filters,
                        List<Point> points) implements MetricResult {}
    public record Bar(String code, Double value) {}
    public record Chart(String dimension, Map<String, String> fixedFilters, List<Bar> bars) {}
    public record Comparison(String dataset, String view, String unit, String period,
                             List<Chart> charts) implements MetricResult {}
    public record Source(Long id, String fileName, String url) {}
    public record PolicyItem(Long id, int periodStart, int periodEnd, String policy, Source source, Integer page) {}
    public record Policies(String type, List<PolicyItem> items) {}
    public record ApiError(String code, String message, String field) {}
}
