package com.medicalapp.controller;

import com.medicalapp.model.AuditTrail;
import com.medicalapp.model.User;
import com.medicalapp.repository.AppointmentRepository;
import com.medicalapp.repository.AuditTrailRepository;
import com.medicalapp.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AuditTrailRepository auditTrailRepository;

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        // Show doctors that are approved by their hospital but pending final admin approval
        List<User> pendingDoctors = userRepository.findByRoleAndHospitalApprovedAndIsApproved("doctor", true, false);
        List<User> allDoctors = userRepository.findByRoleAndIsApproved("doctor", true);
        List<User> allPatients = userRepository.findByRole("patient");
        List<User> pendingHospitals = userRepository.findByRoleAndIsApproved("hospital", false);
        List<User> allHospitals = userRepository.findByRoleAndIsApproved("hospital", true);

        Map<String, Object> stats = new HashMap<>();
        stats.put("total_patients", allPatients.size());
        stats.put("total_doctors", allDoctors.size());
        stats.put("pending_approvals", pendingDoctors.size() + pendingHospitals.size());
        stats.put("total_appointments", appointmentRepository.count());
        stats.put("total_hospitals", allHospitals.size());
        stats.put("pending_hospitals", pendingHospitals.size());

        model.addAttribute("pending_doctors", pendingDoctors);
        model.addAttribute("all_doctors", allDoctors);
        model.addAttribute("all_patients", allPatients);
        model.addAttribute("pending_hospitals", pendingHospitals);
        model.addAttribute("all_hospitals", allHospitals);
        model.addAttribute("stats", stats);

        // Fetch logs to embed directly in dashboard or a separate tab
        List<AuditTrail> logs = auditTrailRepository.findAllByOrderByTimestampDesc();
        model.addAttribute("audit_logs", logs);

        return "admin_dashboard";
    }

    @GetMapping("/admin/audit-logs")
    public String auditLogs(Model model) {
        List<AuditTrail> logs = auditTrailRepository.findAllByOrderByTimestampDesc();
        model.addAttribute("audit_logs", logs);
        return "audit_logs"; // Custom page we can create to show audit trails beautifully
    }
}
