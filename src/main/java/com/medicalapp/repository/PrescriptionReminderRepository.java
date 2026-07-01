package com.medicalapp.repository;

import com.medicalapp.model.PrescriptionReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PrescriptionReminderRepository extends JpaRepository<PrescriptionReminder, Long> {
    List<PrescriptionReminder> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<PrescriptionReminder> findByPatientIdAndIsActive(Long patientId, boolean isActive);
}
