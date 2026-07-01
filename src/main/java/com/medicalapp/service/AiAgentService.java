package com.medicalapp.service;

import com.medicalapp.model.Appointment;
import com.medicalapp.model.MedicalReport;
import com.medicalapp.model.User;
import com.medicalapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiAgentService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Symptom Checker Agent:
     * Analyzes patient symptoms, suggests possible conditions, gives guidance,
     * and finds matching specialist doctors in the database.
     */
    public Map<String, Object> checkSymptoms(String symptoms) {
        Map<String, Object> response = new HashMap<>();
        String normalized = symptoms.toLowerCase();
        
        String condition = "Mild Viral Infection / General Fatigue";
        String guidance = "Ensure plenty of rest, stay well-hydrated with warm fluids, and monitor your temperature.";
        String neededSpecialization = "General Medicine";

        if (normalized.contains("throat") || normalized.contains("ear") || normalized.contains("nose") || normalized.contains("cough")) {
            condition = "Acute Pharyngitis / Upper Respiratory Congestion";
            guidance = "Avoid cold items. Warm salt water gargles 3-4 times a day can relieve throat pain.";
            neededSpecialization = "General Medicine";
        }
        if (normalized.contains("heart") || normalized.contains("chest") || normalized.contains("breath") || normalized.contains("palpitation")) {
            condition = "Cardiovascular Strain / Chest Discomfort";
            guidance = "Rest immediately. Avoid physical exertion. If chest pain radiates, seek emergency services immediately.";
            neededSpecialization = "Cardiologist";
        }
        if (normalized.contains("skin") || normalized.contains("itch") || normalized.contains("rash") || normalized.contains("acne")) {
            condition = "Allergic Dermatitis / Eczema flare-up";
            guidance = "Do not scratch the affected area. Apply a soothing cold compress and avoid harsh chemical soaps.";
            neededSpecialization = "Dermatologist";
        }
        if (normalized.contains("tooth") || normalized.contains("teeth") || normalized.contains("gum") || normalized.contains("dent")) {
            condition = "Dental Caries / Gingival Inflammation";
            guidance = "Rinse with warm salt water. Avoid hot, cold, or sugary drinks. Brush gently.";
            neededSpecialization = "Dentist";
        }
        if (normalized.contains("bone") || normalized.contains("joint") || normalized.contains("fracture") || normalized.contains("sprain")) {
            condition = "Joint Strain / Osteoarthritis / Mild Sprain";
            guidance = "Follow the R.I.C.E. protocol: Rest the area, Ice it, Compress with a bandage, and Elevate it.";
            neededSpecialization = "Orthopedist";
        }

        // Query doctors with matched specialization
        final String specSearch = neededSpecialization;
        List<User> matchingDoctors = userRepository.findByRoleAndIsApproved("doctor", true).stream()
                .filter(d -> d.getSpecialization() != null && d.getSpecialization().toLowerCase().contains(specSearch.toLowerCase()))
                .collect(Collectors.toList());

        // If no matching specialization, fallback to general doctors
        if (matchingDoctors.isEmpty()) {
            matchingDoctors = userRepository.findByRoleAndIsApproved("doctor", true);
        }

        response.put("condition", condition);
        response.put("guidance", guidance);
        response.put("specialization", neededSpecialization);
        response.put("suggestedDoctors", matchingDoctors);
        return response;
    }

    /**
     * Hospital Information Agent:
     * Answers queries about hospital administration, departments, and timings.
     */
    public String answerHospitalQuery(String query) {
        String normalized = query.toLowerCase();

        if (normalized.contains("timing") || normalized.contains("hour") || normalized.contains("open") || normalized.contains("close")) {
            return "🏥 **Hospital Timings**:\n- **General OPD**: Monday to Saturday, 9:00 AM - 6:00 PM\n- **Emergency / ICU**: Open 24/7, 365 Days\n- **Visitor Hours**: 4:00 PM - 7:00 PM Daily.";
        }
        if (normalized.contains("emergency") || normalized.contains("icu") || normalized.contains("trauma")) {
            return "🚨 **Emergency Services**:\nOur emergency care and trauma unit is fully staffed 24/7. \n**Emergency Hotline**: +91-98765-43210. Ambulances are available on-call.";
        }
        if (normalized.contains("department") || normalized.contains("specialist") || normalized.contains("doctors")) {
            return "🩺 **Specialty Departments**:\nWe offer expert care in:\n- **General Medicine** (Primary Care)\n- **Cardiology** (Heart)\n- **Dermatology** (Skin)\n- **Dentistry** (Teeth)\n- **Orthopedics** (Bones & Joints)\nAll doctors are accredited healthcare practitioners.";
        }
        if (normalized.contains("location") || normalized.contains("address") || normalized.contains("map") || normalized.contains("where")) {
            return "📍 **Hospital Location**:\nWe are located at: **123 Healthcare Blvd, Medical District, Suite 500**.\n- Near Medical Square Metro Station.\n- Dedicated valet parking is available for patients at the entrance.";
        }
        if (normalized.contains("insurance") || normalized.contains("billing") || normalized.contains("pay") || normalized.contains("cost")) {
            return "💳 **Billing & Insurance**:\n- We support cash, UPI (GPay, PhonePe, Paytm), and major credit cards.\n- We accept leading corporate insurance policies (TPA cash-less support).\n- Direct consultation fees are paid securely during slot booking or at the clinic desk.";
        }

        return "🤖 **Hospital Info Desk**:\nI can help you with questions about visiting hours, emergency services, department details, insurance billing, and our physical location. Please refine your query!";
    }

    /**
     * Medical Report Summarizer:
     * Formulates a structured summary highlighting critical metrics and recommendations.
     */
    public String summarizeReport(MedicalReport report) {
        StringBuilder summary = new StringBuilder();
        summary.append("### 📝 AI Medical Report Analysis\n\n");
        summary.append("**Report Name**: ").append(report.getReportName()).append("\n");
        summary.append("**Date of Report**: ").append(report.getReportDate() != null ? report.getReportDate() : "N/A").append("\n");
        summary.append("**Category**: ").append(report.getCategory()).append("\n\n");

        summary.append("#### 🔍 Key Findings:\n");
        if ("Prescription".equalsIgnoreCase(report.getCategory())) {
            summary.append("- Issued treatment plan containing therapeutic medicines.\n");
            summary.append("- Follow medication regimen strictly matching daily dosage specifications.\n");
        } else if ("Test Report".equalsIgnoreCase(report.getCategory())) {
            summary.append("- Biochemical lab values scanned.\n");
            summary.append("- Check indicator levels relative to baseline healthy ranges (e.g. cholesterol, CBC, blood sugar).\n");
        } else if ("Diagnosis".equalsIgnoreCase(report.getCategory())) {
            summary.append("- Diagnostic clinical assessment details identified.\n");
            summary.append("- Outlines condition history and medical notes recorded by practitioner: ").append(report.getDoctorName()).append(".\n");
        } else {
            summary.append("- General medical documentation archived.\n");
        }

        if (report.getDescription() != null && !report.getDescription().trim().isEmpty()) {
            summary.append("- **Notes Detail**: ").append(report.getDescription()).append("\n");
        }

        summary.append("\n#### ⚠️ Critical Focus/Warning Signs:\n");
        summary.append("- Monitor for side-effects, abnormal fatigue, or fever spikes.\n");
        summary.append("- In case of sudden breathing distress or pain, report to Emergency care immediately.\n");

        summary.append("\n#### 💡 Action Recommendations:\n");
        summary.append("- Keep a digital log of physical symptoms.\n");
        if (report.getDoctorName() != null && !report.getDoctorName().isEmpty()) {
            summary.append("- Schedule a review session with **Dr. ").append(report.getDoctorName()).append("** to discuss indicators.\n");
        } else {
            summary.append("- Consult a doctor if test values display abnormal deviation markers.\n");
        }

        return summary.toString();
    }

    /**
     * Appointment Scheduling Agent:
     * Parses natural language command, extracts parameters, and tries to schedule a slot.
     */
    public Map<String, Object> parseAndSchedule(String text, User patient) {
        Map<String, Object> result = new HashMap<>();
        String normalized = text.toLowerCase();

        // 1. Identify Doctor
        List<User> doctors = userRepository.findByRoleAndIsApproved("doctor", true);
        User matchedDoctor = null;
        for (User doc : doctors) {
            if (normalized.contains(doc.getName().toLowerCase())) {
                matchedDoctor = doc;
                break;
            }
        }

        // 2. Identify Date (e.g. tomorrow, next Monday, specific date)
        LocalDate date = LocalDate.now();
        boolean dateFound = false;

        if (normalized.contains("tomorrow")) {
            date = LocalDate.now().plusDays(1);
            dateFound = true;
        } else if (normalized.contains("day after tomorrow")) {
            date = LocalDate.now().plusDays(2);
            dateFound = true;
        } else {
            // Regex to find date like YYYY-MM-DD or MM/DD
            Pattern datePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
            Matcher dateMatcher = datePattern.matcher(text);
            if (dateMatcher.find()) {
                try {
                    date = LocalDate.parse(dateMatcher.group(1));
                    dateFound = true;
                } catch (Exception e) {}
            }
        }

        // 3. Identify Time
        String time = "10:00"; // default slot
        Pattern timePattern = Pattern.compile("(\\d{2}:\\d{2})");
        Matcher timeMatcher = timePattern.matcher(text);
        if (timeMatcher.find()) {
            time = timeMatcher.group(1);
        }

        // 4. Identify Symptoms
        String symptoms = "AI-scheduled consultation request";
        Pattern symptomPattern = Pattern.compile("(?:due to|because of|symptoms of|having)\\s+([^.,?!]+)");
        Matcher symptomMatcher = symptomPattern.matcher(normalized);
        if (symptomMatcher.find()) {
            symptoms = symptomMatcher.group(1).trim();
        }

        if (matchedDoctor == null) {
            result.put("success", false);
            result.put("reply", "I couldn't identify the doctor you want to book with. Please specify a doctor's name, e.g., 'Book an appointment with Dr. Jane Doe'.\n\n**Available Doctors**:\n" + 
                    doctors.stream().map(d -> "Dr. " + d.getName() + " (" + d.getSpecialization() + ")").collect(Collectors.joining("\n")));
            return result;
        }

        result.put("success", true);
        result.put("doctorId", matchedDoctor.getId());
        result.put("doctorName", matchedDoctor.getName());
        result.put("date", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        result.put("time", time);
        result.put("symptoms", symptoms);
        result.put("fee", matchedDoctor.getConsultationFee());
        result.put("reply", "📅 **Ready to Schedule**!\nI parsed the following details from your request:\n" +
                "- **Doctor**: Dr. " + matchedDoctor.getName() + "\n" +
                "- **Date**: " + date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "\n" +
                "- **Time**: " + time + "\n" +
                "- **Symptoms**: " + symptoms + "\n\n" +
                "Click the submit button on the left to confirm your Booking Form.");

        return result;
    }
}
