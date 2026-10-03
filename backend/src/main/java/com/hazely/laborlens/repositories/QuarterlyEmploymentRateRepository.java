package com.hazely.laborlens.repositories;

import com.hazely.laborlens.entities.QuarterlyEmploymentRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Queries only the fixed quarterly_employment_rate mapping. */
public interface QuarterlyEmploymentRateRepository extends JpaRepository<QuarterlyEmploymentRate, Long>, JpaSpecificationExecutor<QuarterlyEmploymentRate> {
}
