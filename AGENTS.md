<!-- GENERATED FROM ../AGENTS.md. Do not edit directly. Run python scripts/sync_agent_docs.py --write. -->

# Agent Instructions

Before working anywhere in `dataTwoModelEcosystem`, do the default quick
startup read:

1. `TODAY.md`
2. `WORKBOARD.md`
3. `MANIFEST.md`

After reading `TODAY.md`: check whether the **One Main Push** has an unchecked
item (`- [ ]`). If it does not — either because the section is empty or all
items are already checked — stop and ask the user:
> "TODAY.md has no active main push. What is your one main push for today?"
Write their answer into `TODAY.md` before continuing. Do not start task work
until a main push is set.

Only do the deeper startup read when the task touches architecture, roadmap
status, repo boundaries, implementation targets, or plan conflicts:

1. `README.md`
2. `HUMAN.md`
3. `ROADMAP.md`
4. `MANIFEST.md`
5. `WORKBOARD.md`
6. `TODAY.md`

If this file is reached from a child repo, then after reading the parent
startup files appropriate to the task, the agent must check the current repo for
local instruction files and read them before editing or running task-specific
commands:

1. `CLAUDE.local.md`
2. `AGENTS.local.md`

Parent instructions set workspace context. Local instruction files set
repo-specific rules and must be honored unless the user explicitly overrides
them.

Child `AGENTS.md` and `CLAUDE.md` files are generated copies of this file. After
editing this file, run:

```powershell
python scripts/sync_agent_docs.py --write
```

Use `python scripts/sync_agent_docs.py --check` before commits or handoffs to
catch stale generated copies.

If planning or status docs disagree in a way that could change task order, repo
boundaries, or implementation targets, use the JSON conflict workflow:

- `/find-plan-conflict`: add actionable conflicts to `conflicts/open_conflicts.json`
- `/close-plan-conflict`: apply filled user responses and move resolved items to `conflicts/conflict_log.json`

Do not resolve roadmap/status conflicts ad hoc in chat when they should become
durable review items.
