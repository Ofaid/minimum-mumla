# Codex worker workflow

The personal custom agent used for this project is:

```text
%USERPROFILE%\.codex\agents\luna-worker.toml
name = luna_worker
model = gpt-5.6-luna
model_reasoning_effort = max
```

The personal file is intentionally not committed. It must contain `name`, `description`, and
`developer_instructions`; the local Codex manual also allows `model` and
`model_reasoning_effort` in a standalone custom-agent file.

Use the main Sol agent for ambiguous architecture, prioritization, security decisions and integration.
Use `luna_worker` for clear repeatable work with a bounded contract. Max reasoning is the requested
default for this worker, but low/medium is normally more efficient for mechanical scans or simple
formatting when an explicit invocation override is available.

Every delegated task must state:

1. Exact repository and allowed files/components.
2. Files and behaviors that must not be touched.
3. Concrete expected behavior/deliverables.
4. Exact build/test/inspection commands.
5. Required return format: changed files, behavior, command results, blockers and risks.

The parent agent must inspect the returned diff and test evidence. A worker result is not evidence
that the feature is complete until it is integrated and verified from the main working tree.
