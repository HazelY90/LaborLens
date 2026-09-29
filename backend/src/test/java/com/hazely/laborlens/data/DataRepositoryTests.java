package com.hazely.laborlens.data;

import com.hazely.laborlens.dtos.DataDtos.*;
import com.hazely.laborlens.entities.*;
import com.hazely.laborlens.entities.enums.*;
import com.hazely.laborlens.repositories.*;
import com.hazely.laborlens.services.*;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Validates V1 mappings and real query predicates in an isolated database only. */
@Testcontainers(disabledWithoutDocker = true)
class DataRepositoryTests {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Test
    void queriesMigratedTablesAndCommittedHistory() {
        var ds = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var sql = new JdbcTemplate(ds);
        sql.update("INSERT INTO annual_employment_rate (year,age_group,sex,education_attainment_level,nuts_2_region,employment_rate_percent) "
                + "VALUES (2023,'ALL','ALL','ALL','IRELAND',0),(2025,'ALL','ALL','ALL','IRELAND',70),"
                + "(2025,'ALL','FEMALE','ALL','IRELAND',65),(2025,'AGE_30_34','FEMALE','ALL','IRELAND',99)");
        sql.update("INSERT INTO monthly_unemployment_rate (year,month,age_group,sex,unemployment_rate_percent) "
                + "VALUES (2025,12,'AGE_15_74','ALL',4.5),(2025,12,'AGE_15_24','ALL',9)");
        sql.update("INSERT INTO quarterly_employment_rate (year,quarter,age_group,sex,education_attainment_level,employment_rate_percent) "
                + "VALUES (2024,1,'ALL','ALL','ALL',60),(2024,2,'ALL','ALL','ALL',70)");
        sql.update("INSERT INTO quarterly_employment_count (year,quarter,citizenship,economic_sector,employed_persons_thousands) "
                + "VALUES (2024,1,'ALL','ALL',100),(2024,2,'ALL','ALL',200)");
        sql.update("INSERT INTO source_file (file_name,table_name,download_url,download_path,checksum) "
                + "VALUES ('report.pdf','policy','https://example.org/report.pdf','policies/report.pdf',REPEAT('a',64))");
        sql.update("INSERT INTO policy (period_start,period_end,policy,type,source_file_id) "
                + "VALUES (2025,2028,'Later','SKILLS_DEVELOPMENT',1),(2023,2025,'Earlier','SKILLS_DEVELOPMENT',1),"
                + "(2023,2025,'Other','WORKING_CONDITIONS',1)");
        for (String status : List.of("SUCCESS", "SUCCESS", "FAILED", "SKIPPED")) {
            sql.update("INSERT INTO job_run (job_type,source_file_id,file_name,checksum,status,started_at,finished_at,row_count,process_version) "
                    + "VALUES ('POLICY_EXTRACTION',1,'report.pdf',REPEAT('a',64),?,NOW(),NOW(),2,'v1')", status);
        }
        var config = new Configuration().setProperty("hibernate.connection.url", mysql.getJdbcUrl())
                .setProperty("hibernate.connection.username", mysql.getUsername())
                .setProperty("hibernate.connection.password", mysql.getPassword())
                .setProperty("hibernate.hbm2ddl.auto", "validate");
        for (Class<?> entity : List.of(AnnualEmploymentRate.class, MonthlyUnemploymentRate.class,
                QuarterlyEmploymentRate.class, QuarterlyEmploymentCount.class, SourceFile.class, Policy.class, JobRun.class)) {
            config.addAnnotatedClass(entity);
        }
        try (var factory = config.buildSessionFactory(); var em = factory.createEntityManager()) {
            var repos = new JpaRepositoryFactory(em);
            var service = new MetricService(repos.getRepository(AnnualEmploymentRateRepository.class),
                    repos.getRepository(MonthlyUnemploymentRateRepository.class),
                    repos.getRepository(QuarterlyEmploymentRateRepository.class), repos.getRepository(QuarterlyEmploymentCountRepository.class));
            var empty = new LinkedMultiValueMap<String, String>();
            var annual = (Trend) service.query(DataCatalog.ANNUAL, empty);
            assertEquals(0.0, annual.points().get(4).value()); assertNull(annual.points().get(5).value());
            assertEquals(70.0, annual.points().get(6).value());
            var filter = new LinkedMultiValueMap<String, String>(); filter.add("sex", "FEMALE");
            assertEquals(65.0, ((Trend) service.query(DataCatalog.ANNUAL, filter)).points().get(6).value());
            assertEquals(4.5, ((Trend) service.query(DataCatalog.MONTHLY, empty)).points().get(83).value());
            var period = new LinkedMultiValueMap<String, String>();
            period.add("view", "comparison"); period.add("year", "2024"); period.add("quarter", "2");
            assertEquals(70.0, ((Comparison) service.query(DataCatalog.QUARTERLY, period)).charts().get(0).bars().get(0).value());
            assertEquals(200.0, ((Comparison) service.query(DataCatalog.COUNT, period)).charts().get(0).bars().get(0).value());
            var policies = repos.getRepository(PolicyRepository.class)
                    .findByTypeOrderByPeriodStartAscPeriodEndAscSourceIdAscIdAsc(PolicyType.SKILLS_DEVELOPMENT);
            assertEquals(List.of("Earlier", "Later"), policies.stream().map(Policy::getPolicy).toList());
            assertEquals("report.pdf", policies.get(0).getSource().getFileName());
            assertEquals(2L, repos.getRepository(JobRunRepository.class).latestPolicies(List.of(1L)).get(0).getId());
        }
    }
}
