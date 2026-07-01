package com.medicalapp.repository;

import com.medicalapp.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findByPatientId(Long patientId);
    List<Prescription> findByPatientIdAndDoctorIdOrderByCreatedAtDesc(Long patientId, Long doctorId);
    long countByPatientId(Long patientId);
}
