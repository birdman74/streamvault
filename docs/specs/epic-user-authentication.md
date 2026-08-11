# Epic: User Authentication

## Goal
Enable multiple users to securely register and access StreamVault, each with their own private streaming library, using either email/password or Google sign-in. Sessions are stateless (JWT-based) to keep the backend simple and cheap to run on the existing t3.micro infrastructure. Every user has a single role and can only ever see and manage their own data — there is no shared or admin-level access in this epic.

## Stories
- STORY-001: Email/Password Registration and Login
- STORY-002: Google OAuth2 Sign-In

## Out of Scope (see docs/specs/backlog.md)
- Password reset and email verification
- Account settings (change password, delete account, profile updates)
