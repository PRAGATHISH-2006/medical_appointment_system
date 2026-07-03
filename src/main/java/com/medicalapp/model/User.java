package com.medicalapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // admin, doctor, patient

    @Column(nullable = false)
    private String name;

    private String specialization;
    private String qualification;
    private String experience;
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(name = "is_approved")
    private boolean isApproved = false;

    @Column(name = "consultation_fee")
    private double consultationFee = 0.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id")
    private User hospital;

    @Column(name = "hospital_approved")
    private boolean hospitalApproved = false;

    @Column(name = "hospital_approval_message", length = 1000)
    private String hospitalApprovalMessage;

    @Column(name = "availability_status")
    private String availabilityStatus = "Active";

    @Column(name = "hospital_specialties", length = 500)
    private String hospitalSpecialties;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }

    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public boolean isApproved() { return isApproved; }
    public void setApproved(boolean approved) { isApproved = approved; }

    public double getConsultationFee() { return consultationFee; }
    public void setConsultationFee(double consultationFee) { this.consultationFee = consultationFee; }

    public User getHospital() { return hospital; }
    public void setHospital(User hospital) { this.hospital = hospital; }

    public boolean isHospitalApproved() { return hospitalApproved; }
    public void setHospitalApproved(boolean hospitalApproved) { this.hospitalApproved = hospitalApproved; }

    public String getHospitalApprovalMessage() { return hospitalApprovalMessage; }
    public void setHospitalApprovalMessage(String hospitalApprovalMessage) { this.hospitalApprovalMessage = hospitalApprovalMessage; }

    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }

    public String getHospitalSpecialties() { return hospitalSpecialties; }
    public void setHospitalSpecialties(String hospitalSpecialties) { this.hospitalSpecialties = hospitalSpecialties; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
