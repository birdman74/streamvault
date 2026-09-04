# STORY-019: Bypass Removal Confirmation Preference

## Prerequisites
- story-011

## As a...
signed-in StreamVault user

## I want to...
turn off the confirmation prompt that appears when I remove items from my library

## So that...
I can clean up my library quickly once I am comfortable with how removal works

## Acceptance Criteria
- [ ] AC-1: By default every account has removal confirmation enabled, matching STORY-011 AC-8
- [ ] AC-2: A signed-in user can set a per-account preference that bypasses the removal confirmation step
- [ ] AC-3: When the bypass preference is on, a remove request from that user completes without a separate confirmation step; when it is off, STORY-011's confirmation step applies
- [ ] AC-4: The preference is per user and persists across sessions; one user's setting never affects another user's removal behavior
- [ ] AC-5: The preference affects only removal confirmation; it does not change what removal does, who may remove, or any other confirmation in the app
- [ ] AC-6: Turning the preference back off restores the confirmation step on the next removal

## Notes
- This is the third per-account setting carved out ahead of a full Account Settings epic, after STORY-005 (rating type) and STORY-018 (automatic series refresh). When that epic is specced these preferences fold into it.
- The confirmation step itself is defined by STORY-011. This story only toggles whether that step is required.

## Out of Scope
- Any confirmation prompt other than library-item removal
- A blanket "disable every confirmation in the app" switch
- Undo or restore after removal
