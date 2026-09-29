package com.hazely.laborlens.repositories;

import com.hazely.laborlens.entities.MonthlyUnemploymentRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Queries only the fixed monthly_unemployment_rate mapping. */
public interface MonthlyUnemploymentRateRepository extends JpaRepository<MonthlyUnemploymentRate, Long>, JpaSpecificationExecutor<MonthlyUnemploymentRate> {}
