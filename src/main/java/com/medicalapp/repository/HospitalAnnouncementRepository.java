package com.medicalapp.repository;

import com.medicalapp.model.HospitalAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HospitalAnnouncementRepository extends JpaRepository<HospitalAnnouncement, Long> {
    List<HospitalAnnouncement> findByHospitalIdOrderByCreatedAtDesc(Long hospitalId);
    List<HospitalAnnouncement> findByHospitalIdAndVisibleToPatientsOrderByCreatedAtDesc(Long hospitalId, boolean visibleToPatients);
}
