# STORY-014: Notes and Review per Library Item

## Prerequisites
- story-007
- story-008

## As a...
signed-in StreamVault user

## I want to...
write a free-text note or short review on a movie or series in my library

## So that...
I can remember what I thought about it

## Acceptance Criteria
- [ ] AC-1: A signed-in user can add or edit a single free-text note on a movie in their own library
- [ ] AC-2: A signed-in user can add or edit a single free-text note on a series in their own library
- [ ] AC-3: A note persists and is returned on subsequent reads of that library item
- [ ] AC-4: A user can clear a note, returning the item to having no note
- [ ] AC-5: A note has a defined maximum length; input over that length is rejected with a clear, user-facing error and the stored note is unchanged
- [ ] AC-6: Notes are private to the owning user and per item; one user's note is never visible on another user's library or on another item
- [ ] AC-7: Note text is stored and returned as written and is not interpreted as markup when displayed

## Notes
- One note per library item for this epic. Per-season and per-episode notes are deferred to the backlog.
- "Note" and "review" are the same field for this epic; there is no separate structured review.
- The maximum length is a contract decision for Test; the business requirement is only that a sane cap exists.

## Out of Scope
- Per-season or per-episode notes (backlog)
- Multiple notes, threaded notes, or a dated note history per item
- Sharing or publishing notes
- Rich text or attachments
