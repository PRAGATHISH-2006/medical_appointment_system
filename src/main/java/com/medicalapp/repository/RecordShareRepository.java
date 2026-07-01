package com.medicalapp.repository;

import com.medicalapp.model.RecordShare;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RecordShareRepository extends JpaRepository<RecordShare, Long> {
    List<RecordShare> findByDoctorId(Long doctorId);
    List<RecordShare> findByDoctorIdAndReportPatientId(Long doctorId, Long patientId);
    Optional<RecordShare> findByReportIdAndDoctorId(Long reportId, Long doctorId);
    void deleteByReportId(Long reportId);
}
