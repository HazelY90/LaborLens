package com.hazely.laborlens.services;

import com.hazely.laborlens.dtos.DataDtos.MetricFilter;
import com.hazely.laborlens.exceptions.QueryError;
import com.hazely.laborlens.entities.enums.PolicyType;
import org.springframework.util.MultiValueMap;
import java.util.*;

/** Parses strict single-value query parameters before any database access. */
public final class DataFilters {
    private DataFilters() {
    }

    public static MetricFilter metrics(DataCatalog data, MultiValueMap<String, String> params) {
        single(params);
        String view = params.getFirst("view");
        if (view == null) view = "trend";
        if (!data.metadata().views().contains(view)) throw invalid("view", "Unsupported view.");
        if (view.equals("comparison")) {
            allowed(params, data == DataCatalog.ANNUAL ? Set.of("view", "year") : Set.of("view",
                    "year", "quarter"));
            int year = number(params, "year", 2019, 2025);
            Integer quarter = data == DataCatalog.ANNUAL ? null : number(params, "quarter",
                    1, 4);
            return new MetricFilter(view, year, quarter, data.metadata().defaults());
        }
        Set<String> keys = new HashSet<>(data.metadata().defaults().keySet());
        keys.add("view");
        allowed(params, keys);
        Map<String, String> filters = new LinkedHashMap<>(data.metadata().defaults());
        for (var dim : data.metadata().dimensions()) {
            String value = params.getFirst(dim.key());
            if (value == null) continue;
            if (dim.values().stream().noneMatch(option -> option.code().equals(value))) {
                throw new QueryError("INVALID_FILTER", dim.key(), "Unsupported " + dim.key() + " for this dataset.");
            }
            filters.put(dim.key(), value);
        }
        return new MetricFilter(view, null, null, Collections.unmodifiableMap(filters));
    }

    public static PolicyType policy(MultiValueMap<String, String> params) {
        single(params);
        allowed(params, Set.of("type"));
        String type = params.getFirst("type");
        if (type == null) throw invalid("type", "Policy type is required.");
        try {
            return PolicyType.valueOf(type);
        }
        catch (IllegalArgumentException error) {
            throw new QueryError("INVALID_FILTER", "type", "Unsupported policy type.");
        }
    }

    public static void empty(MultiValueMap<String, String> params) {
        single(params);
        allowed(params, Set.of());
    }

    private static void single(MultiValueMap<String, String> params) {
        params.forEach((key, values) -> {
            if (values.size() != 1 || values.get(0) == null || values.get(0).isBlank() || values.get(0).contains(",")) {
                throw invalid(key, "Exactly one nonblank value is required.");
            }
        });
    }

    private static void allowed(MultiValueMap<String, String> params, Set<String> keys) {
        for (String key : params.keySet()) if (!keys.contains(key)) throw invalid(key, "Unsupported query parameter.");
    }

    private static int number(MultiValueMap<String, String> params, String key, int min, int max) {
        String raw = params.getFirst(key);
        try {
            if (raw == null || !raw.matches("[0-9]+")) throw new NumberFormatException();
            int value = Integer.parseInt(raw);
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw invalid(key, "Expected an integer from " + min + " to " + max + ".");
        }
    }

    private static QueryError invalid(String field, String message) {
        return new QueryError("INVALID_PARAMETER", field, message);
    }
}
