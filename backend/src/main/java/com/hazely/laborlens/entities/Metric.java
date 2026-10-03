package com.hazely.laborlens.entities;

import jakarta.persistence.*;
import lombok.Getter;
import java.util.Map;

/** Common observation identity; missing observations are represented only in API DTOs. */
@Getter
@MappedSuperclass
public abstract class Metric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int year;

    public abstract double getValue();
    public abstract String period();
    public abstract Map<String, String> dimensions();
}
