package com.streamvault.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.streamvault.backend.auth.exception.GoogleSignInException;

/**
 * Unit tests for the real GoogleTokenVerifier implementation, at the seam below the interface
 * boundary that GoogleAuthServiceTest mocks. Per story-002-agreed.md, this covers malformed
 * tokens, network failures, and non-2xx responses from Google, plus the audience check that
 * guards against accepting an ID token issued for a different Google client.
 */
class GoogleTokenInfoVerifierTest {

    private static final String CLIENT_ID = "expected-client-id.apps.googleusercontent.com";

    private MockRestServiceServer server;
    private GoogleTokenInfoVerifier verifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new GoogleTokenInfoVerifier(builder, CLIENT_ID);
    }

    @Test
    void returnsGoogleUserInfo_when_tokenIsValidAndAudienceMatches() {
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "sub": "google-subject-12345",
                          "email": "user@example.com",
                          "email_verified": "true",
                          "aud": "expected-client-id.apps.googleusercontent.com"
                        }
                        """, MediaType.APPLICATION_JSON));

        GoogleUserInfo result = verifier.verify("a-valid-id-token");

        assertThat(result.googleId()).isEqualTo("google-subject-12345");
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.emailVerified()).isTrue();
    }

    @Test
    void throwsGoogleSignInException_when_audienceDoesNotMatchConfiguredClientId() {
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "sub": "google-subject-12345",
                          "email": "user@example.com",
                          "email_verified": "true",
                          "aud": "someone-elses-client-id.apps.googleusercontent.com"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify("a-token-issued-for-another-app"))
                .isInstanceOf(GoogleSignInException.class);
    }

    @Test
    void throwsGoogleSignInException_when_googleReturnsNon2xxForMalformedToken() {
        server.expect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error_description\":\"Invalid Value\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify("a-malformed-token"))
                .isInstanceOf(GoogleSignInException.class);
    }

    @Test
    void throwsGoogleSignInException_when_networkCallFails() {
        server.expect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> verifier.verify("a-token"))
                .isInstanceOf(GoogleSignInException.class);
    }
}
