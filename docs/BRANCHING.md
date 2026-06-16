# Branching And Review

## Branch Model

- `main` is the stable branch.
- Feature work should use short-lived branches named `feat/<scope>`, `fix/<scope>`, `test/<scope>`, or `docs/<scope>`.
- Merge through pull requests.
- Prefer squash merges for a clean public history.

## Recommended Main Branch Protection

Enable these settings in GitHub:

- Require a pull request before merging.
- Require approvals: `1`.
- Dismiss stale approvals when new commits are pushed.
- Require conversation resolution before merging.
- Require linear history.
- Do not allow force pushes.
- Do not allow deletions.
- Include administrators once the workflow is stable.

When CI is reintroduced, also enable:

- Require status checks to pass before merging.
- Require branches to be up to date before merging.
- Add required checks for unit tests, integration tests, formatting, and secret scanning.

## Pull Request Expectations

Every feature PR should include:

- A clear summary of behavior changed.
- Acceptance criteria in the linked issue or PR body.
- Tests that match the changed feature surface.
- Migration notes when database schema changes.
- Manual verification notes when behavior cannot be fully automated.
