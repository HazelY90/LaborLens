package com.hazely.laborlens.repositories;

import com.hazely.laborlens.entities.JobRun;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

/** Only committed extraction runs can supply evidence for current policy rows. */
public interface JobRunRepository extends JpaRepository<JobRun, Long> {
    @Query(value = "SELECT j.* FROM job_run j JOIN "
            + "(SELECT source_file_id, MAX(id) id FROM job_run WHERE source_file_id IN (:sources) "
            + "AND job_type = 'POLICY_EXTRACTION' AND status = 'SUCCESS' GROUP BY source_file_id) latest "
            + "ON j.id = latest.id", nativeQuery = true)
    List<JobRun> latestPolicies(@Param("sources") Collection<Long> sources);
}
