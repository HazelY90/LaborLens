package com.hazely.laborlens.controllers;

import com.hazely.laborlens.dtos.DataDtos.*;
import com.hazely.laborlens.services.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.MultiValueMap;

/** One authenticated read API for all five data pages and their shared metadata. */
@RestController
@RequestMapping("/api/data")
public class DataController {
    private final MetricService metrics;
    private final PolicyService policies;

    public DataController(MetricService metrics, PolicyService policies) {
        this.metrics = metrics;
        this.policies = policies;
    }

    @GetMapping("/metadata")
    public Metadata metadata(@RequestParam MultiValueMap<String, String> params) {
        DataFilters.empty(params);
        return DataCatalog.all();
    }

    @GetMapping("/annual-employment-rate")
    public MetricResult annual(@RequestParam MultiValueMap<String, String> params) {
        return metrics.query(DataCatalog.ANNUAL, params);
    }

    @GetMapping("/monthly-unemployment-rate")
    public MetricResult monthly(@RequestParam MultiValueMap<String, String> params) {
        return metrics.query(DataCatalog.MONTHLY, params);
    }

    @GetMapping("/quarterly-employment-rate")
    public MetricResult quarterly(@RequestParam MultiValueMap<String, String> params) {
        return metrics.query(DataCatalog.QUARTERLY, params);
    }

    @GetMapping("/quarterly-employment-count")
    public MetricResult count(@RequestParam MultiValueMap<String, String> params) {
        return metrics.query(DataCatalog.COUNT, params);
    }

    @GetMapping("/policies")
    public Policies policies(@RequestParam MultiValueMap<String, String> params) {
        return policies.query(params);
    }
}
