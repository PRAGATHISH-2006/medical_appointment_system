package com.medicalapp.repository;

import com.medicalapp.model.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {
    List<AuditTrail> findAllByOrderByTimestampDesc();
}
