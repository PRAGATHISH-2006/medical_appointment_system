package com.medicalapp.repository;

import com.medicalapp.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<Appointment> findByPatientIdAndDateAndStatus(Long patientId, String date, String status);
    List<Appointment> findByPatientIdAndStatusOrderByDateDesc(Long patientId, String status);
    List<Appointment> findByDoctorIdAndStatus(Long doctorId, String status);
    List<Appointment> findByDoctorIdAndStatusOrderByCreatedAtDesc(Long doctorId, String status);
    List<Appointment> findByDoctorHospitalIdOrderByCreatedAtDesc(Long hospitalId);
    List<Appointment> findByDoctorHospitalId(Long hospitalId);
}
