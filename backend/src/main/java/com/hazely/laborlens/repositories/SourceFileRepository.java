package com.hazely.laborlens.repositories;

import com.hazely.laborlens.entities.SourceFile;
import org.springframework.data.jpa.repository.JpaRepository;

/** Source registry read access for API consumers. */
public interface SourceFileRepository extends JpaRepository<SourceFile, Long> {}
