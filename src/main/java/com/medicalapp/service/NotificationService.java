package com.medicalapp.service;

import com.medicalapp.model.Appointment;
import com.medicalapp.model.Notification;
import com.medicalapp.model.Prescription;
import com.medicalapp.model.PrescriptionReminder;
import com.medicalapp.repository.AppointmentRepository;
import com.medicalapp.repository.NotificationRepository;
import com.medicalapp.repository.PrescriptionReminderRepository;
import com.medicalapp.repository.PrescriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PrescriptionReminderRepository prescriptionReminderRepository;

    @Transactional
    public void checkAndGenerateNotifications(Long userId) {
        // 1. Appointment Reminders (Check for appointments tomorrow)
        String tomorrowStr = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        List<Appointment> apptsTomorrow = appointmentRepository.findByPatientIdAndDateAndStatus(userId, tomorrowStr, "approved");

        for (Appointment appt : apptsTomorrow) {
            String message = "Reminder: Upcoming appointment with Dr. " + appt.getDoctor().getName() + " tomorrow at " + appt.getTime() + ".";
            if (!notificationRepository.existsByUserIdAndMessage(userId, message)) {
                Notification notif = new Notification();
                notif.setUserId(userId);
                notif.setType("appointment");
                notif.setMessage(message);
                notificationRepository.save(notif);
            }
        }

        // 2. Prescription Refill Reminders (30 days after prescription)
        List<Prescription> prescriptions = prescriptionRepository.findByPatientId(userId);
        for (Prescription pres : prescriptions) {
            long daysBetween = ChronoUnit.DAYS.between(pres.getCreatedAt(), LocalDateTime.now());
            if (daysBetween >= 30) {
                String message = "Reminder: Your prescription from Dr. " + pres.getDoctor().getName() + " may need a refill soon.";
                if (!notificationRepository.existsByUserIdAndMessage(userId, message)) {
                    Notification notif = new Notification();
                    notif.setUserId(userId);
                    notif.setType("refill");
                    notif.setMessage(message);
                    notificationRepository.save(notif);
                }
            }
        }

        // 3. User-configured Prescription Reminders (Daily alarms)
        List<PrescriptionReminder> activeAlarms = prescriptionReminderRepository.findByPatientIdAndIsActive(userId, true);
        String todayDateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM"));
        for (PrescriptionReminder alarm : activeAlarms) {
            String message = "Medication Alarm (" + todayDateStr + "): It is time to take your " + alarm.getMedicineName() + " (" + alarm.getDosage() + ") scheduled for " + alarm.getTimeOfDay() + ".";
            if (!notificationRepository.existsByUserIdAndMessage(userId, message)) {
                Notification notif = new Notification();
                notif.setUserId(userId);
                notif.setType("refill");
                notif.setMessage(message);
                notificationRepository.save(notif);
            }
        }

        // 4. Health Checkup Reminders (6 months (180 days) after last appointment)

        List<Appointment> lastAppts = appointmentRepository.findByPatientIdAndStatusOrderByDateDesc(userId, "approved");
        if (!lastAppts.isEmpty()) {
            Appointment lastAppt = lastAppts.get(0);
            try {
                LocalDate apptDate = LocalDate.parse(lastAppt.getDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                long daysSince = ChronoUnit.DAYS.between(apptDate, LocalDate.now());
                if (daysSince >= 180) {
                    String message = "It's been 6 months since your last appointment. Consider booking a general health checkup.";
                    if (!notificationRepository.existsByUserIdAndMessage(userId, message)) {
                        Notification notif = new Notification();
                        notif.setUserId(userId);
                        notif.setType("checkup");
                        notif.setMessage(message);
                        notificationRepository.save(notif);
                    }
                }
            } catch (Exception e) {
                // Ignore parse exceptions
            }
        }
    }
}
