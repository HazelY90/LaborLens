package com.hazely.laborlens.entities;

import jakarta.persistence.*;
import lombok.Getter;
import com.hazely.laborlens.entities.enums.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/** Run history read model; ingestion owns status changes and transaction boundaries. */
@Getter
@Entity
@Table(name = "job_run")
public class JobRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 32)
    private JobType jobType;

    @Column(name = "source_file_id")
    private Long sourceFileId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(columnDefinition = "CHAR(64)")
    private String checksum;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "process_version", nullable = false, length = 128)
    private String processVersion;
}
