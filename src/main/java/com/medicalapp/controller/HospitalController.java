package com.medicalapp.controller;

import com.medicalapp.model.*;
import com.medicalapp.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/hospital")
public class HospitalController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private HospitalAnnouncementRepository hospitalAnnouncementRepository;

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Long hospitalId = (Long) session.getAttribute("user_id");

        List<User> activeDoctors = userRepository.findByHospitalIdAndRoleAndHospitalApprovedAndIsApproved(hospitalId, "doctor", true, true);
        List<User> pendingDoctors = userRepository.findByHospitalIdAndRoleAndHospitalApproved(hospitalId, "doctor", false);
        List<User> pendingAdminDoctors = userRepository.findByHospitalIdAndRoleAndHospitalApprovedAndIsApproved(hospitalId, "doctor", true, false);

        List<Appointment> allAppts = appointmentRepository.findByDoctorHospitalId(hospitalId);
        List<Appointment> recentAppts = appointmentRepository.findByDoctorHospitalIdOrderByCreatedAtDesc(hospitalId).stream().limit(10).toList();

        long uniquePatients = allAppts.stream().map(a -> a.getPatient().getId()).distinct().count();
        double totalRevenue = allAppts.stream()
                .filter(a -> "approved".equalsIgnoreCase(a.getStatus()))
                .mapToDouble(Appointment::getAmount)
                .sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total_doctors", activeDoctors.size());
        stats.put("pending_approvals", pendingDoctors.size());
        stats.put("pending_admin_approvals", pendingAdminDoctors.size());
        stats.put("total_appointments", allAppts.size());
        stats.put("unique_patients", uniquePatients);
        stats.put("total_revenue", totalRevenue);

        model.addAttribute("active_doctors", activeDoctors);
        model.addAttribute("pending_doctors", pendingDoctors);
        model.addAttribute("pending_admin_doctors", pendingAdminDoctors);
        model.addAttribute("appointments", recentAppts);
        model.addAttribute("stats", stats);

        return "hospital_dashboard";
    }

    @GetMapping("/announcements")
    public String announcements(HttpSession session, Model model) {
        Long hospitalId = (Long) session.getAttribute("user_id");
        List<HospitalAnnouncement> list = hospitalAnnouncementRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId);
        model.addAttribute("announcements", list);
        return "hospital_announcements";
    }

    @PostMapping("/announcements")
    public String createAnnouncement(@RequestParam String title,
                                     @RequestParam String message,
                                     @RequestParam(required = false, defaultValue = "false") boolean visibleToPatients,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        Long hospitalId = (Long) session.getAttribute("user_id");
        User hospital = userRepository.findById(hospitalId).orElseThrow();

        HospitalAnnouncement ann = new HospitalAnnouncement();
        ann.setHospital(hospital);
        ann.setTitle(title);
        ann.setMessage(message);
        ann.setVisibleToPatients(visibleToPatients);

        hospitalAnnouncementRepository.save(ann);
        redirectAttributes.addFlashAttribute("success", "Announcement published successfully!");
        return "redirect:/hospital/announcements";
    }
}
