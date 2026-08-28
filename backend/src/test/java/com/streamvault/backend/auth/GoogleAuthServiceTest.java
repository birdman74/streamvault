package com.streamvault.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.streamvault.backend.auth.dto.AuthResponse;
import com.streamvault.backend.auth.dto.GoogleSignInRequest;
import com.streamvault.backend.auth.exception.GoogleAccountEmailCollisionException;
import com.streamvault.backend.auth.exception.GoogleSignInException;
import com.streamvault.backend.user.User;
import com.streamvault.backend.user.UserRepository;

/**
 * Contract lives in docs/specs/design/story-002-api-contracts.md. GoogleAuthService,
 * GoogleTokenVerifier, GoogleUserInfo, and the related exceptions/User factory do not exist yet;
 * this suite is expected to fail to compile until Dev implements against that contract.
 */
@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    private static final String ID_TOKEN = "google-issued-id-token";
    private static final String GOOGLE_ID = "google-subject-12345";
    private static final String EMAIL = "user@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthService(userRepository, jwtService, googleTokenVerifier);
    }

    @Test
    void should_createNewUserLinkedToGoogleId_when_noExistingAccountMatchesEmailOrGoogleId() {
        GoogleSignInRequest request = new GoogleSignInRequest(ID_TOKEN);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(GOOGLE_ID, EMAIL, true));
        when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

        googleAuthService.googleSignIn(request);

        verify(userRepository).save(any(User.class));
    }

    @Test
    void should_returnBearerTokenInAuthResponse_when_signInSucceeds() {
        GoogleSignInRequest request = new GoogleSignInRequest(ID_TOKEN);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(GOOGLE_ID, EMAIL, true));
        when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

        AuthResponse response = googleAuthService.googleSignIn(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void should_returnTokenForExistingGoogleLinkedUser_when_returningUserSignsIn() {
        GoogleSignInRequest request = new GoogleSignInRequest(ID_TOKEN);
        User existingUser = User.googleUser(EMAIL, GOOGLE_ID);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(GOOGLE_ID, EMAIL, true));
        when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.of(existingUser));
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token-for-returning-user");

        AuthResponse response = googleAuthService.googleSignIn(request);

        assertThat(response.token()).isEqualTo("jwt-token-for-returning-user");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void should_issueTokenBoundToTheMatchedUsersId_when_returningGoogleUserSignsInAgain() {
        GoogleSignInRequest request = new GoogleSignInRequest(ID_TOKEN);
        User existingUser = User.googleUser(EMAIL, GOOGLE_ID);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(GOOGLE_ID, EMAIL, true));
        when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.of(existingUser));
        when(jwtService.generateToken(existingUser.getId(), EMAIL)).thenReturn("jwt-token");

        googleAuthService.googleSignIn(request);

        verify(jwtService).generateToken(existingUser.getId(), EMAIL);
    }

    @Test
    void should_throwGoogleAccountEmailCollisionException_when_emailMatchesExistingLocalAccount() {
        GoogleSignInRequest request = new GoogleSignInRequest(ID_TOKEN);
        User existingLocalUser = new User(EMAIL, "hashed-password");
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(GOOGLE_ID, EMAIL, true));
        when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingLocalUser));

        assertThatThrownBy(() -> googleAuthService.googleSignIn(request))
                .isInstanceOf(GoogleAccountEmailCollisionException.class);
    }

    @Test
    void should_neverCallSaveOnUserRepository_when_emailCollisionWithLocalAccountOccurs() {
        GoogleSignInRequest request = new GoogleSignInRequest(ID_TOKEN);
        User existingLocalUser = new User(EMAIL, "hashed-password");
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(GOOGLE_ID, EMAIL, true));
        when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingLocalUser));

        assertThatThrownBy(() -> googleAuthService.googleSignIn(request))
                .isInstanceOf(GoogleAccountEmailCollisionException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void should_propagateGoogleSignInException_when_tokenVerificationFails() {
        GoogleSignInRequest request = new GoogleSignInRequest(ID_TOKEN);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenThrow(new GoogleSignInException());

        assertThatThrownBy(() -> googleAuthService.googleSignIn(request))
                .isInstanceOf(GoogleSignInException.class);
    }

    @Test
    void should_neverCallSaveOnUserRepository_when_tokenVerificationFails() {
        GoogleSignInRequest request = new GoogleSignInRequest(ID_TOKEN);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenThrow(new GoogleSignInException());

        assertThatThrownBy(() -> googleAuthService.googleSignIn(request))
                .isInstanceOf(GoogleSignInException.class);

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).findByGoogleId(any());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void should_throwGoogleSignInException_when_emailNotVerified() {
        GoogleSignInRequest request = new GoogleSignInRequest(ID_TOKEN);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(GOOGLE_ID, EMAIL, false));

        assertThatThrownBy(() -> googleAuthService.googleSignIn(request))
                .isInstanceOf(GoogleSignInException.class);
    }

    @Test
    void should_neverCallUserRepository_when_emailNotVerified() {
        GoogleSignInRequest request = new GoogleSignInRequest(ID_TOKEN);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(GOOGLE_ID, EMAIL, false));

        assertThatThrownBy(() -> googleAuthService.googleSignIn(request))
                .isInstanceOf(GoogleSignInException.class);

        verify(userRepository, never()).findByGoogleId(any());
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any(User.class));
    }
}
