package dev.jpzconception.allodocteur;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jpzconception.allodocteur.ApiClient.Session;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.mock.web.MockMultipartFile;

/** Données de test réutilisables : un professionnel approuvé, disponible tous les jours de 08:00 à 12:00. */
public class Fixtures {

    private final ApiClient api;
    private final String adminToken;
    private final int typeId;
    private final Map<String, Integer> typeIds = new HashMap<>();
    private final int specialtyId;
    private final int cityId;

    public Fixtures(ApiClient api) throws Exception {
        this.api = api;
        this.adminToken = api.login("admin@test.local", "AdminPassw0rd!").token();
        Session probe = api.registerAndLogin("PATIENT", "Ref");
        int type = 0;
        for (JsonNode t : api.body(api.get("/api/v1/reference/professional-types", probe.token()))) {
            typeIds.put(t.get("code").asText(), t.get("id").asInt());
            if (t.get("code").asText().equals("GENERAL_PRACTITIONER")) type = t.get("id").asInt();
        }
        this.typeId = type;
        this.specialtyId = api.body(api.get("/api/v1/reference/specialties", probe.token())).get(0).get("id").asInt();
        int city = 0;
        for (JsonNode c : api.body(api.get("/api/v1/reference/cities", probe.token()))) {
            if (c.get("name").asText().equals("Abidjan")) city = c.get("id").asInt();
        }
        this.cityId = city;
    }

    public String adminToken() {
        return adminToken;
    }

    public Session patient(String lastName) throws Exception {
        return api.registerAndLogin("PATIENT", lastName);
    }

    public Session approvedProfessional(String lastName) throws Exception {
        return approvedProfessional(lastName, true, true);
    }

    public int cityId() {
        return cityId;
    }

    /** Professionnel approuvé d'un autre type que médecin généraliste (ex. PHARMACIST). */
    public Session approvedProfessionalOfType(String lastName, String typeCode) throws Exception {
        Integer id = typeIds.get(typeCode);
        if (id == null) throw new IllegalStateException("Type de professionnel inconnu : " + typeCode);
        return approvedProfessional(lastName, true, true, id);
    }

    public Session approvedProfessional(String lastName, boolean teleconsultation, boolean homeVisit) throws Exception {
        return approvedProfessional(lastName, teleconsultation, homeVisit, typeId);
    }

    private Session approvedProfessional(String lastName, boolean teleconsultation, boolean homeVisit, int professionalTypeId)
            throws Exception {
        Session pro = api.registerAndLogin("PROFESSIONAL", lastName);
        var profile = Map.ofEntries(
                Map.entry("professionalTypeId", professionalTypeId),
                Map.entry("qualification", "Docteur en médecine"),
                Map.entry("licenseNumber", "FX-" + UUID.randomUUID()),
                Map.entry("yearsExperience", 5),
                Map.entry("cityId", cityId),
                Map.entry("consultationFee", 5000),
                Map.entry("homeVisitFee", 8000),
                Map.entry("offersTeleconsultation", teleconsultation),
                Map.entry("offersHomeVisit", homeVisit),
                Map.entry("specialtyIds", List.of(specialtyId)),
                Map.entry("coverageZones", List.of()));
        check(api.put("/api/v1/professionals/me", profile, pro.token()).getResponse().getStatus(), 200, "profil");

        List<Map<String, Object>> rules = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            rules.add(Map.of("dayOfWeek", day, "startTime", "08:00", "endTime", "12:00",
                    "teleconsultation", teleconsultation, "homeVisit", homeVisit));
        }
        check(api.put("/api/v1/professionals/me/availability/rules", Map.of("rules", rules), pro.token())
                .getResponse().getStatus(), 200, "disponibilités");

        var pdf = new MockMultipartFile("file", "ordre.pdf", "application/pdf", "%PDF-1.4 fixture".getBytes());
        check(api.perform(multipart("/api/v1/professionals/me/documents").file(pdf).param("type", "LICENSE"), pro.token())
                .getResponse().getStatus(), 201, "justificatif");
        check(api.post("/api/v1/admin/professionals/" + pro.userId() + "/approve", Map.of(), adminToken)
                .getResponse().getStatus(), 200, "approbation");
        return pro;
    }

    public record Booking(String id, Instant startAt) {}

    /** Réserve le créneau n° {@code slotIndex} (08:00, 08:30…) du surlendemain : toujours dans le futur. */
    public Booking book(Session patient, Session pro, String type, int slotIndex) throws Exception {
        return book(patient, pro, type, slotIndex, null);
    }

    /** Idem, pour une personne à charge ({@code dependentId} nul = le titulaire du compte). */
    public Booking book(Session patient, Session pro, String type, int slotIndex, String dependentId) throws Exception {
        LocalDate day = LocalDate.now(ZoneOffset.UTC).plusDays(2);
        JsonNode slot = api.body(api.get("/api/v1/professionals/" + pro.userId() + "/slots", patient.token(),
                "type", type, "from", day.toString())).get(slotIndex);
        Map<String, Object> req = new HashMap<>();
        req.put("professionalId", pro.userId());
        req.put("type", type);
        req.put("reason", "CONSULTATION");
        req.put("startAt", slot.get("startAt").asText());
        if (dependentId != null) req.put("dependentId", dependentId);
        var result = api.post("/api/v1/appointments", req, patient.token());
        check(result.getResponse().getStatus(), 201, "réservation");
        return new Booking(api.body(result).get("id").asText(), Instant.parse(slot.get("startAt").asText()));
    }

    private static void check(int actual, int expected, String step) {
        if (actual != expected) {
            throw new IllegalStateException("Fixture « " + step + " » : statut " + actual + " au lieu de " + expected);
        }
    }
}
