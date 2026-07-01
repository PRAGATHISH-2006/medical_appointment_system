package com.medicalapp.repository;

import com.medicalapp.model.CurrentDoctor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CurrentDoctorRepository extends JpaRepository<CurrentDoctor, Long> {
    List<CurrentDoctor> findByPatientId(Long patientId);
    List<CurrentDoctor> findByDoctorId(Long doctorId);
    Optional<CurrentDoctor> findByPatientIdAndDoctorId(Long patientId, Long doctorId);
}
