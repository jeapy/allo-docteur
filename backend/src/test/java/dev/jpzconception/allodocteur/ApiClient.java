package dev.jpzconception.allodocteur;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Petit client de test autour de MockMvc (JSON, jeton Bearer, helpers d'inscription). */
public class ApiClient {

    public static final String PASSWORD = "Sup3rSecretPass!";

    private final MockMvc mvc;
    private final ObjectMapper json;

    public ApiClient(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public record Session(String email, String userId, String token) {}

    public MvcResult get(String url, String token, String... queryPairs) throws Exception {
        MockHttpServletRequestBuilder req = get(url);
        for (int i = 0; i + 1 < queryPairs.length; i += 2) req.param(queryPairs[i], queryPairs[i + 1]);
        return mvc.perform(auth(req, token)).andReturn();
    }

    public MvcResult post(String url, Object body, String token) throws Exception {
        return mvc.perform(auth(post(url), token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn();
    }

    public MvcResult put(String url, Object body, String token) throws Exception {
        return mvc.perform(auth(put(url), token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn();
    }

    public MvcResult delete(String url, String token) throws Exception {
        return mvc.perform(auth(delete(url), token)).andReturn();
    }

    public MvcResult perform(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(auth(req, token)).andReturn();
    }

    public JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    public Session registerAndLogin(String role, String lastName) throws Exception {
        String email = "u-" + UUID.randomUUID() + "@test.local";
        MvcResult reg = post("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD,
                "firstName", "Test", "lastName", lastName, "role", role), null);
        if (reg.getResponse().getStatus() != 201) throw new IllegalStateException(reg.getResponse().getContentAsString());
        return login(email, PASSWORD);
    }

    public Session login(String email, String password) throws Exception {
        JsonNode b = body(post("/api/v1/auth/login", Map.of("email", email, "password", password), null));
        return new Session(email, b.get("user").get("id").asText(), b.get("accessToken").asText());
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, String token) {
        return token == null ? req : req.header("Authorization", "Bearer " + token);
    }
}
