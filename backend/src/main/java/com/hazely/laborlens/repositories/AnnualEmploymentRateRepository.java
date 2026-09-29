package com.hazely.laborlens.repositories;

import com.hazely.laborlens.entities.AnnualEmploymentRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Queries only the fixed annual_employment_rate mapping. */
public interface AnnualEmploymentRateRepository extends JpaRepository<AnnualEmploymentRate, Long>, JpaSpecificationExecutor<AnnualEmploymentRate> {}
