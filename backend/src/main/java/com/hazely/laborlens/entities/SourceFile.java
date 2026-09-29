package com.hazely.laborlens.entities;

import jakarta.persistence.*;
import lombok.Getter;

/** Read-only API mapping; ingestion owns writes to this table. */
@Getter
@Entity
@Table(name = "source_file")
public class SourceFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;
    @Column(name = "table_name", nullable = false, length = 64)
    private String tableName;
    @Column(name = "download_url", nullable = false, length = 1024)
    private String downloadUrl;
    @Column(name = "download_path", nullable = false, length = 1024)
    private String downloadPath;
    @Column(name = "checksum", nullable = false, columnDefinition = "CHAR(64)")
    private String checksum;
}
