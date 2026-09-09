package com.streamvault.backend.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamvault.backend.auth.AuthenticatedUser;
import com.streamvault.backend.settings.dto.AccountSettingsResponse;
import com.streamvault.backend.settings.dto.UpdateAccountSettingsRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/account/settings")
public class AccountSettingsController {

    private final AccountSettingsService accountSettingsService;

    public AccountSettingsController(AccountSettingsService accountSettingsService) {
        this.accountSettingsService = accountSettingsService;
    }

    @GetMapping
    public ResponseEntity<AccountSettingsResponse> getSettings(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(accountSettingsService.getSettings(principal.userId()));
    }

    @PatchMapping
    public ResponseEntity<AccountSettingsResponse> updateSettings(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateAccountSettingsRequest request) {
        return ResponseEntity.ok(accountSettingsService.updateRatingType(principal.userId(), request));
    }
}
