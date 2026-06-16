# Security Policy

## Supported Versions

This project is early-stage and currently supports the `main` branch only.

## Reporting a Vulnerability

Do not open a public issue for security-sensitive reports.

Use GitHub private vulnerability reporting if it is enabled for this repository. If it is not enabled, contact the repository owner directly and include:

- A short description of the issue.
- Steps to reproduce.
- Potential impact.
- Any relevant logs or configuration details, with secrets removed.

## Secret Handling

- Real credentials must never be committed.
- Use `.env.example` for placeholders only.
- Local `.env*`, key files, keystores, secret SQL files, and local seed data are ignored.
- Rotate any credential that may have been committed, even if the commit was later removed.
