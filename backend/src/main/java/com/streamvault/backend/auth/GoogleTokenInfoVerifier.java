package com.streamvault.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.streamvault.backend.auth.exception.GoogleSignInException;

/**
 * Verifies Google ID tokens against Google's tokeninfo endpoint. Google performs signature and
 * expiry validation server-side; this class additionally checks the audience claim against this
 * app's configured OAuth client ID so a token issued for a different Google client is rejected.
 */
@Component
public class GoogleTokenInfoVerifier implements GoogleTokenVerifier {

    private final RestClient restClient;
    private final String expectedAudience;

    public GoogleTokenInfoVerifier(
            RestClient.Builder restClientBuilder,
            @Value("${app.google.client-id}") String expectedAudience
    ) {
        this.restClient = restClientBuilder.baseUrl("https://oauth2.googleapis.com").build();
        this.expectedAudience = expectedAudience;
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        TokenInfoResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("id_token", idToken).build())
                    .retrieve()
                    .body(TokenInfoResponse.class);
        } catch (RestClientException ex) {
            throw new GoogleSignInException();
        }

        if (response == null || response.sub() == null || response.email() == null
                || !expectedAudience.equals(response.aud())) {
            throw new GoogleSignInException();
        }

        return new GoogleUserInfo(response.sub(), response.email(), Boolean.parseBoolean(response.emailVerified()));
    }

    private record TokenInfoResponse(
            String sub,
            String email,
            @JsonProperty("email_verified") String emailVerified,
            String aud
    ) {
    }
}
