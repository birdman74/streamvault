package com.streamvault.backend.auth;

import org.springframework.stereotype.Service;

import com.streamvault.backend.auth.dto.AuthResponse;
import com.streamvault.backend.auth.dto.GoogleSignInRequest;
import com.streamvault.backend.auth.exception.GoogleAccountEmailCollisionException;
import com.streamvault.backend.auth.exception.GoogleSignInException;
import com.streamvault.backend.user.User;
import com.streamvault.backend.user.UserRepository;

@Service
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public GoogleAuthService(UserRepository userRepository, JwtService jwtService,
            GoogleTokenVerifier googleTokenVerifier) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    public AuthResponse googleSignIn(GoogleSignInRequest request) {
        GoogleUserInfo googleUserInfo = googleTokenVerifier.verify(request.idToken());

        if (!googleUserInfo.emailVerified()) {
            throw new GoogleSignInException();
        }

        User user = userRepository.findByGoogleId(googleUserInfo.googleId())
                .orElseGet(() -> resolveByEmail(googleUserInfo));

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token);
    }

    private User resolveByEmail(GoogleUserInfo googleUserInfo) {
        return userRepository.findByEmail(googleUserInfo.email())
                .map(existing -> rejectLocalAccountCollision(existing))
                .orElseGet(() -> userRepository.save(User.googleUser(googleUserInfo.email(), googleUserInfo.googleId())));
    }

    private User rejectLocalAccountCollision(User existing) {
        if (existing.getPasswordHash() != null) {
            throw new GoogleAccountEmailCollisionException();
        }
        return existing;
    }
}
