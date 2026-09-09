package com.streamvault.backend.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.streamvault.backend.settings.dto.AccountSettingsResponse;
import com.streamvault.backend.settings.dto.UpdateAccountSettingsRequest;
import com.streamvault.backend.settings.exception.InvalidRatingTypeException;
import com.streamvault.backend.user.RatingType;
import com.streamvault.backend.user.User;
import com.streamvault.backend.user.UserRepository;

/**
 * Contract lives in docs/specs/design/story-005-api-contracts.md. AccountSettingsService,
 * AccountSettingsResponse, UpdateAccountSettingsRequest, and InvalidRatingTypeException do not
 * exist yet; this test is expected to fail to compile until Dev implements them.
 */
@ExtendWith(MockitoExtension.class)
class AccountSettingsServiceTest {

    @Mock
    private UserRepository userRepository;

    private AccountSettingsService accountSettingsService;

    @BeforeEach
    void setUp() {
        accountSettingsService = new AccountSettingsService(userRepository);
    }

    @Test
    void should_returnCurrentRatingType_when_settingsAreRead() {
        User user = new User("user@example.com", "hashed-password");
        user.setRatingType(RatingType.THUMBS_UP_THUMBS_DOWN);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        AccountSettingsResponse response = accountSettingsService.getSettings(42L);

        assertThat(response.ratingType()).isEqualTo("THUMBS_UP_THUMBS_DOWN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOVE_LIKE_MEH_DISLIKE_HATE", "THUMBS_UP_THUMBS_DOWN", "HALF_STAR_OUT_OF_5"})
    void should_updateStoredRatingType_when_ratingTypeIsSetTo(String ratingType) {
        User user = new User("user@example.com", "hashed-password");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountSettingsResponse response =
                accountSettingsService.updateRatingType(42L, new UpdateAccountSettingsRequest(ratingType));

        assertThat(response.ratingType()).isEqualTo(ratingType);
        assertThat(user.getRatingType()).isEqualTo(RatingType.valueOf(ratingType));
        verify(userRepository).save(user);
    }

    @Test
    void should_persistRatingTypeChange_when_readAfterUpdate() {
        User user = new User("user@example.com", "hashed-password");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountSettingsService.updateRatingType(42L, new UpdateAccountSettingsRequest("HALF_STAR_OUT_OF_5"));
        AccountSettingsResponse readBack = accountSettingsService.getSettings(42L);

        assertThat(readBack.ratingType()).isEqualTo("HALF_STAR_OUT_OF_5");
    }

    @Test
    void should_onlyReadOrWriteTheAuthenticatedUsersRow_when_settingsAreAccessed() {
        User userA = new User("a@example.com", "hashed-password-a");
        User userB = new User("b@example.com", "hashed-password-b");
        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));
        lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(userB));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountSettingsService.updateRatingType(1L, new UpdateAccountSettingsRequest("THUMBS_UP_THUMBS_DOWN"));

        assertThat(userA.getRatingType()).isEqualTo(RatingType.THUMBS_UP_THUMBS_DOWN);
        assertThat(userB.getRatingType()).isEqualTo(RatingType.LOVE_LIKE_MEH_DISLIKE_HATE);
        verify(userRepository).save(userA);
        verify(userRepository, never()).save(userB);
        verify(userRepository, never()).findById(eq(2L));
    }

    @Test
    void should_throwInvalidRatingTypeException_when_ratingTypeValueIsUnsupported() {
        assertThatThrownBy(() -> accountSettingsService.updateRatingType(
                42L, new UpdateAccountSettingsRequest("FIVE_STARS")))
                .isInstanceOf(InvalidRatingTypeException.class);
    }

    @Test
    void should_notAccessUserRepositoryAtAll_when_ratingTypeValueIsUnsupported() {
        assertThatThrownBy(() -> accountSettingsService.updateRatingType(
                42L, new UpdateAccountSettingsRequest("FIVE_STARS")))
                .isInstanceOf(InvalidRatingTypeException.class);

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void should_throwInvalidRatingTypeException_when_ratingTypeValueDiffersOnlyInCase() {
        assertThatThrownBy(() -> accountSettingsService.updateRatingType(
                42L, new UpdateAccountSettingsRequest("thumbs_up_thumbs_down")))
                .isInstanceOf(InvalidRatingTypeException.class);

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any(User.class));
    }
}
