package com.hazely.laborlens.data;

import com.hazely.laborlens.config.SecurityConfig;
import com.hazely.laborlens.controllers.*;
import com.hazely.laborlens.exceptions.DataErrors;
import com.hazely.laborlens.repositories.*;
import com.hazely.laborlens.services.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import com.hazely.laborlens.security.RefreshCookie;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

/** Exercise the real controller, validation, services and authenticated access without a live database. */
@WebMvcTest(DataController.class)
@Import({SecurityConfig.class, DataErrors.class, MetricService.class, PolicyService.class, PolicyEvidence.class, DataApiTests.CookieConfig.class})
class DataApiTests {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;


    @MockitoBean AnnualEmploymentRateRepository annual;
    @MockitoBean MonthlyUnemploymentRateRepository monthly;
    @MockitoBean QuarterlyEmploymentRateRepository quarterly;
    @MockitoBean QuarterlyEmploymentCountRepository count;
    @MockitoBean PolicyRepository policies;
    @MockitoBean JobRunRepository runs;

    @org.springframework.boot.test.context.TestConfiguration
    static class CookieConfig {
        @org.springframework.context.annotation.Bean
        RefreshCookie cookies() {
            var cookie = mock(RefreshCookie.class);
            when(cookie.origins()).thenReturn(List.of("http://localhost:5173"));
            return cookie;
        }
    }

    @Test
    void rejectsEveryEndpointWithoutCredentials() throws Exception {
        for (String path : new String[]{"metadata", "annual-employment-rate", "monthly-unemployment-rate",
                "quarterly-employment-rate", "quarterly-employment-count", "policies?type=SKILLS_DEVELOPMENT"}) {
            mvc.perform(get("/api/data/" + path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @WithMockUser
    void returnsMetadataForAllPages() throws Exception {
        mvc.perform(get("/api/data/metadata")).andExpect(status().isOk())
                .andExpect(jsonPath("$.datasets.length()").value(4))
                .andExpect(jsonPath("$.policyTypes.length()").value(5))
                .andExpect(jsonPath("$.datasets[0].defaults.region").value("IRELAND"))
                .andExpect(jsonPath("$.datasets[1].defaults.ageGroup").value("AGE_15_74"))
                .andExpect(jsonPath("$.datasets[0].dimensions[0].values.length()").value(10))
                .andExpect(jsonPath("$.datasets[2].dimensions[0].values.length()").value(11));
        verifyNoInteractions(annual, monthly, quarterly, count, policies, runs);
    }

    @Test
    @WithMockUser
    void preservesMissingSlotsForEveryGranularity() throws Exception {
        mvc.perform(get("/api/data/annual-employment-rate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points.length()").value(7))
                .andExpect(jsonPath("$.points[0].period").value("2019"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"value\":null")));
        mvc.perform(get("/api/data/monthly-unemployment-rate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points.length()").value(84))
                .andExpect(jsonPath("$.points[83].period").value("2025-12"));
        mvc.perform(get("/api/data/quarterly-employment-rate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points.length()").value(28));
        mvc.perform(get("/api/data/quarterly-employment-count"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points[27].period").value("2025-Q4"));
    }

    @Test
    @WithMockUser
    void returnsAllComparisonChartsAndEmptyPolicies() throws Exception {
        mvc.perform(get("/api/data/annual-employment-rate?view=comparison&year=2024"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.charts.length()").value(4))
                .andExpect(jsonPath("$.charts[0].fixedFilters.region").value("IRELAND"));
        mvc.perform(get("/api/data/quarterly-employment-rate?view=comparison&year=2024&quarter=2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.charts.length()").value(3))
                .andExpect(jsonPath("$.period").value("2024-Q2"));
        mvc.perform(get("/api/data/quarterly-employment-count?view=comparison&year=2024&quarter=2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.charts.length()").value(2));
        mvc.perform(get("/api/data/policies?type=SKILLS_DEVELOPMENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        verifyNoInteractions(runs);
    }

    @Test
    @WithMockUser
    void rejectsInvalidFiltersBeforeQuerying() throws Exception {
        for (String path : new String[]{
                "annual-employment-rate?ageGroup=AGE_15_74", "annual-employment-rate?sex=FEMALE&sex=ALL",
                "annual-employment-rate?sex=FEMALE,ALL", "annual-employment-rate?sex=", "annual-employment-rate?year=2024",
                "annual-employment-rate?table=policy", "annual-employment-rate?view=comparison",
                "annual-employment-rate?view=comparison&year=2024&sex=ALL",
                "monthly-unemployment-rate?view=comparison&year=2024", "monthly-unemployment-rate?ageGroup=ALL",
                "quarterly-employment-rate?view=comparison&year=2024", "quarterly-employment-rate?region=IRELAND",
                "quarterly-employment-count?view=comparison&year=2024&quarter=5",
                "quarterly-employment-count?view=comparison&year=2018&quarter=1",
                "policies", "policies?type=UNKNOWN", "metadata?type=ALL"}) {
            mvc.perform(get("/api/data/" + path)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").exists()).andExpect(jsonPath("$.field").exists());
        }
        verifyNoInteractions(annual, monthly, quarterly, count, policies, runs);
    }

    @Test
    @WithMockUser
    void hidesDatabaseErrorDetails() throws Exception {
        when(policies.findByTypeOrderByPeriodStartAscPeriodEndAscSourceIdAscIdAsc(any()))
                .thenThrow(new IllegalStateException("private SQL details"));
        mvc.perform(get("/api/data/policies?type=SKILLS_DEVELOPMENT"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
    }
}
