package com.medicalapp.controller;

import com.medicalapp.model.*;
import com.medicalapp.repository.*;
import com.medicalapp.service.NotificationService;
import com.medicalapp.service.PdfService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Controller
public class PatientController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private MedicalReportRepository medicalReportRepository;

    @Autowired
    private RecordShareRepository recordShareRepository;

    @Autowired
    private AuditTrailRepository auditTrailRepository;

    @Autowired
    private CurrentDoctorRepository currentDoctorRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PdfService pdfService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @GetMapping("/patient/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Long patientId = (Long) session.getAttribute("user_id");
        notificationService.checkAndGenerateNotifications(patientId);

        List<CurrentDoctor> currentDoctors = currentDoctorRepository.findByPatientId(patientId);
        List<Appointment> appointments = appointmentRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        List<Notification> unreadNotifs = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(patientId, false);
        long prescriptionCount = prescriptionRepository.countByPatientId(patientId);

        // Keep list sizes matching legacy view limits
        List<Appointment> limitAppointments = appointments.stream().limit(5).toList();
        List<Notification> limitNotifs = unreadNotifs.stream().limit(3).toList();

        model.addAttribute("current_doctors", currentDoctors);
        model.addAttribute("appointments", limitAppointments);
        model.addAttribute("notifications", limitNotifs);
        model.addAttribute("prescription_count", prescriptionCount);

        return "patient_dashboard";
    }

    @GetMapping("/patient/notifications")
    public String notifications(HttpSession session, Model model) {
        Long patientId = (Long) session.getAttribute("user_id");
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(patientId);
        model.addAttribute("notifications", notifications);
        return "notifications";
    }

    @GetMapping("/patient/medical-reports")
    public String medicalReports(HttpSession session, Model model) {
        Long patientId = (Long) session.getAttribute("user_id");
        List<MedicalReport> reports = medicalReportRepository.findByPatientId(patientId);
        List<User> doctors = userRepository.findByRoleAndIsApproved("doctor", true);

        model.addAttribute("reports", reports);
        model.addAttribute("doctors", doctors);
        return "medical_reports";
    }

    @PostMapping("/patient/medical-reports/upload")
    public String uploadReport(@RequestParam String report_name,
                               @RequestParam String category,
                               @RequestParam String report_date,
                               @RequestParam(required = false, defaultValue = "") String doctor_name,
                               @RequestParam(required = false, defaultValue = "") String description,
                               @RequestParam("report_file") MultipartFile file,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {

        Long patientId = (Long) session.getAttribute("user_id");

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No selected file");
            return "redirect:/patient/medical-reports";
        }

        try {
            // Secure filename generation
            String originalName = file.getOriginalFilename();
            String cleanName = originalName != null ? originalName.replaceAll("[^a-zA-Z0-9._-]", "_") : "file.pdf";
            String savedFilename = patientId + "_" + System.currentTimeMillis() + "_" + cleanName;

            File uploadFolder = new File(uploadDir);
            if (!uploadFolder.exists()) {
                uploadFolder.mkdirs();
            }

            File destFile = new File(uploadFolder, savedFilename);
            file.transferTo(destFile);

            // Save to DB
            MedicalReport report = new MedicalReport();
            report.setPatient(userRepository.findById(patientId).orElseThrow());
            report.setReportName(report_name);
            report.setCategory(category);
            report.setReportType(category); // Keep legacy field populated
            report.setReportDate(report_date);
            report.setDoctorName(doctor_name);
            report.setDescription(description);
            report.setFilePath(savedFilename);
            medicalReportRepository.save(report);

            // Log Audit Trail
            AuditTrail audit = new AuditTrail();
            audit.setUserId(patientId);
            audit.setAction("Upload");
            audit.setTargetType("MedicalReport");
            audit.setTargetId(report.getId());
            audit.setDetails("Uploaded " + report.getReportName() + " in category " + report.getCategory());
            auditTrailRepository.save(audit);

            redirectAttributes.addFlashAttribute("success", "Medical report uploaded successfully!");
        } catch (IOException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error uploading file");
        }

        return "redirect:/patient/medical-reports";
    }

    @PostMapping("/patient/medical-reports/delete/{reportId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteReport(@PathVariable Long reportId, HttpSession session) {
        Long patientId = (Long) session.getAttribute("user_id");
        Optional<MedicalReport> reportOpt = medicalReportRepository.findById(reportId);

        if (reportOpt.isPresent()) {
            MedicalReport report = reportOpt.get();
            if (report.getPatient().getId().equals(patientId)) {
                // Delete physical file
                if (report.getFilePath() != null) {
                    File file = new File(uploadDir, report.getFilePath());
                    if (file.exists()) {
                        file.delete();
                    }
                }

                // Log Audit Trail
                AuditTrail audit = new AuditTrail();
                audit.setUserId(patientId);
                audit.setAction("Delete");
                audit.setTargetType("MedicalReport");
                audit.setTargetId(report.getId());
                audit.setDetails("Deleted " + report.getReportName());
                auditTrailRepository.save(audit);

                // Remove shares and report
                List<RecordShare> shares = recordShareRepository.findByDoctorIdAndReportPatientId(null, patientId); // matching method
                recordShareRepository.deleteByReportId(report.getId());
                medicalReportRepository.delete(report);

                Map<String, Object> body = new HashMap<>();
                body.put("success", true);
                body.put("message", "Report deleted successfully!");
                return ResponseEntity.ok(body);
            }
        }

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", "Report not found or unauthorized");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody);
    }

    @PostMapping("/patient/medical-reports/share")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> shareReport(@RequestBody Map<String, Long> payload, HttpSession session) {
        Long patientId = (Long) session.getAttribute("user_id");
        Long reportId = payload.get("report_id");
        Long doctorId = payload.get("doctor_id");

        Optional<MedicalReport> reportOpt = medicalReportRepository.findById(reportId);
        Optional<User> doctorOpt = userRepository.findById(doctorId);

        if (reportOpt.isPresent() && doctorOpt.isPresent()) {
            MedicalReport report = reportOpt.get();
            User doctor = doctorOpt.get();

            if (report.getPatient().getId().equals(patientId) && "doctor".equalsIgnoreCase(doctor.getRole())) {
                Optional<RecordShare> shareOpt = recordShareRepository.findByReportIdAndDoctorId(reportId, doctorId);
                if (shareOpt.isEmpty()) {
                    RecordShare share = new RecordShare();
                    share.setReport(report);
                    share.setDoctor(doctor);
                    recordShareRepository.save(share);

                    // Log Audit Trail
                    AuditTrail audit = new AuditTrail();
                    audit.setUserId(patientId);
                    audit.setAction("Share");
                    audit.setTargetType("MedicalReport");
                    audit.setTargetId(reportId);
                    audit.setDetails("Shared " + report.getReportName() + " with Dr. " + doctor.getName());
                    auditTrailRepository.save(audit);

                    Map<String, Object> body = new HashMap<>();
                    body.put("success", true);
                    body.put("message", "Report shared with Dr. " + doctor.getName() + "!");
                    return ResponseEntity.ok(body);
                } else {
                    Map<String, Object> errorBody = new HashMap<>();
                    errorBody.put("error", "Already shared with this doctor");
                    return ResponseEntity.badRequest().body(errorBody);
                }
            }
        }

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", "Invalid request");
        return ResponseEntity.badRequest().body(errorBody);
    }

    @PostMapping("/patient/medical-reports/edit/{reportId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> editReport(@PathVariable Long reportId, @RequestBody Map<String, String> payload, HttpSession session) {
        Long patientId = (Long) session.getAttribute("user_id");
        Optional<MedicalReport> reportOpt = medicalReportRepository.findById(reportId);

        if (reportOpt.isPresent()) {
            MedicalReport report = reportOpt.get();
            if (report.getPatient().getId().equals(patientId)) {
                report.setReportName(payload.getOrDefault("report_name", report.getReportName()));
                report.setCategory(payload.getOrDefault("category", report.getCategory()));
                report.setReportDate(payload.getOrDefault("report_date", report.getReportDate()));
                report.setDescription(payload.getOrDefault("description", report.getDescription()));
                medicalReportRepository.save(report);

                // Log Audit Trail
                AuditTrail audit = new AuditTrail();
                audit.setUserId(patientId);
                audit.setAction("Update");
                audit.setTargetType("MedicalReport");
                audit.setTargetId(report.getId());
                audit.setDetails("Updated details for " + report.getReportName());
                auditTrailRepository.save(audit);

                Map<String, Object> body = new HashMap<>();
                body.put("success", true);
                body.put("message", "Report updated successfully!");
                return ResponseEntity.ok(body);
            }
        }

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", "Report not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody);
    }

    @GetMapping("/patient/appointment")
    public String appointment(Model model) {
        List<User> doctors = userRepository.findByRoleAndIsApproved("doctor", true);
        model.addAttribute("doctors", doctors);
        return "appointment";
    }

    @PostMapping("/patient/appointment")
    public String createAppointment(@RequestParam Long doctor_id,
                                    @RequestParam String date,
                                    @RequestParam String time,
                                    @RequestParam String symptoms,
                                    @RequestParam(required = false, defaultValue = "") String notes,
                                    @RequestParam(required = false, defaultValue = "offline") String payment_method,
                                    @RequestParam(required = false, defaultValue = "pending") String payment_status,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {

        Long patientId = (Long) session.getAttribute("user_id");
        User patient = userRepository.findById(patientId).orElseThrow();
        User doctor = userRepository.findById(doctor_id).orElseThrow();

        Appointment appt = new Appointment();
        appt.setPatient(patient);
        appt.setDoctor(doctor);
        appt.setDate(date);
        appt.setTime(time);
        appt.setSymptoms(symptoms);
        appt.setNotes(notes);
        appt.setPaymentMethod(payment_method);
        appt.setPaymentStatus(payment_status);
        appt.setAmount(doctor.getConsultationFee());
        appointmentRepository.save(appt);

        if ("online".equalsIgnoreCase(payment_method)) {
            redirectAttributes.addFlashAttribute("success", "Payment verified and appointment booked!");
        } else {
            redirectAttributes.addFlashAttribute("success", "Appointment request sent successfully! Please pay at clinic.");
        }

        return "redirect:/patient/dashboard";
    }

    @GetMapping("/patient/current-doctor")
    public String currentDoctors(HttpSession session, Model model) {
        Long patientId = (Long) session.getAttribute("user_id");
        List<CurrentDoctor> cds = currentDoctorRepository.findByPatientId(patientId);

        // Fetch prescription logs for each doctor relation
        Map<Long, List<Prescription>> prescriptionMap = new HashMap<>();
        for (CurrentDoctor cd : cds) {
            List<Prescription> pres = prescriptionRepository.findByPatientIdAndDoctorIdOrderByCreatedAtDesc(patientId, cd.getDoctor().getId());
            prescriptionMap.put(cd.getId(), pres);
        }

        model.addAttribute("current_doctors", cds);
        model.addAttribute("prescriptions", prescriptionMap);
        return "current_doctor";
    }

    @GetMapping("/patient/prescription/download/{prescriptionId}")
    public ResponseEntity<byte[]> downloadPrescription(@PathVariable Long prescriptionId, HttpSession session) {
        Long userId = (Long) session.getAttribute("user_id");
        String role = (String) session.getAttribute("role");

        Optional<Prescription> presOpt = prescriptionRepository.findById(prescriptionId);
        if (presOpt.isPresent()) {
            Prescription pres = presOpt.get();

            // Authorization validations
            if ("patient".equalsIgnoreCase(role) && !pres.getPatient().getId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            } else if ("doctor".equalsIgnoreCase(role) && !pres.getDoctor().getId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            byte[] pdfBytes = pdfService.generatePrescriptionPdf(pres);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "prescription_" + prescriptionId + ".pdf");
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/patient/ai-assistant")
    public String aiAssistant() {
        return "ai_assistant";
    }
}
