---
name: check-imports
description: Check if legacy/shared/ modules have Paper or Minestom imports. Use to verify decoupling.
---

# Check Shared Module Imports

Verify that legacy/shared/ modules remain platform-agnostic.

## Steps

1. Search for Bukkit/Paper imports in legacy/shared/:
```
grep -r "org.bukkit" legacy/shared/ --include="*.java"
grep -r "io.papermc" legacy/shared/ --include="*.java"
```

2. Search for Minestom imports in legacy/shared/:
```
grep -r "net.minestom" legacy/shared/ --include="*.java"
```

3. Report findings:
   - List each file with forbidden imports
   - Suggest how to remove the dependency (adapter pattern, interface extraction)

4. Check legacy/server/ module does NOT import from legacy/plugins/:
```
grep -r "net.elytrarace.game" legacy/server/ --include="*.java"
grep -r "net.elytrarace.setup" legacy/server/ --include="*.java"
```

## Expected Result
- legacy/shared/ modules: ZERO Bukkit/Paper/Minestom imports
- legacy/server/ module: ZERO plugin imports
- If violations found: list files + suggested fix
