# STORY-004: GitHub PR Automation for Dev and Test Personas

## As a...
Dev or Test persona operating inside my Docker container

## I want to...
authenticate to GitHub with a scoped token and open pull requests or post comments programmatically

## So that...
implementation and verification work can move from a local commit to a reviewable pull request without Brian creating the PR or relaying my summary on my behalf

## Acceptance Criteria
- [ ] A fine-grained GitHub personal access token, scoped to only the `streamvault` repository, is provisioned and referenced via `.env` (never hardcoded or committed)
- [ ] The token's permissions are limited to pull-request read/write and issue-comment write on this repo, nothing broader
- [ ] The Dev persona container can authenticate to GitHub using the token and open a pull request from its current feature branch
- [ ] The Test persona container can authenticate to GitHub using the same token and post its Test Run Summary as a comment on the relevant open pull request
- [ ] Neither persona's tooling includes any command or credential capable of merging a pull request to main; that remains exclusively a Brian action outside the personas' containers
- [ ] If the GitHub token is missing, invalid, or fails authentication, the persona fails with a clear, actionable error rather than silently skipping the PR or comment step

## Notes
- Authentication mechanism is a scoped personal access token (`gh` CLI or GitHub REST API), not a GitHub App, per Brian's decision to keep this simple for a personal project.
- This is separate from the existing shared GitHub deploy key, which only covers `git push`/`pull` over SSH and cannot call the GitHub API to open PRs or post comments.
- Builds on STORY-003 but is independently testable and deliverable — PR/comment automation does not depend on the build tooling working.

## Out of Scope
- GitHub App-based authentication (see `docs/specs/backlog.md` framing above, decided against)
- Merge capability of any kind, for either persona
- Any other repo automation not listed above (issue creation, releases, branch protection changes, etc.)
