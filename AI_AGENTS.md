# Mitti GO Admin — notes for AI agents

> Entry point for any AI coding agent. `CLAUDE.md` only points here.
> **Updated:** 2026-10-21

## What this is

This is the owner's web panel for **Mitti GO**, a kids' video app on YouTube. It covers:

- prices and what Pro includes;
- promo codes;
- users;
- contacts, legal links and release notes;
- recommended and official channels;
- YouTube quota and its log;
- admins and the audit log;
- **Контент → Категории**: the Learning catalog by topic and language, with hide / show (C50).

The panel has **no database or YouTube key of its own**. Everything goes through the backend's `/api/v1/admin/**`.

Related repos are listed in the backend's `AI_AGENTS.md`. The app is `~/Documents/Mitti-go-MAIN`, and its `docs/` hold the product
rules. The backend is `~/Documents/Mitti-go-backend`.

## Stack

- Java 21, Spring Boot 4.1, Thymeleaf server-rendered pages, Spring Security.
- `backend/BackendClient` is the HTTP client to the backend. Base URL: `mittigo.backend-url`, env `BACKEND_URL`.
- Sign-in: `security/BackendAuthenticationProvider` checks the login against the backend and keeps the backend's admin token in
  the session (8 h).
- The first admin comes from the backend's `mittigo.admin.bootstrap-*` settings.

## Run locally

Start the backend first (port 8280), then:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr"
BACKEND_URL=http://localhost:8280/api/v1 ./gradlew bootRun --args='--server.port=8290'
```

Open http://localhost:8290. `ADMIN_SECURE_COOKIE=true` on a real HTTPS server. Local overrides go in `./application-local.yaml`,
which is git-ignored and must never be committed.

## Code map (`src/main/java/uz/mittisoftware/mittigo/admin/`)

| Where | What |
|---|---|
| `web/*Controller` | One controller per section. `Flash.run` wraps a backend call and shows its message. `Models` holds the DTO records; `PanelAdvice` adds common model attributes |
| `templates/` | One page per section. `fragments.html` has the layout and the nav (add a new section's link there) |
| `static/` | `admin.css`. CSP allows `img-src 'self' https: data:`, so YouTube thumbnails load |

## Rules

- The UI is in Russian (the owner's language).
- Every write goes through a backend admin endpoint, which writes the audit log.
- Never show or log tokens, passwords or promo codes in full.
- Don't commit or push unless the user says so («залей»).
