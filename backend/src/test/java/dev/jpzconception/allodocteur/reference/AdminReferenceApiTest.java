package dev.jpzconception.allodocteur.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Référentiels gérés par l'administration : doublons refusés, désactivation sans suppression, niveau SUPER requis. */
class AdminReferenceApiTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;

    ApiClient api;
    Session admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        admin = api.login("admin@test.local", "AdminPassw0rd!");
    }

    private int status(org.springframework.test.web.servlet.MvcResult r) {
        return r.getResponse().getStatus();
    }

    private boolean publicCityNames(String name) throws Exception {
        JsonNode cities = api.body(api.get("/api/v1/reference/cities", admin.token()));
        for (JsonNode c : cities) if (c.get("name").asText().equals(name)) return true;
        return false;
    }

    @Test
    void cityAndDistrictLifecycle() throws Exception {
        String name = "Ville-" + UUID.randomUUID().toString().substring(0, 8);
        var created = api.post("/api/v1/admin/reference/cities", Map.of("name", "  " + name + "  "), admin.token());
        assertThat(status(created)).isEqualTo(201);
        JsonNode city = api.body(created);
        assertThat(city.get("name").asText()).isEqualTo(name); // espaces retirés
        int cityId = city.get("id").asInt();

        // doublon, sans tenir compte de la casse
        assertThat(status(api.post("/api/v1/admin/reference/cities", Map.of("name", name.toUpperCase()), admin.token())))
                .isEqualTo(409);
        assertThat(status(api.post("/api/v1/admin/reference/cities", Map.of("name", "   "), admin.token())))
                .isEqualTo(400);

        var withDistrict = api.post("/api/v1/admin/reference/cities/" + cityId + "/districts", Map.of("name", "Centre"), admin.token());
        assertThat(status(withDistrict)).isEqualTo(201);
        JsonNode districts = api.body(withDistrict).get("districts");
        assertThat(districts).hasSize(1);
        int districtId = districts.get(0).get("id").asInt();
        assertThat(status(api.post("/api/v1/admin/reference/cities/" + cityId + "/districts", Map.of("name", "centre"), admin.token())))
                .isEqualTo(409);

        assertThat(publicCityNames(name)).isTrue();
        // désactivée : disparaît de la liste publique mais reste dans celle de l'administration
        assertThat(status(api.post("/api/v1/admin/reference/cities/" + cityId + "/deactivate", Map.of(), admin.token())))
                .isEqualTo(200);
        assertThat(publicCityNames(name)).isFalse();
        JsonNode all = api.body(api.get("/api/v1/admin/reference/cities", admin.token()));
        boolean listed = false;
        for (JsonNode c : all) if (c.get("id").asInt() == cityId) listed = !c.get("active").asBoolean();
        assertThat(listed).isTrue();

        assertThat(status(api.post("/api/v1/admin/reference/cities/" + cityId + "/activate", Map.of(), admin.token())))
                .isEqualTo(200);
        var renamed = api.put("/api/v1/admin/reference/districts/" + districtId, Map.of("name", "Plateau"), admin.token());
        assertThat(api.body(renamed).get("districts").get(0).get("name").asText()).isEqualTo("Plateau");
    }

    @Test
    void specialtiesAndProfessionalTypes() throws Exception {
        String code = "TEST_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase().replace("-", "");
        var created = api.post("/api/v1/admin/reference/specialties", Map.of("code", code.toLowerCase(), "name", "Test spé"), admin.token());
        assertThat(status(created)).isEqualTo(201);
        JsonNode s = api.body(created);
        assertThat(s.get("code").asText()).isEqualTo(code); // normalisé en majuscules
        assertThat(status(api.post("/api/v1/admin/reference/specialties", Map.of("code", code, "name", "Autre"), admin.token())))
                .isEqualTo(409);
        assertThat(status(api.post("/api/v1/admin/reference/specialties", Map.of("code", "bad code!", "name", "X"), admin.token())))
                .isEqualTo(400);

        int id = s.get("id").asInt();
        assertThat(status(api.post("/api/v1/admin/reference/specialties/" + id + "/deactivate", Map.of(), admin.token()))).isEqualTo(200);
        String publicSpecialties = api.get("/api/v1/reference/specialties", admin.token()).getResponse().getContentAsString();
        assertThat(publicSpecialties).doesNotContain(code);

        var type = api.post("/api/v1/admin/reference/professional-types", Map.of("code", code, "name", "Type test"), admin.token());
        assertThat(status(type)).isEqualTo(201);
        int typeId = api.body(type).get("id").asInt();
        var renamed = api.put("/api/v1/admin/reference/professional-types/" + typeId, Map.of("name", "Type renommé"), admin.token());
        assertThat(api.body(renamed).get("label").asText()).isEqualTo("Type renommé");
        assertThat(api.body(renamed).get("code").asText()).isEqualTo(code); // le code ne change jamais
    }

    @Test
    void onlySuperAdminsCanManageReferenceData() throws Exception {
        Session patient = api.registerAndLogin("PATIENT", "Intrus");
        assertThat(status(api.get("/api/v1/admin/reference/cities", patient.token()))).isEqualTo(403);
        assertThat(status(api.post("/api/v1/admin/reference/cities", Map.of("name", "Piratée"), patient.token()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/admin/reference/cities", null))).isEqualTo(401);
    }
}
