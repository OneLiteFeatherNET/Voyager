---
name: create-component
description: Create a new ECS Component for the game. Use when adding gameplay data.
---

# Create ECS Component

Create a new ECS Component following project conventions.

## Input
- Component name (e.g., "PowerUp", "Cooldown", "Health")
- Fields and their types

## Steps

1. Create the component file at:
   `server/src/main/java/net/elytrarace/server/ecs/component/{Name}Component.java`

2. Follow this template:
```java
package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;

public class {Name}Component implements Component {
    // Fields with sensible defaults
    // Getters and setters
    // reset() method if stateful
}
```

3. Create matching test at:
   `server/src/test/java/net/elytrarace/server/ecs/component/{Name}ComponentTest.java`

4. Test initial state, setters, and edge cases with JUnit 5 + AssertJ

5. Run `/build` to verify compilation

## Rules
- Components hold DATA only, no logic
- All fields must have sensible defaults
- Include reset() for components that accumulate state
- Implement `net.elytrarace.common.ecs.Component`
