package dev.jpzconception.allodocteur.professional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.ApiClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Données et requêtes communes aux tests de la candidature publique (aucune authentification). */
final class PublicApplicationSupport {

    final ApiClient api;
    final ObjectMapper json;
    final int typeId;
    final int specialtyId;
    final int cityId;

    PublicApplicationSupport(ApiClient api, ObjectMapper json) throws Exception {
        this.api = api;
        this.json = json;
        JsonNode types = api.body(api.get("/api/v1/reference/professional-types", null));
        int id = 0;
        for (JsonNode t : types) if (t.get("code").asText().equals("GENERAL_PRACTITIONER")) id = t.get("id").asInt();
        this.typeId = id;
        this.specialtyId = api.body(api.get("/api/v1/reference/specialties", null)).get(0).get("id").asInt();
        int city = 0;
        for (JsonNode c : api.body(api.get("/api/v1/reference/cities", null))) {
            if (c.get("name").asText().equals("Abidjan")) city = c.get("id").asInt();
        }
        this.cityId = city;
    }

    static String email() {
        return "pro-" + UUID.randomUUID() + "@test.local";
    }

    Map<String, Object> application(String email, String license) {
        return Map.of(
                "account", Map.of("email", email, "password", ApiClient.PASSWORD, "firstName", "Awa",
                        "lastName", "Kone", "phone", "+22507" + (10000000 + (int) (Math.random() * 89999999))),
                "profile", Map.ofEntries(
                        Map.entry("professionalTypeId", typeId),
                        Map.entry("qualification", "Docteur en médecine"),
                        Map.entry("licenseNumber", license),
                        Map.entry("yearsExperience", 5),
                        Map.entry("cityId", cityId),
                        Map.entry("consultationFee", 5000),
                        Map.entry("offersTeleconsultation", true),
                        Map.entry("offersHomeVisit", false),
                        Map.entry("specialtyIds", List.of(specialtyId))),
                "acceptTerms", true);
    }

    MockMultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", "%PDF-1.4 test".getBytes());
    }

    MvcResult submit(Map<String, Object> application, List<String> types, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/public/professional-applications");
        req.file(new MockMultipartFile("application", "", MediaType.APPLICATION_JSON_VALUE,
                json.writeValueAsBytes(application)));
        for (MockMultipartFile f : files) req.file(f);
        for (String t : types) req.param("types", t);
        return api.perform(req, null);
    }
}
