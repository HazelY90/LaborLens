package com.hazely.laborlens.entities;

import com.hazely.laborlens.entities.enums.PolicyType;
import jakarta.persistence.*;
import lombok.Getter;

/** A policy retains the strategy period and source of its report. */
@Getter
@Entity
@Table(name = "policy")
public class Policy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "period_start", nullable = false)
    private int periodStart;
    @Column(name = "period_end", nullable = false)
    private int periodEnd;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String policy;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PolicyType type;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_file_id")
    private SourceFile source;
}
