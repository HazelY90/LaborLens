package com.hazely.laborlens.data;

import com.hazely.laborlens.dtos.DataDtos.*;
import com.hazely.laborlens.entities.*;
import com.hazely.laborlens.entities.enums.*;
import com.hazely.laborlens.repositories.*;
import com.hazely.laborlens.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class DataServiceTests {
    @TempDir Path root;
    private final AnnualEmploymentRateRepository annual = mock(AnnualEmploymentRateRepository.class);
    private final MetricService metrics = new MetricService(annual, mock(MonthlyUnemploymentRateRepository.class),
            mock(QuarterlyEmploymentRateRepository.class), mock(QuarterlyEmploymentCountRepository.class));

    @Test
    void preservesZerosGapsAndChronologicalOrder() {
        when(annual.findAll(org.mockito.ArgumentMatchers.<Specification<AnnualEmploymentRate>>any()))
                .thenReturn(List.of(row(2025, Sex.ALL, AgeGroup.ALL, 70), row(2023, Sex.ALL, AgeGroup.ALL, 0)));
        var result = (Trend) metrics.query(DataCatalog.ANNUAL, new LinkedMultiValueMap<>());
        assertEquals(7, result.points().size());
        assertEquals(new Point("2023", 0.0), result.points().get(4));
        assertEquals(new Point("2024", null), result.points().get(5));
        assertEquals(new Point("2025", 70.0), result.points().get(6));
    }

    @Test
    void fixesOtherDimensionsToOverallForEachChart() {
        when(annual.findAll(org.mockito.ArgumentMatchers.<Specification<AnnualEmploymentRate>>any()))
                .thenReturn(List.of(row(2024, Sex.ALL, AgeGroup.ALL, 70), row(2024, Sex.FEMALE, AgeGroup.ALL, 65),
                        row(2024, Sex.ALL, AgeGroup.AGE_30_34, 80), row(2024, Sex.FEMALE, AgeGroup.AGE_30_34, 99)));
        var params = new LinkedMultiValueMap<String, String>();
        params.add("view", "comparison"); params.add("year", "2024");
        var result = (Comparison) metrics.query(DataCatalog.ANNUAL, params);
        var sex = result.charts().stream().filter(c -> c.dimension().equals("sex")).findFirst().orElseThrow();
        assertEquals(List.of(new Bar("ALL", 70.0), new Bar("FEMALE", 65.0), new Bar("MALE", null)), sex.bars());
        var age = result.charts().get(0);
        assertEquals(80.0, age.bars().stream().filter(v -> v.code().equals("AGE_30_34")).findFirst().orElseThrow().value());
    }

    @Test
    void readsOptionalEvidenceAndRejectsSymlinks() throws Exception {
        var evidence = new PolicyEvidence(root.toString());
        Path dir = Files.createDirectories(root.resolve("runs/5"));
        Path file = dir.resolve("evidence.json");
        Files.writeString(file, "[{\"policy_id\":8,\"policy\":{\"page\":3,\"source_file\":\"report.pdf\"}}]");
        assertEquals(3, evidence.read(5).get(8L).page());
        assertTrue(evidence.read(6).isEmpty());
        Files.writeString(file, "broken");
        assertTrue(evidence.read(5).isEmpty());
        Path other = Files.createDirectory(root.resolve("other"));
        Files.writeString(other.resolve("evidence.json"), "[]");
        Files.createSymbolicLink(root.resolve("runs/6"), other);
        assertTrue(evidence.read(6).isEmpty());
    }

    @Test
    void joinsCitationsByPolicyIdAndSource() throws Exception {
        var repo = mock(PolicyRepository.class);
        var runs = mock(JobRunRepository.class);
        var evidence = mock(PolicyEvidence.class);
        var source = new SourceFile();
        set(source, "id", 2L); set(source, "fileName", "report.pdf"); set(source, "downloadUrl", "https://example.org/report.pdf");
        var first = new Policy();
        set(first, "id", 10L); set(first, "source", source); set(first, "periodStart", 2023); set(first, "periodEnd", 2025);
        set(first, "policy", "Training action");
        var second = new Policy(); set(second, "id", 11L); set(second, "source", source);
        var run = new JobRun(); set(run, "id", 20L); set(run, "sourceFileId", 2L);
        when(repo.findByTypeOrderByPeriodStartAscPeriodEndAscSourceIdAscIdAsc(PolicyType.SKILLS_DEVELOPMENT))
                .thenReturn(List.of(first, second));
        when(runs.latestPolicies(List.of(2L))).thenReturn(List.of(run));
        when(evidence.read(20)).thenReturn(Map.of(10L, new PolicyEvidence.Citation("report.pdf", 3),
                11L, new PolicyEvidence.Citation("different.pdf", 4)));
        var params = new LinkedMultiValueMap<String, String>(); params.add("type", "SKILLS_DEVELOPMENT");
        var result = new PolicyService(repo, runs, evidence).query(params);
        assertEquals(3, result.items().get(0).page());
        assertNull(result.items().get(1).page());
        assertEquals("report.pdf", result.items().get(0).source().fileName());
        verify(evidence, times(1)).read(20);
    }

    private static AnnualEmploymentRate row(int year, Sex sex, AgeGroup age, double value) {
        var row = new AnnualEmploymentRate();
        set(row, "year", year); set(row, "sex", sex); set(row, "ageGroup", age);
        set(row, "education", EducationLevel.ALL); set(row, "region", Region.IRELAND); set(row, "value", value);
        return row;
    }
    private static void set(Object target, String field, Object value) { ReflectionTestUtils.setField(target, field, value); }
}
