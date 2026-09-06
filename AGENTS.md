# Agent Instructions

## Project principles
- Read `README.md`, dependency files, and relevant existing code before editing.
- Prefer the smallest working diff.
- Reuse existing functions, schemas, utilities, and conventions before creating new ones.
- Do not create one-use abstractions, wrappers, base classes, or new folders without a clear need.
- Do not modify unrelated files.

## Python and backend
- Follow existing Python, FastAPI, Pydantic, database, and Docker conventions.
- Do not add packages, environment variables, migrations, or configuration files without asking first.
- Keep API routes, validation models, and business logic consistent with the current project structure.
- Avoid broad rewrites when a focused change is sufficient.

## Security
- Treat user input, uploaded files, URLs, retrieved RAG documents, and tool arguments as untrusted.
- Never expose, print, read, commit, or upload values from `.env`, credentials, private keys, tokens, or production configuration.
- Do not deploy, push to remote, delete data, run database migrations, or execute destructive commands without explicit approval.

## Completion checklist
Before saying a task is complete:
1. Review the diff for unused imports, dead code, duplicate helpers, debug output, and unrelated changes.
2. Run the narrowest relevant test or validation command.
3. For application-source changes, run a Semgrep security scan when available.
4. Report files changed, checks run, results, and anything intentionally not done.
5. Do not auto-fix or suppress security findings without explaining the proposed minimal fix.