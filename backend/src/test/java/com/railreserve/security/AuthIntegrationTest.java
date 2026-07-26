package com.railreserve.security;

import com.railreserve.AbstractIntegrationTest;
import com.railreserve.security.web.dto.LoginRequest;
import com.railreserve.security.web.dto.RefreshRequest;
import com.railreserve.security.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real authentication flow end to end through the security filter chain:
 * register, login (which mints genuine HS256 tokens), refresh, and calling a protected endpoint
 * with a real {@code Authorization: Bearer} header decoded by the Nimbus resource server.
 */
@Transactional
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest(email, "password123", "Test User", "9990000000"))))
                .andReturn();
    }

    private JsonNode login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result);
    }

    @Test
    void registerReturns201WithUserDetails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest("newuser@example.com", "password123", "New User", "9990000000"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void registeringADuplicateEmailReturns409() throws Exception {
        register("dupe@example.com").getResponse();
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest("dupe@example.com", "password123", "Again", "9990000000"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() throws Exception {
        register("login@example.com");

        JsonNode data = login("login@example.com");

        assertThat(data.get("accessToken").asString()).isNotBlank();
        assertThat(data.get("refreshToken").asString()).isNotBlank();
        assertThat(data.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(data.get("expiresInSeconds").asLong()).isPositive();
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        register("wrongpass@example.com");
        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest("wrongpass@example.com", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshExchangesRefreshTokenForNewTokens() throws Exception {
        register("refresh@example.com");
        String refreshToken = login("refresh@example.com").get("refreshToken").asString();

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString());
    }

    @Test
    void anAccessTokenCannotBeUsedToRefresh() throws Exception {
        register("mixup@example.com");
        String accessToken = login("mixup@example.com").get("accessToken").asString();

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(accessToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aRealBearerTokenGrantsAccessToAProtectedEndpoint() throws Exception {
        register("bearer@example.com");
        String accessToken = login("bearer@example.com").get("accessToken").asString();

        mockMvc.perform(get("/api/v1/bookings").header(AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void aRefreshTokenIsRejectedAsAnAccessToken() throws Exception {
        register("refreshaccess@example.com");
        String refreshToken = login("refreshaccess@example.com").get("refreshToken").asString();

        mockMvc.perform(get("/api/v1/bookings").header(AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aProtectedEndpointWithoutATokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }
}
