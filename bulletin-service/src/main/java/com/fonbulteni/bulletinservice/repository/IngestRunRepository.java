package com.fonbulteni.bulletinservice.repository;

import com.fonbulteni.bulletinservice.entity.IngestRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestRunRepository extends JpaRepository<IngestRun, Long> {
}
