package com.medicalapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicalapp.model.Appointment;
import com.medicalapp.model.MedicalReport;
import com.medicalapp.model.User;
import com.medicalapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    @Value("${gemini.api.key:YOUR_GEMINI_API_KEY_HERE}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─────────────────────────────────────────────────────────────────────────
    //  GEMINI INTEGRATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if a real Gemini API key has been configured.
     */
    public boolean isGeminiConfigured() {
        return geminiApiKey != null
                && !geminiApiKey.isBlank()
                && !geminiApiKey.equals("YOUR_GEMINI_API_KEY_HERE");
    }

    /**
     * Calls the Gemini generativeLanguage REST API with a full system+user prompt.
     * Returns the generated text or null if not configured / on error.
     */
    public String callGemini(String systemInstruction, String userMessage) {
        if (geminiApiKey == null || geminiApiKey.isBlank() || geminiApiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
            return null; // Signal: fallback to rule-based
        }

        // DYNAMIC ADAPTER: Groq vs Google Gemini
        boolean isGroq = geminiApiKey.startsWith("gsk_");

        if (isGroq) {
            String url = "https://api.groq.com/openai/v1/chat/completions";

            // Build request body for OpenAI-compatible Chat endpoint
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", geminiModel.startsWith("gemini") ? "llama-3.1-8b-instant" : geminiModel);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 1024);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemInstruction));
            messages.add(Map.of("role", "user", "content", userMessage));
            requestBody.put("messages", messages);

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(geminiApiKey);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List choices = (List) response.getBody().get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map choice = (Map) choices.get(0);
                        Map message = (Map) choice.get("message");
                        if (message != null) {
                            return (String) message.get("content");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[GroqService] API call failed: " + e.getMessage());
            }
            return null;
        } else {
            // Google Gemini API schema
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + geminiModel + ":generateContent?key=" + geminiApiKey;

            Map<String, Object> requestBody = new HashMap<>();

            Map<String, Object> systemPart = new HashMap<>();
            systemPart.put("text", systemInstruction);
            Map<String, Object> systemContent = new HashMap<>();
            systemContent.put("parts", List.of(systemPart));
            requestBody.put("system_instruction", systemContent);

            Map<String, Object> userPart = new HashMap<>();
            userPart.put("text", userMessage);
            Map<String, Object> userContent = new HashMap<>();
            userContent.put("role", "user");
            userContent.put("parts", List.of(userPart));
            requestBody.put("contents", List.of(userContent));

            Map<String, Object> genConfig = new HashMap<>();
            genConfig.put("temperature", 0.7);
            genConfig.put("maxOutputTokens", 1024);
            requestBody.put("generationConfig", genConfig);

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List candidates = (List) response.getBody().get("candidates");
                    if (candidates != null && !candidates.isEmpty()) {
                        Map candidate = (Map) candidates.get(0);
                        Map content = (Map) candidate.get("content");
                        List parts = (List) content.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            Map part = (Map) parts.get(0);
                            return (String) part.get("text");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[GeminiService] API call failed: " + e.getMessage());
            }
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GENERAL GEMINI CHAT (patient free-form queries)
    // ─────────────────────────────────────────────────────────────────────────

    public String handleGeminiChat(String agentType, String userMessage, String patientName) {
        // Build a context-rich system prompt per agent
        String systemPrompt = buildSystemPrompt(agentType, patientName);

        String geminiReply = callGemini(systemPrompt, userMessage);
        if (geminiReply != null) {
            return geminiReply;
        }

        // ── Fallback to built-in rule engine if no API key ──
        if (isGreetingOrChitChat(userMessage)) {
            return switch (agentType) {
                case "hospital" -> "Hello! I am the Hospital Info Desk assistant. How can I help you today? You can ask me about hospital timings, emergency hotline, departments, location, or billing/insurance details.";
                case "scheduler" -> "Hello! I am your Smart Appointment Scheduler. I can help parse your request to schedule an appointment. For example, you can type: 'Book with Dr. Jane Doe tomorrow at 10:00 AM' or tell me which doctor and time you prefer!";
                default -> "Hello! I am your AI Symptom Checker. Please describe the symptoms you are experiencing (e.g., headache, fever, sore throat, or cough), and I will help analyze potential causes and recommend a specialist.";
            };
        }

        return switch (agentType) {
            case "hospital" -> answerHospitalQuery(userMessage);
            case "scheduler" -> "Please specify a doctor name and preferred date/time. " +
                    "Example: \"Book appointment with Dr. Smith tomorrow at 10:00\"";
            default -> buildFallbackSymptomReply(userMessage);
        };
    }

    private String buildSystemPrompt(String agentType, String patientName) {
        String base = "You are a helpful medical AI assistant embedded in the Medicalcare patient portal. " +
                "The patient's name is " + (patientName != null ? patientName : "Patient") + ". " +
                "Always respond in a warm, empathetic, professional tone. " +
                "Keep responses concise (under 250 words). " +
                "Use bullet points for lists. " +
                "IMPORTANT: Always end with a disclaimer that you are an AI and the patient should consult a real doctor for medical decisions. ";

        List<User> approvedHospitals = userRepository.findByRoleAndIsApproved("hospital", true);
        String hospitalsInfo = approvedHospitals.isEmpty()
            ? "No registered hospitals in the system yet."
            : approvedHospitals.stream().map(h -> 
                "- " + h.getName() + " located at " + h.getAddress() + " (Phone: " + h.getPhone() + ", Specialties: " + (h.getHospitalSpecialties() != null ? h.getHospitalSpecialties() : "General") + ")"
              ).collect(Collectors.joining("\n"));

        List<User> activeDoctors = userRepository.findByRoleAndIsApproved("doctor", true);
        String doctorsInfo = activeDoctors.isEmpty()
            ? "No active doctors in the system currently."
            : activeDoctors.stream().map(d -> 
                "- Dr. " + d.getName() + " (" + d.getSpecialization() + ") at " + (d.getHospital() != null ? d.getHospital().getName() : "Independent") + " [Availability: " + d.getAvailabilityStatus() + "]"
              ).collect(Collectors.joining("\n"));

        return switch (agentType) {
            case "symptoms" -> base +
                    "You are a Symptom Checker Agent. " +
                    "When the patient describes symptoms: " +
                    "1) List 2-3 possible conditions. " +
                    "2) Give practical home-care advice. " +
                    "3) Indicate which medical specialty they should consult. " +
                    "4) Mention any red-flag warning signs that require immediate emergency care. " +
                    "Do NOT diagnose definitively — only suggest possibilities.\n\n" +
                    "Here are the active doctors and their hospitals in our system you can refer to:\n" + doctorsInfo;

            case "hospital" -> base +
                    "You are the Hospital Information Desk Agent for Medicalcare Hospital. " +
                    "Answer hospital-related questions. " +
                    "If asked anything unrelated to the hospitals, politely redirect.\n\n" +
                    "Here are the registered and approved hospitals in our network:\n" + hospitalsInfo + "\n\n" +
                    "Here are the active doctors and their affiliations:\n" + doctorsInfo;

            case "scheduler" -> base +
                    "You are a Smart Appointment Scheduler Agent. " +
                    "Help the patient figure out when to book an appointment. " +
                    "Ask clarifying questions if the doctor name, hospital, or time/shift is missing. " +
                    "Provide scheduling advice and explain that they can use the 'Book Appointment' page. " +
                    "Do not actually create appointments yourself.\n\n" +
                    "Here are the active doctors, their hospitals, and their availability in our system:\n" + doctorsInfo;

            default -> base;
        };
    }

    private String buildFallbackSymptomReply(String symptoms) {
        Map<String, Object> result = checkSymptoms(symptoms);
        if ("Unknown".equals(result.get("condition"))) {
            return "I couldn't identify specific symptoms from your description. Could you please specify what symptoms you are experiencing (e.g. fever, headache, stomach ache, cough, joint pain)? This will help me recommend the right specialist.";
        }
        return "**Possible Condition**: " + result.get("condition") + "\n\n" +
                "**Guidance**: " + result.get("guidance") + "\n\n" +
                "**Recommended Specialist**: " + result.get("specialization") + "\n\n" +
                "_Note: This is a basic analysis. Please consult a real doctor for proper diagnosis._";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EXISTING RULE-BASED AGENTS (kept as fallbacks)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Symptom Checker Agent (rule-based fallback):
     * Analyzes patient symptoms, suggests possible conditions, gives guidance,
     * and finds matching specialist doctors in the database.
     */
    public Map<String, Object> checkSymptoms(String symptoms) {
        Map<String, Object> response = new HashMap<>();
        if (isGreetingOrChitChat(symptoms)) {
            response.put("condition", "No symptoms described");
            response.put("guidance", "Please describe your symptoms so I can assist you.");
            response.put("specialization", "None");
            response.put("suggestedDoctors", Collections.emptyList());
            return response;
        }

        String normalized = symptoms.toLowerCase();
        boolean matched = false;

        String condition = "";
        String guidance = "";
        String neededSpecialization = "";

        if (normalized.contains("throat") || normalized.contains("ear") || normalized.contains("nose") || normalized.contains("cough") || normalized.contains("sneeze") || normalized.contains("congestion")) {
            condition = "Acute Pharyngitis / Upper Respiratory Congestion";
            guidance = "Avoid cold items. Warm salt water gargles 3-4 times a day can relieve throat pain.";
            neededSpecialization = "General Medicine";
            matched = true;
        } else if (normalized.contains("fever") || normalized.contains("temp") || normalized.contains("chill") || normalized.contains("cold") || normalized.contains("flu")) {
            condition = "Viral Fever / Influenza-like Illness";
            guidance = "Monitor your body temperature regularly. Take paracetamol if needed, stay hydrated, and rest.";
            neededSpecialization = "General Medicine";
            matched = true;
        } else if (normalized.contains("heart") || normalized.contains("chest") || normalized.contains("breath") || normalized.contains("palpitation") || normalized.contains("cardio")) {
            condition = "Cardiovascular Strain / Chest Discomfort";
            guidance = "Rest immediately. Avoid physical exertion. If chest pain radiates, seek emergency services immediately.";
            neededSpecialization = "Cardiologist";
            matched = true;
        } else if (normalized.contains("skin") || normalized.contains("itch") || normalized.contains("rash") || normalized.contains("acne") || normalized.contains("allergy") || normalized.contains("eczema")) {
            condition = "Allergic Dermatitis / Eczema flare-up";
            guidance = "Do not scratch the affected area. Apply a soothing cold compress and avoid harsh chemical soaps.";
            neededSpecialization = "Dermatologist";
            matched = true;
        } else if (normalized.contains("tooth") || normalized.contains("teeth") || normalized.contains("gum") || normalized.contains("dent") || normalized.contains("mouth")) {
            condition = "Dental Caries / Gingival Inflammation";
            guidance = "Rinse with warm salt water. Avoid hot, cold, or sugary drinks. Brush gently.";
            neededSpecialization = "Dentist";
            matched = true;
        } else if (normalized.contains("bone") || normalized.contains("joint") || normalized.contains("fracture") || normalized.contains("sprain") || normalized.contains("muscle") || normalized.contains("backache") || normalized.contains("pain")) {
            condition = "Joint Strain / Osteoarthritis / Muscle Sprain";
            guidance = "Follow the R.I.C.E. protocol: Rest the area, Ice it, Compress with a bandage, and Elevate it.";
            neededSpecialization = "Orthopedist";
            matched = true;
        } else if (normalized.contains("head") || normalized.contains("migraine") || normalized.contains("dizziness") || normalized.contains("brain")) {
            condition = "Tension Headache / Migraine";
            guidance = "Rest in a quiet, dark room. Stay hydrated. OTC pain relievers may help mild cases.";
            neededSpecialization = "Neurologist";
            matched = true;
        } else if (normalized.contains("stomach") || normalized.contains("abdomen") || normalized.contains("nausea") || normalized.contains("vomit") || normalized.contains("diarrhea") || normalized.contains("digestion") || normalized.contains("acid")) {
            condition = "Gastrointestinal Distress / Gastroenteritis";
            guidance = "Stay hydrated with ORS or clear fluids. Avoid spicy or fatty foods. Rest your digestive system.";
            neededSpecialization = "Gastroenterologist";
            matched = true;
        }

        if (!matched) {
            response.put("condition", "Unknown");
            response.put("guidance", "Please describe your symptoms in more detail.");
            response.put("specialization", "General Medicine");
            response.put("suggestedDoctors", Collections.emptyList());
            return response;
        }

        // Query doctors with matched specialization (must be hospital-approved and active/not on leave)
        final String specSearch = neededSpecialization;
        List<User> matchingDoctors = userRepository.findByRoleAndIsApproved("doctor", true).stream()
                .filter(d -> d.isHospitalApproved() && "Active".equalsIgnoreCase(d.getAvailabilityStatus()))
                .filter(d -> d.getSpecialization() != null && d.getSpecialization().toLowerCase().contains(specSearch.toLowerCase()))
                .collect(Collectors.toList());

        // If no matching specialization, fallback to general active doctors
        if (matchingDoctors.isEmpty()) {
            matchingDoctors = userRepository.findByRoleAndIsApproved("doctor", true).stream()
                    .filter(d -> d.isHospitalApproved() && "Active".equalsIgnoreCase(d.getAvailabilityStatus()))
                    .collect(Collectors.toList());
        }

        response.put("condition", condition);
        response.put("guidance", guidance);
        response.put("specialization", neededSpecialization);
        response.put("suggestedDoctors", matchingDoctors);
        return response;
    }

    /**
     * Hospital Information Agent (rule-based fallback).
     */
    public String answerHospitalQuery(String query) {
        String normalized = query.toLowerCase();

        List<User> approvedHospitals = userRepository.findByRoleAndIsApproved("hospital", true);

        if (normalized.contains("hospital") || normalized.contains("list") || normalized.contains("available") || normalized.contains("registered")) {
            if (approvedHospitals.isEmpty()) {
                return "🏥 Currently, there are no approved hospitals registered in our network.";
            }
            StringBuilder sb = new StringBuilder("🏥 **Registered Hospitals in our network**:\n");
            for (User h : approvedHospitals) {
                sb.append("- **").append(h.getName()).append("**: located at ").append(h.getAddress())
                  .append(" (Phone: ").append(h.getPhone()).append(", Specialties: ").append(h.getHospitalSpecialties() != null ? h.getHospitalSpecialties() : "General")
                  .append(")\n");
            }
            return sb.toString();
        }

        if (normalized.contains("timing") || normalized.contains("hour") || normalized.contains("open") || normalized.contains("close")) {
            return "🏥 **General Hospital Timings**:\n- **General OPD**: Monday to Saturday, 9:00 AM - 6:00 PM\n- **Emergency / ICU**: Open 24/7, 365 Days\n- **Visitor Hours**: 4:00 PM - 7:00 PM Daily.";
        }
        if (normalized.contains("emergency") || normalized.contains("icu") || normalized.contains("trauma")) {
            return "🚨 **Emergency Services**:\nEmergency care and trauma units are fully staffed 24/7 at our hospitals.\n**General Hotline**: +91-98765-43210. Ambulances are available on-call.";
        }
        if (normalized.contains("department") || normalized.contains("specialist") || normalized.contains("doctors")) {
            return "🩺 **Specialty Departments**:\nOur network offers expert care in: General Medicine, Cardiology, Dermatology, Dentistry, Orthopedics.\nAll doctors are accredited healthcare practitioners.";
        }
        if (normalized.contains("location") || normalized.contains("address") || normalized.contains("map") || normalized.contains("where")) {
            if (!approvedHospitals.isEmpty()) {
                StringBuilder sb = new StringBuilder("📍 **Hospital Addresses**:\n");
                for (User h : approvedHospitals) {
                    sb.append("- **").append(h.getName()).append("**: ").append(h.getAddress()).append("\n");
                }
                return sb.toString();
            }
            return "📍 **Hospital Location**:\nWe are located at: **123 Healthcare Blvd, Medical District, Suite 500**.\n- Near Medical Square Metro Station.\n- Dedicated valet parking is available for patients at the entrance.";
        }
        if (normalized.contains("insurance") || normalized.contains("billing") || normalized.contains("pay") || normalized.contains("cost")) {
            return "💳 **Billing & Insurance**:\n- We support cash, UPI (GPay, PhonePe, Paytm), and major credit cards.\n- We accept leading corporate insurance policies (TPA cash-less support).\n- Direct consultation fees are paid securely during slot booking or at the clinic desk.";
        }

        return "🤖 **Hospital Info Desk**:\nI can help you with questions about our registered hospitals, visiting hours, emergency services, department details, insurance billing, and physical locations. Please refine your query!";
    }

    /**
     * Medical Report Summarizer: Formulates a structured summary.
     * Enhanced to also try Gemini if a description exists.
     */
    public String summarizeReport(MedicalReport report) {
        // Try Gemini if API key is configured and description is present
        if (report.getDescription() != null && !report.getDescription().isBlank()) {
            String prompt = "Summarize this medical report for a patient in plain, easy-to-understand language:\n\n" +
                    "Report Name: " + report.getReportName() + "\n" +
                    "Category: " + report.getCategory() + "\n" +
                    "Date: " + report.getReportDate() + "\n" +
                    "Notes: " + report.getDescription();

            String geminiSummary = callGemini(
                    "You are a medical AI assistant that summarizes medical reports in a patient-friendly way. " +
                    "Use markdown headings and bullet points. Keep it under 200 words. " +
                    "Always end with a note to consult their doctor for personalized advice.",
                    prompt
            );
            if (geminiSummary != null) {
                return "### 🤖 AI-Powered Report Analysis (Gemini)\n\n" + geminiSummary;
            }
        }

        // Rule-based fallback
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
            summary.append("- Outlines condition history and medical notes recorded by practitioner.\n");
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
     * Appointment Scheduling Agent: Parses natural language command.
     */
    public Map<String, Object> parseAndSchedule(String text, User patient) {
        Map<String, Object> result = new HashMap<>();
        String normalized = text.toLowerCase();

        // 1. Identify Hospital
        List<User> hospitals = userRepository.findByRoleAndIsApproved("hospital", true);
        User matchedHospital = null;
        for (User hosp : hospitals) {
            if (normalized.contains(hosp.getName().toLowerCase())) {
                matchedHospital = hosp;
                break;
            }
        }

        // 2. Identify Doctor
        List<User> doctors = userRepository.findByRoleAndIsApproved("doctor", true);
        User matchedDoctor = null;
        for (User doc : doctors) {
            if (normalized.contains(doc.getName().toLowerCase())) {
                // If a hospital is also identified, check doctor hospital assignment
                if (matchedHospital != null && doc.getHospital() != null && !doc.getHospital().getId().equals(matchedHospital.getId())) {
                    continue;
                }
                matchedDoctor = doc;
                break;
            }
        }

        // Auto-detect hospital from doctor if not explicitly requested
        if (matchedDoctor != null && matchedHospital == null && matchedDoctor.getHospital() != null) {
            matchedHospital = matchedDoctor.getHospital();
        }

        // 3. Identify Date
        LocalDate date = LocalDate.now();

        if (normalized.contains("tomorrow")) {
            date = LocalDate.now().plusDays(1);
        } else if (normalized.contains("day after tomorrow")) {
            date = LocalDate.now().plusDays(2);
        } else {
            Pattern datePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
            Matcher dateMatcher = datePattern.matcher(text);
            if (dateMatcher.find()) {
                try { date = LocalDate.parse(dateMatcher.group(1)); } catch (Exception ignored) {}
            }
        }

        // 4. Identify Time
        String time = "10:00";
        Pattern timePattern = Pattern.compile("(\\d{2}:\\d{2})");
        Matcher timeMatcher = timePattern.matcher(text);
        if (timeMatcher.find()) {
            time = timeMatcher.group(1);
        }

        // 5. Identify Shift
        String shift = "Morning";
        if (normalized.contains("afternoon")) {
            shift = "Afternoon";
        } else if (normalized.contains("evening") || normalized.contains("night")) {
            shift = "Evening";
        }

        // 6. Identify Symptoms
        String symptoms = "AI-scheduled consultation request";
        Pattern symptomPattern = Pattern.compile("(?:due to|because of|symptoms of|having)\\s+([^.,?!]+)");
        Matcher symptomMatcher = symptomPattern.matcher(normalized);
        if (symptomMatcher.find()) {
            symptoms = symptomMatcher.group(1).trim();
        }

        if (matchedDoctor == null) {
            result.put("success", false);
            String docList = doctors.stream()
                .filter(d -> d.isHospitalApproved() && "Active".equalsIgnoreCase(d.getAvailabilityStatus()))
                .map(d -> "Dr. " + d.getName() + " (" + d.getSpecialization() + ") at " + (d.getHospital() != null ? d.getHospital().getName() : "Independent"))
                .collect(Collectors.joining("\n"));
            result.put("reply", "I couldn't identify the doctor you want to book with. Please specify a doctor's name, e.g., 'Book an appointment with Dr. Jane Doe'.\n\n**Available Doctors**:\n" + docList);
            return result;
        }

        result.put("success", true);
        result.put("doctorId", matchedDoctor.getId());
        result.put("doctorName", matchedDoctor.getName());
        if (matchedHospital != null) {
            result.put("hospitalId", matchedHospital.getId());
            result.put("hospitalName", matchedHospital.getName());
        }
        result.put("shift", shift);
        result.put("date", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        result.put("time", time);
        result.put("symptoms", symptoms);
        result.put("fee", matchedDoctor.getConsultationFee());
        result.put("reply", "📅 **Ready to Schedule**!\nI parsed the following details from your request:\n" +
                "- **Hospital**: " + (matchedHospital != null ? matchedHospital.getName() : "None/Independent") + "\n" +
                "- **Doctor**: Dr. " + matchedDoctor.getName() + "\n" +
                "- **Date**: " + date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "\n" +
                "- **Time**: " + time + "\n" +
                "- **Shift**: " + shift + "\n" +
                "- **Symptoms**: " + symptoms + "\n\n" +
                "Click the submit button on the left to confirm your Booking Form.");

        return result;
    }

    /**
     * Checks if the user message is a standard greeting or basic chit-chat
     */
    public boolean isGreetingOrChitChat(String message) {
        if (message == null) return false;
        String cleanMessage = message.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "");
        
        List<String> greetings = Arrays.asList(
            "hi", "hello", "hey", "greetings", "good morning", "good afternoon", "good evening", "yo", "hi there", "hello there"
        );
        if (greetings.contains(cleanMessage)) {
            return true;
        }
        
        List<String> chitChat = Arrays.asList(
            "who are you", "what is your name", "whats your name", "how are you", "how r u", "thank you", "thanks", "thank u"
        );
        for (String phrase : chitChat) {
            if (cleanMessage.contains(phrase)) {
                return true;
            }
        }
        
        return false;
    }
}
