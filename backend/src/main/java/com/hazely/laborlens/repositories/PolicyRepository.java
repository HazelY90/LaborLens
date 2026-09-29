package com.hazely.laborlens.repositories;

import com.hazely.laborlens.entities.Policy;
import com.hazely.laborlens.entities.enums.PolicyType;
import org.springframework.data.jpa.repository.*;
import java.util.List;

/** Fetch sources with policies to avoid one query per policy card. */
public interface PolicyRepository extends JpaRepository<Policy, Long> {
    @EntityGraph(attributePaths = "source")
    List<Policy> findByTypeOrderByPeriodStartAscPeriodEndAscSourceIdAscIdAsc(PolicyType type);
}
