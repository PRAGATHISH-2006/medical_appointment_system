package com.medicalapp.controller;

import com.medicalapp.model.MedicalReport;
import com.medicalapp.model.PrescriptionReminder;
import com.medicalapp.model.User;
import com.medicalapp.repository.MedicalReportRepository;
import com.medicalapp.repository.PrescriptionReminderRepository;
import com.medicalapp.repository.UserRepository;
import com.medicalapp.service.AiAgentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/ai")
public class AiAgentController {

    @Autowired
    private AiAgentService aiAgentService;

    @Autowired
    private MedicalReportRepository medicalReportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PrescriptionReminderRepository prescriptionReminderRepository;

    @PostMapping("/symptom-checker")
    public ResponseEntity<Map<String, Object>> symptomChecker(@RequestBody Map<String, String> payload) {
        String symptoms = payload.get("symptoms");
        if (symptoms == null || symptoms.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Symptoms text is required");
            return ResponseEntity.badRequest().body(err);
        }

        Map<String, Object> advice = aiAgentService.checkSymptoms(symptoms);
        
        // Convert Suggested Doctors to simple list to avoid serialization loops or heavy lazy loading
        List<User> rawDocs = (List<User>) advice.get("suggestedDoctors");
        List<Map<String, Object>> docList = new ArrayList<>();
        for (User u : rawDocs) {
            Map<String, Object> dMap = new HashMap<>();
            dMap.put("id", u.getId());
            dMap.put("name", u.getName());
            dMap.put("specialization", u.getSpecialization());
            dMap.put("qualification", u.getQualification());
            dMap.put("fee", u.getConsultationFee());
            docList.add(dMap);
        }
        
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("condition", advice.get("condition"));
        body.put("guidance", advice.get("guidance"));
        body.put("specialization", advice.get("specialization"));
        body.put("suggestedDoctors", docList);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/hospital-info")
    public ResponseEntity<Map<String, Object>> hospitalInfo(@RequestBody Map<String, String> payload) {
        String query = payload.get("query");
        if (query == null || query.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Query text is required");
            return ResponseEntity.badRequest().body(err);
        }

        String reply = aiAgentService.answerHospitalQuery(query);
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("reply", reply);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/schedule-parsing")
    public ResponseEntity<Map<String, Object>> scheduleParsing(@RequestBody Map<String, String> payload, HttpSession session) {
        Long patientId = (Long) session.getAttribute("user_id");
        if (patientId == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
        }

        String text = payload.get("text");
        if (text == null || text.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Scheduling query is required");
            return ResponseEntity.badRequest().body(err);
        }

        User patient = userRepository.findById(patientId).orElseThrow();
        Map<String, Object> result = aiAgentService.parseAndSchedule(text, patient);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/summarize-report/{reportId}")
    public ResponseEntity<Map<String, Object>> summarizeReport(@PathVariable Long reportId, HttpSession session) {
        Long patientId = (Long) session.getAttribute("user_id");
        Optional<MedicalReport> reportOpt = medicalReportRepository.findById(reportId);

        if (reportOpt.isPresent()) {
            MedicalReport report = reportOpt.get();
            // Validate owner
            if (report.getPatient().getId().equals(patientId)) {
                String summary = aiAgentService.summarizeReport(report);
                Map<String, Object> body = new HashMap<>();
                body.put("success", true);
                body.put("summary", summary);
                return ResponseEntity.ok(body);
            }
        }

        Map<String, Object> err = new HashMap<>();
        err.put("error", "Report not found or unauthorized");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
    }

    @GetMapping("/prescription-reminders")
    public ResponseEntity<Map<String, Object>> getReminders(HttpSession session) {
        Long patientId = (Long) session.getAttribute("user_id");
        List<PrescriptionReminder> reminders = prescriptionReminderRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        
        List<Map<String, Object>> list = new ArrayList<>();
        for (PrescriptionReminder r : reminders) {
            Map<String, Object> rMap = new HashMap<>();
            rMap.put("id", r.getId());
            rMap.put("medicine_name", r.getMedicineName());
            rMap.put("time_of_day", r.getTimeOfDay());
            rMap.put("dosage", r.getDosage());
            rMap.put("is_active", r.isActive());
            list.add(rMap);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("reminders", list);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/prescription-reminders")
    @Transactional
    public ResponseEntity<Map<String, Object>> createReminder(@RequestBody Map<String, String> payload, HttpSession session) {
        Long patientId = (Long) session.getAttribute("user_id");
        String medicineName = payload.get("medicine_name");
        String timeOfDay = payload.get("time_of_day");
        String dosage = payload.get("dosage");

        if (medicineName == null || timeOfDay == null || dosage == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "All fields are required");
            return ResponseEntity.badRequest().body(err);
        }

        User patient = userRepository.findById(patientId).orElseThrow();
        PrescriptionReminder r = new PrescriptionReminder();
        r.setPatient(patient);
        r.setMedicineName(medicineName);
        r.setTimeOfDay(timeOfDay);
        r.setDosage(dosage);
        r.setActive(true);
        prescriptionReminderRepository.save(r);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "Reminder created successfully!");
        return ResponseEntity.ok(body);
    }

    @PostMapping("/prescription-reminders/delete/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteReminder(@PathVariable Long id, HttpSession session) {
        Long patientId = (Long) session.getAttribute("user_id");
        Optional<PrescriptionReminder> rOpt = prescriptionReminderRepository.findById(id);

        if (rOpt.isPresent()) {
            PrescriptionReminder r = rOpt.get();
            if (r.getPatient().getId().equals(patientId)) {
                prescriptionReminderRepository.delete(r);
                Map<String, Object> body = new HashMap<>();
                body.put("success", true);
                body.put("message", "Reminder deleted successfully!");
                return ResponseEntity.ok(body);
            }
        }

        Map<String, Object> err = new HashMap<>();
        err.put("error", "Reminder not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
    }
}
