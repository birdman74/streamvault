package com.streamvault.backend.settings;

import org.springframework.stereotype.Service;

import com.streamvault.backend.settings.dto.AccountSettingsResponse;
import com.streamvault.backend.settings.dto.UpdateAccountSettingsRequest;
import com.streamvault.backend.settings.exception.InvalidRatingTypeException;
import com.streamvault.backend.user.RatingType;
import com.streamvault.backend.user.User;
import com.streamvault.backend.user.UserRepository;

@Service
public class AccountSettingsService {

    private final UserRepository userRepository;

    public AccountSettingsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AccountSettingsResponse getSettings(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return new AccountSettingsResponse(user.getRatingType().name());
    }

    public AccountSettingsResponse updateRatingType(Long userId, UpdateAccountSettingsRequest request) {
        RatingType ratingType = parseRatingType(request.ratingType());

        User user = userRepository.findById(userId).orElseThrow();
        user.setRatingType(ratingType);
        userRepository.save(user);

        return new AccountSettingsResponse(user.getRatingType().name());
    }

    private RatingType parseRatingType(String value) {
        try {
            return RatingType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRatingTypeException(value);
        }
    }
}
