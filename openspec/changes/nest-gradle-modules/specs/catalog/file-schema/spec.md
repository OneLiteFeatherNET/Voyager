## MODIFIED Requirements

### Requirement: A schema document exists for each file kind
**Priority:** MoSCoW Must

THE SYSTEM SHALL ship one JSON Schema document for map files and one for cup files, both declaring draft 2020-12 and a
stable `$id`, under `voyager/platform/src/main/resources/schema/` as `map.schema.json` and `cup.schema.json`. The `$id` of
each document SHALL be the raw URL of that file in the repository's `main` branch, so it names the file's location.

#### Scenario: Schema declares its draft
- **WHEN** either schema document is parsed
- **THEN** its `$schema` is the 2020-12 meta-schema URI and it declares a non-empty `$id`

### Requirement: Shipped files reference their schema
**Priority:** MoSCoW Must

THE SYSTEM SHALL have every shipped map and cup file declare `$schema` as a relative path to the schema document of its
kind, and that path SHALL resolve to an existing file in the repository.

#### Scenario: Shipped map resolves its schema
- **WHEN** `voyager/server/src/main/resources/maps/elytraraceblueandred.json` is read
- **THEN** its `$schema` resolves to `voyager/platform/src/main/resources/schema/map.schema.json`

#### Scenario: Shipped cup resolves its schema
- **WHEN** `voyager/server/src/main/resources/cups/test_cup.json` is read
- **THEN** its `$schema` resolves to `voyager/platform/src/main/resources/schema/cup.schema.json`
