---
name: create-system
description: Create a new ECS System for the game. Use when adding gameplay behavior.
---

# Create ECS System

Create a new ECS System following project conventions.

## Input
- System name (e.g., "PowerUp", "Cooldown", "Damage")
- Required components
- What it does each tick

## Steps

1. Create the system file at:
   `server/src/main/java/net/elytrarace/server/ecs/system/{Name}System.java`

2. Follow this template:
```java
package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.System;
import java.util.Set;

public final class {Name}System implements System {

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(/* required components */);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        var comp = entity.getComponent(SomeComponent.class);
        // Processing logic
    }
}
```

3. Create matching test at:
   `server/src/test/java/net/elytrarace/server/ecs/system/{Name}SystemTest.java`

4. Run `/build` to verify

## Rules
- Systems process BEHAVIOR, components hold DATA
- Keep process() under 20 lines
- Declare ALL required components in getRequiredComponents()
- Systems must be stateless where possible
