package com.medicalapp.repository;

import com.medicalapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByRole(String role);
    List<User> findByRoleAndIsApproved(String role, boolean isApproved);
    List<User> findByHospitalId(Long hospitalId);
    List<User> findByHospitalIdAndRole(Long hospitalId, String role);
    List<User> findByHospitalIdAndRoleAndHospitalApproved(Long hospitalId, String role, boolean hospitalApproved);
    List<User> findByHospitalIdAndRoleAndHospitalApprovedAndIsApproved(Long hospitalId, String role, boolean hospitalApproved, boolean isApproved);
    List<User> findByRoleAndHospitalApprovedAndIsApproved(String role, boolean hospitalApproved, boolean isApproved);
}
