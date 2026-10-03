package com.hazely.laborlens.repositories;

import com.hazely.laborlens.entities.QuarterlyEmploymentCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Queries only the fixed quarterly_employment_count mapping. */
public interface QuarterlyEmploymentCountRepository extends JpaRepository<QuarterlyEmploymentCount, Long>, JpaSpecificationExecutor<QuarterlyEmploymentCount> {
}
