---
name: adr
description: Create an Architecture Decision Record. Use before major technical decisions.
---

# Create ADR

Create an Architecture Decision Record in docs/adr/.

## Input
- Decision title
- Context (why this decision is needed)
- Options to evaluate

## Steps

1. Find the next ADR number:
```bash
ls docs/adr/ 2>/dev/null | sort -r | head -1
```

2. Create `docs/adr/ADR-{NNN}-{kebab-title}.md` with this template:

```markdown
# ADR-{NNN}: {Title}

## Status
Proposed

## Date
{today}

## Context
{Why is this decision needed?}

## Decision
{What was decided and why?}

## Alternatives

| Option | Pro | Contra |
|---|---|---|
| A: {Name} | {Advantages} | {Disadvantages} |
| B: {Name} | {Advantages} | {Disadvantages} |

## Consequences

### Positive
- {What improves}

### Negative
- {What becomes harder}
```

3. Ask the user for approval before finalizing

## Rules
- Always present at least 2 alternatives
- Include concrete pro/contra for each
- Status starts as "Proposed" until user approves
