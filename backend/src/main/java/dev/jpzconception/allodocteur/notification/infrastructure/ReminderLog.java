package dev.jpzconception.allodocteur.notification.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Journal des rappels envoyés (table appointment_reminders). */
@Component
public class ReminderLog {

    @PersistenceContext
    private EntityManager em;

    /** @return true si ce rendez-vous n'avait pas encore été rappelé (et le marque comme rappelé). */
    public boolean markIfNew(UUID appointmentId, Instant now) {
        int n = em.createNativeQuery("""
                INSERT INTO appointment_reminders (appointment_id, sent_at) VALUES (:id, :now)
                ON CONFLICT DO NOTHING""")
                .setParameter("id", appointmentId)
                .setParameter("now", now)
                .executeUpdate();
        return n == 1;
    }
}
