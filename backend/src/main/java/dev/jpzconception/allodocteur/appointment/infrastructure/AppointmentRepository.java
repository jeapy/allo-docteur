package dev.jpzconception.allodocteur.appointment.infrastructure;

import dev.jpzconception.allodocteur.appointment.domain.Appointment;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    @Query("""
            select a from Appointment a
            where a.professionalId = :pid and a.status in :statuses
              and a.startAt < :to and a.endAt > :from
            """)
    List<Appointment> findOverlapping(@Param("pid") UUID professionalId,
                                      @Param("statuses") Collection<AppointmentStatus> statuses,
                                      @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.patientId = :pid and a.dependentId is null and a.status in :statuses
              and a.startAt < :to and a.endAt > :from
            """)
    boolean existsOverlapForSelf(@Param("pid") UUID patientId,
                                 @Param("statuses") Collection<AppointmentStatus> statuses,
                                 @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.dependentId = :did and a.status in :statuses
              and a.startAt < :to and a.endAt > :from
            """)
    boolean existsOverlapForDependent(@Param("did") UUID dependentId,
                                      @Param("statuses") Collection<AppointmentStatus> statuses,
                                      @Param("from") Instant from, @Param("to") Instant to);
}
