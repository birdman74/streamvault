package com.streamvault.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.streamvault.backend.auth.dto.AuthResponse;
import com.streamvault.backend.auth.dto.LoginRequest;
import com.streamvault.backend.auth.dto.RegisterRequest;
import com.streamvault.backend.auth.dto.RegisterResponse;
import com.streamvault.backend.auth.exception.EmailAlreadyRegisteredException;
import com.streamvault.backend.auth.exception.InvalidCredentialsException;
import com.streamvault.backend.user.User;
import com.streamvault.backend.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerCreatesUserWithEncodedPassword() {
        RegisterRequest request = new RegisterRequest("user@example.com", "Password1");
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterResponse response = authService.register(request);

        assertThat(response.email()).isEqualTo("user@example.com");
        verify(passwordEncoder).encode("Password1");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("user@example.com", "Password1");
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        User user = new User("user@example.com", "hashed-password");
        ReflectionTestUtils.setField(user, "id", 42L);
        LoginRequest request = new LoginRequest("user@example.com", "Password1");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(42L, "user@example.com")).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(jwtService).generateToken(42L, "user@example.com");
    }

    @Test
    void loginRejectsUnknownEmailWithGenericError() {
        LoginRequest request = new LoginRequest("nobody@example.com", "Password1");
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsWrongPasswordWithGenericError() {
        User user = new User("user@example.com", "hashed-password");
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
