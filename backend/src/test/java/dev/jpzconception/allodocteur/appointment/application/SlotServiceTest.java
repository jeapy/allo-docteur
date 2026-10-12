package dev.jpzconception.allodocteur.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jpzconception.allodocteur.appointment.application.SlotService.Slot;
import dev.jpzconception.allodocteur.appointment.domain.Appointment;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentType;
import dev.jpzconception.allodocteur.appointment.infrastructure.AppointmentRepository;
import dev.jpzconception.allodocteur.professional.domain.AvailabilityException;
import dev.jpzconception.allodocteur.professional.domain.AvailabilityRule;
import dev.jpzconception.allodocteur.professional.domain.Professional;
import dev.jpzconception.allodocteur.professional.infrastructure.AvailabilityExceptionRepository;
import dev.jpzconception.allodocteur.professional.infrastructure.AvailabilityRuleRepository;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlotServiceTest {

    static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    AvailabilityRuleRepository rules = mock(AvailabilityRuleRepository.class);
    AvailabilityExceptionRepository exceptions = mock(AvailabilityExceptionRepository.class);
    AppointmentRepository appointments = mock(AppointmentRepository.class);
    Professional pro = new Professional();
    UUID proId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        pro.setId(proId);
        pro.setOffersTeleconsultation(true);
        pro.setOffersHomeVisit(true);
        when(exceptions.findByProfessionalIdAndDateBetween(eq(proId), any(), any())).thenReturn(List.of());
        when(appointments.findOverlapping(eq(proId), any(), any(), any())).thenReturn(List.of());
    }

    private SlotService service(String now) {
        Clock clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC);
        var props = new AppointmentProperties("Africa/Abidjan", 30, 60, 60, 60, 120, 15, 15);
        return new SlotService(mock(ProfessionalRepository.class), rules, exceptions, appointments, props, clock);
    }

    private SlotService earlyMorning() {
        return service("2030-01-07T06:00:00Z");
    }

    private AvailabilityRule rule(int day, String from, String to, boolean tele, boolean home) {
        AvailabilityRule r = new AvailabilityRule();
        r.setProfessionalId(proId);
        r.setDayOfWeek(day);
        r.setStartTime(LocalTime.parse(from));
        r.setEndTime(LocalTime.parse(to));
        r.setTeleconsultation(tele);
        r.setHomeVisit(home);
        return r;
    }

    private void rules(AvailabilityRule... list) {
        when(rules.findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(proId)).thenReturn(List.of(list));
    }

    private List<String> starts(List<Slot> slots) {
        return slots.stream().map(s -> s.startAt().toString().substring(11, 16)).toList();
    }

    @Test
    void generatesSlotsAlignedOnTheRuleStart() {
        rules(rule(1, "08:00", "10:00", true, false));
        var slots = earlyMorning().compute(pro, MONDAY, MONDAY, AppointmentType.TELECONSULTATION_VIDEO);
        assertThat(starts(slots)).containsExactly("08:00", "08:30", "09:00", "09:30");
        assertThat(slots.get(0).endAt()).isEqualTo(Instant.parse("2030-01-07T08:30:00Z"));
    }

    @Test
    void lastPartialSlotIsDropped() {
        rules(rule(1, "08:00", "09:45", true, false));
        var slots = earlyMorning().compute(pro, MONDAY, MONDAY, AppointmentType.CHAT);
        assertThat(starts(slots)).containsExactly("08:00", "08:30", "09:00");
    }

    @Test
    void minimumNoticeHidesSlotsTooClose() {
        rules(rule(1, "08:00", "10:00", true, false));
        var slots = service("2030-01-07T08:20:00Z")
                .compute(pro, MONDAY, MONDAY, AppointmentType.TELECONSULTATION_AUDIO);
        assertThat(starts(slots)).containsExactly("09:30"); // préavis d'une heure : à partir de 09:20
    }

    @Test
    void partialAbsenceRemovesOnlyOverlappingSlots() {
        rules(rule(1, "08:00", "10:00", true, false));
        AvailabilityException absence = new AvailabilityException();
        absence.setProfessionalId(proId);
        absence.setDate(MONDAY);
        absence.setStartTime(LocalTime.of(8, 30));
        absence.setEndTime(LocalTime.of(9, 30));
        when(exceptions.findByProfessionalIdAndDateBetween(eq(proId), any(), any())).thenReturn(List.of(absence));
        var slots = earlyMorning().compute(pro, MONDAY, MONDAY, AppointmentType.CHAT);
        assertThat(starts(slots)).containsExactly("08:00", "09:30"); // bornes adjacentes conservées
    }

    @Test
    void fullDayAbsenceRemovesEverything() {
        rules(rule(1, "08:00", "10:00", true, false));
        AvailabilityException absence = new AvailabilityException();
        absence.setProfessionalId(proId);
        absence.setDate(MONDAY);
        when(exceptions.findByProfessionalIdAndDateBetween(eq(proId), any(), any())).thenReturn(List.of(absence));
        assertThat(earlyMorning().compute(pro, MONDAY, MONDAY, AppointmentType.CHAT)).isEmpty();
    }

    @Test
    void existingAppointmentBlocksItsSlot() {
        rules(rule(1, "08:00", "10:00", true, false));
        Appointment busy = new Appointment();
        busy.setStartAt(Instant.parse("2030-01-07T09:00:00Z"));
        busy.setEndAt(Instant.parse("2030-01-07T09:30:00Z"));
        when(appointments.findOverlapping(eq(proId), any(), any(), any())).thenReturn(List.of(busy));
        var slots = earlyMorning().compute(pro, MONDAY, MONDAY, AppointmentType.CHAT);
        assertThat(starts(slots)).containsExactly("08:00", "08:30", "09:30");
    }

    @Test
    void homeVisitsUseTheirOwnDurationAndRules() {
        rules(rule(1, "08:00", "10:00", false, true), rule(1, "14:00", "16:00", true, false));
        var home = earlyMorning().compute(pro, MONDAY, MONDAY, AppointmentType.HOME_VISIT);
        assertThat(starts(home)).containsExactly("08:00", "09:00"); // pas d'une heure, plage de l'après-midi exclue
        assertThat(home.get(0).endAt()).isEqualTo(Instant.parse("2030-01-07T09:00:00Z"));
    }

    @Test
    void onlyDaysCoveredByARuleProduceSlots() {
        rules(rule(1, "08:00", "09:00", true, false));
        var slots = earlyMorning().compute(pro, MONDAY, MONDAY.plusDays(1), AppointmentType.CHAT);
        assertThat(starts(slots)).containsExactly("08:00", "08:30"); // le mardi n'a pas de plage
    }

    @Test
    void slotsBeyondTheBookingHorizonAreHidden() {
        rules(rule(1, "08:00", "09:00", true, false));
        // horizon de 60 jours : le lundi 11 mars 2030 est à plus de 60 jours du 7 janvier
        var slots = earlyMorning().compute(pro, LocalDate.of(2030, 3, 11), LocalDate.of(2030, 3, 11), AppointmentType.CHAT);
        assertThat(slots).isEmpty();
    }

    @Test
    void refusesAModeTheProfessionalDoesNotOffer() {
        pro.setOffersHomeVisit(false);
        rules(rule(1, "08:00", "10:00", true, true));
        assertThatThrownBy(() -> earlyMorning().compute(pro, MONDAY, MONDAY, AppointmentType.HOME_VISIT))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("MODE_NOT_OFFERED");
    }

    @Test
    void slotsAreInChronologicalOrderAcrossRules() {
        rules(rule(1, "14:00", "15:00", true, false), rule(1, "08:00", "09:00", true, false));
        var slots = earlyMorning().compute(pro, MONDAY, MONDAY, AppointmentType.CHAT);
        assertThat(starts(slots)).containsExactly("08:00", "08:30", "14:00", "14:30");
    }
}
