package dev.jpzconception.allodocteur.notification.application;

import dev.jpzconception.allodocteur.appointment.application.AppointmentAccess;
import dev.jpzconception.allodocteur.notification.domain.NotificationType;
import dev.jpzconception.allodocteur.notification.infrastructure.ReminderLog;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Rappelle patient et professionnel quand un rendez-vous confirmé approche ; {@link ReminderLog} garantit un seul rappel. */
@Component
public class AppointmentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderJob.class);

    private final AppointmentAccess appointments;
    private final ReminderLog reminders;
    private final NotificationService notifications;
    private final NotificationProperties props;
    private final Clock clock;

    public AppointmentReminderJob(AppointmentAccess appointments, ReminderLog reminders, NotificationService notifications,
                                  NotificationProperties props, Clock clock) {
        this.appointments = appointments;
        this.reminders = reminders;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void scheduled() {
        run();
    }

    /** Retourne le nombre de rendez-vous rappelés (utile aux tests). */
    @Transactional
    public int run() {
        Instant now = clock.instant();
        int sent = 0;
        for (var a : appointments.upcomingConfirmed(now, now.plus(Duration.ofMinutes(props.reminderMinutes())))) {
            if (reminders.markIfNew(a.id(), now)) {
                notifications.notify(a.patientId(), NotificationType.APPOINTMENT_REMINDER, "Rappel de rendez-vous",
                        "Votre rendez-vous commence bientôt.", "APPOINTMENT", a.id());
                notifications.notify(a.professionalId(), NotificationType.APPOINTMENT_REMINDER, "Rappel de rendez-vous",
                        "Un rendez-vous commence bientôt.", "APPOINTMENT", a.id());
                sent++;
            }
        }
        if (sent > 0) {
            log.info("{} rappel(s) de rendez-vous envoyé(s)", sent);
        }
        return sent;
    }
}
