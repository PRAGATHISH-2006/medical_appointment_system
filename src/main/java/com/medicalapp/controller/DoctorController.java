package com.medicalapp.controller;

import com.medicalapp.model.*;
import com.medicalapp.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.Optional;

@Controller
public class DoctorController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CurrentDoctorRepository currentDoctorRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MedicalReportRepository medicalReportRepository;

    @Autowired
    private RecordShareRepository recordShareRepository;

    @Autowired
    private HospitalAnnouncementRepository hospitalAnnouncementRepository;

    @GetMapping("/doctor/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Long doctorId = (Long) session.getAttribute("user_id");
        User doc = userRepository.findById(doctorId).orElseThrow();

        List<Appointment> pendingAppointments = appointmentRepository.findByDoctorIdAndStatusOrderByCreatedAtDesc(doctorId, "pending");
        List<Appointment> approvedAppointments = appointmentRepository.findByDoctorIdAndStatusOrderByCreatedAtDesc(doctorId, "approved");
        List<CurrentDoctor> currentPatients = currentDoctorRepository.findByDoctorId(doctorId);

        // Limit approved appointments size matching legacy view
        List<Appointment> limitApproved = approvedAppointments.stream().limit(5).toList();

        // Get hospital announcements
        List<HospitalAnnouncement> announcements = null;
        if (doc.getHospital() != null) {
            announcements = hospitalAnnouncementRepository.findByHospitalIdOrderByCreatedAtDesc(doc.getHospital().getId());
        }

        model.addAttribute("pending_appointments", pendingAppointments);
        model.addAttribute("approved_appointments", limitApproved);
        model.addAttribute("current_patients", currentPatients);
        model.addAttribute("hospitalName", doc.getHospital() != null ? doc.getHospital().getName() : "None");
        model.addAttribute("availabilityStatus", doc.getAvailabilityStatus());
        model.addAttribute("announcements", announcements);

        return "doctor_dashboard";
    }

    @GetMapping("/doctor/prescription/{patientId}")
    public String prescriptionForm(@PathVariable Long patientId, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Long doctorId = (Long) session.getAttribute("user_id");

        Optional<CurrentDoctor> cdOpt = currentDoctorRepository.findByPatientIdAndDoctorId(patientId, doctorId);
        if (cdOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to write prescription for this patient!");
            return "redirect:/doctor/dashboard";
        }

        Optional<User> patientOpt = userRepository.findById(patientId);
        if (patientOpt.isPresent()) {
            model.addAttribute("patient", patientOpt.get());
            return "prescription";
        }

        return "redirect:/doctor/dashboard";
    }

    @PostMapping("/doctor/prescription/{patientId}")
    public String issuePrescription(@PathVariable Long patientId,
                                    @RequestParam String medicines,
                                    @RequestParam String dosage,
                                    @RequestParam String instructions,
                                    @RequestParam(required = false, defaultValue = "") String notes,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {

        Long doctorId = (Long) session.getAttribute("user_id");
        Optional<CurrentDoctor> cdOpt = currentDoctorRepository.findByPatientIdAndDoctorId(patientId, doctorId);

        if (cdOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to write prescription for this patient!");
            return "redirect:/doctor/dashboard";
        }

        User doctor = userRepository.findById(doctorId).orElseThrow();
        User patient = userRepository.findById(patientId).orElseThrow();
        CurrentDoctor currentDoctor = cdOpt.get();

        Prescription pres = new Prescription();
        pres.setDoctor(doctor);
        pres.setPatient(patient);
        pres.setCurrentDoctor(currentDoctor);
        pres.setMedicines(medicines);
        pres.setDosage(dosage);
        pres.setInstructions(instructions);
        pres.setNotes(notes);
        prescriptionRepository.save(pres);

        // Notify patient
        Notification notif = new Notification();
        notif.setUserId(patientId);
        notif.setType("general");
        notif.setMessage("A new prescription has been issued by Dr. " + doctor.getName() + ". You can view it in your dashboard.");
        notificationRepository.save(notif);

        redirectAttributes.addFlashAttribute("success", "Prescription created successfully!");
        return "redirect:/doctor/dashboard";
    }

    @GetMapping("/doctor/patient-records/{patientId}")
    public String viewPatientRecords(@PathVariable Long patientId, HttpSession session, Model model) {
        Long doctorId = (Long) session.getAttribute("user_id");
        User patient = userRepository.findById(patientId).orElseThrow();

        // Only show records specifically shared with this doctor
        List<MedicalReport> sharedReports = recordShareRepository.findByDoctorIdAndReportPatientId(doctorId, patientId)
                .stream().map(RecordShare::getReport).toList();

        model.addAttribute("patient", patient);
        model.addAttribute("reports", sharedReports);
        return "doctor_patient_records";
    }
}
