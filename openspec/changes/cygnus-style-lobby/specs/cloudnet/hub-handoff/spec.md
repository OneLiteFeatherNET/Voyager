# Spec Delta

## Purpose

A waiting racer may leave the lobby for the network hub on their own. The leave is a plain handoff, so it does not change the
waiting room's bookkeeping for a racer whose cup has finished.

## ADDED Requirements

### Requirement: A waiting racer may leave to the hub from the lobby
**MoSCoW:** Must. WHEN a waiting racer leaves through the leave item or the `/leave` or `/hub` command, THE SYSTEM SHALL send that racer
to the network hub through the installed `HubHandoff` (`platform/handoff/HubHandoff.java`), and SHALL NOT mark the racer as handed off
in the waiting room. Without an installed handoff, the local fallback of this capability disconnects the racer with the translated
message.

#### Scenario: The leave item hands the racer to the hub
- **WHEN** a waiting racer right-clicks the leave item and the bridge has installed the handoff
- **THEN** the handoff receives that racer, and the waiting room does not record the racer as handed off

#### Scenario: The leave without a bridge
- **WHEN** a waiting racer uses `/leave` on a server with no installed handoff
- **THEN** the racer is disconnected with the translated fallback message, as the local fallback requirement describes

#### Scenario: A racer who left does not block the next countdown
- **WHEN** a racer leaves the lobby through the item and another racer joins
- **THEN** the waiting room counts only the racers still online, as before
