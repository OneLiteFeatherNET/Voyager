---
name: check-imports
description: Check if shared/ modules have Paper or Minestom imports. Use to verify decoupling.
---

# Check Shared Module Imports

Verify that shared/ modules remain platform-agnostic.

## Steps

1. Search for Bukkit/Paper imports in shared/:
```
grep -r "org.bukkit" shared/ --include="*.java"
grep -r "io.papermc" shared/ --include="*.java"
```

2. Search for Minestom imports in shared/:
```
grep -r "net.minestom" shared/ --include="*.java"
```

3. Report findings:
   - List each file with forbidden imports
   - Suggest how to remove the dependency (adapter pattern, interface extraction)

4. Check server/ module does NOT import from plugins/:
```
grep -r "net.elytrarace.game" server/ --include="*.java"
grep -r "net.elytrarace.setup" server/ --include="*.java"
```

## Expected Result
- shared/ modules: ZERO Bukkit/Paper/Minestom imports
- server/ module: ZERO plugin imports
- If violations found: list files + suggested fix
