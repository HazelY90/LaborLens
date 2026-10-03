package com.hazely.laborlens.services;

import com.hazely.laborlens.dtos.DataDtos.*;
import com.hazely.laborlens.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.util.*;

/** Resolves source links and optional citations without mixing report editions. */
@Service
@Transactional(readOnly = true)
public class PolicyService {
    private final PolicyRepository policies;
    private final JobRunRepository runs;
    private final PolicyEvidence evidence;

    public PolicyService(PolicyRepository policies, JobRunRepository runs, PolicyEvidence evidence) {
        this.policies = policies;
        this.runs = runs;
        this.evidence = evidence;
    }

    public Policies query(MultiValueMap<String, String> params) {
        var type = DataFilters.policy(params);
        var rows = policies.findByTypeOrderByPeriodStartAscPeriodEndAscSourceIdAscIdAsc(type);
        if (rows.isEmpty()) return new Policies(type.name(), List.of());
        var sources = rows.stream().map(p -> p.getSource().getId()).distinct().toList();
        Map<Long, Map<Long, PolicyEvidence.Citation>> pages = new HashMap<>();
        for (var run : runs.latestPolicies(sources)) pages.put(run.getSourceFileId(), evidence.read(run.getId()));
        List<PolicyItem> items = rows.stream().map(row -> {
            var source = row.getSource();
            var citation = pages.getOrDefault(source.getId(), Map.of()).get(row.getId());
            Integer page = citation != null && source.getFileName().equals(citation.source()) ? citation.page() : null;
            return new PolicyItem(row.getId(), row.getPeriodStart(), row.getPeriodEnd(),
                    row.getPolicy(),
                    new Source(source.getId(), source.getFileName(), source.getDownloadUrl()),
                    page);
        }).toList();
        return new Policies(type.name(), items);
    }
}
