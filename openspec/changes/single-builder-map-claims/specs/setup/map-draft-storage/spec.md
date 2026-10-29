# Spec Delta

## Purpose

A save writes the whole draft, so a save from a copy that is out of date replaces the newer draft without a message. The
setup server refuses such a save and keeps the stored copy, whichever process or outside edit changed it.

## ADDED Requirements

### Requirement: A save refuses to replace a draft that changed on disk
**Priority:** MoSCoW Must

WHEN a session saves a draft, THE SYSTEM SHALL compare the stored copy with the draft the session last loaded or saved, by
their canonical JSON, and IF the stored copy differs or is missing, THEN THE SYSTEM SHALL refuse the save with a
draft-changed failure and SHALL leave the stored copy unchanged.

#### Scenario: Second setup server changed the draft
- **WHEN** one setup server saves a changed `skyfortress` draft, and a second session that still holds the earlier draft saves an edit
- **THEN** the second save is refused with a draft-changed failure, and the file keeps the bytes of the first save

#### Scenario: Stored copy differs only in layout
- **WHEN** the stored copy of `skyfortress` has the same values as the session's draft but other whitespace and key order
- **THEN** the save is accepted and the file is written in the setup server's layout

#### Scenario: Stored copy is missing
- **WHEN** a session saves a draft whose stored copy was deleted from both folders
- **THEN** the save is refused with a draft-changed failure and no file is created

#### Scenario: Refused save keeps the session's draft
- **WHEN** a save is refused with a draft-changed failure
- **THEN** the session keeps the draft it had before the edit, and the builder is told that the map changed on disk
