# Tech Spec: Automated Developer Flow Pipeline for Java Kubernetes Client

**Status:** Draft  
**Author:** ADLC Engineering  
**Date:** 2026-08-18  
**Refs:** [Product Spec](./product-spec.md)

---

## Overview

This document describes the technical implementation of the automated developer flow pipeline defined in the product spec. The solution uses a Harness v1 (simplified) pipeline template stored in `.harness/` and a GitHub webhook trigger to drive lint → test → build → (optional) deploy for every branch push or pull request targeting the `java` Kubernetes client repository.

---

## Architecture

### High-Level Flow

```
GitHub Push / PR Open
        │
        ▼
  Harness Trigger
  (webhook + branch filter)
        │
        ▼
┌──────────────────────────────────────────────┐
│  Pipeline: dev-flow-pipeline                 │
│                                              │
│  Stage 1: CI  (always runs)                  │
│    Step 1.1  Lint / Static Analysis          │
│    Step 1.2  Unit Tests          (parallel)  │
│    Step 1.3  Integration Tests   (parallel)  │
│    Step 1.4  Maven Build                     │
│    Step 1.5  Artifact Upload                 │
│                                              │
│  Stage 2: CD  (master merge only)            │
│    Step 2.1  Deploy to target environment    │
└──────────────────────────────────────────────┘
        │
        ▼
  GitHub Status Check → PR gate
```

### Component Responsibilities

| Component | Responsibility |
|-----------|---------------|
| `.harness/dev-flow-template.yaml` | Canonical reusable pipeline template (Harness v1) |
| `.harness/dev-flow-pipeline.yaml` | Project-level pipeline that references the template |
| `.harness/trigger-pr.yaml` | Harness webhook trigger — fires on branch push and PR open |
| GitHub branch protection | Enforces required status check `harness/dev-flow-pipeline` before merge |
| Maven (`pom.xml`) | Build, test, and lint (Checkstyle/SpotBugs) lifecycle management |
| Artifact repository (Harness Artifact Registry or JFrog) | Stores built JAR files on successful build stage |

---

## Key Implementation Changes

### 1. Harness Pipeline Template (`.harness/dev-flow-template.yaml`)

A single Harness v1 template with the following structure:

- **CI stage** with five sequential steps:
  1. `lint` — runs `mvn checkstyle:check spotbugs:check` via a `run` step.
  2. `unit-test` — runs `mvn test -Dsurefire.failIfNoSpecifiedTests=false`; `failure_strategy: abort` so downstream steps are skipped on failure (satisfies AC-3).
  3. `integration-test` — runs `mvn verify -P integration-tests`; also uses `failure_strategy: abort`.
  4. `build` — runs `mvn package -DskipTests`; produces `target/*.jar`.
  5. `artifact-upload` — uses Harness Upload Artifact step pointing to the configured artifact store (satisfies AC-7).

- **Retry policy** applied at template level: `retry: { attempts: 3, interval: 5s }` on every `run` step (satisfies AC-4).

- **CD stage** (conditional):  
  `when: ${{ pipeline.trigger.sourceBranch == "master" }}` — executes only on master merges (satisfies AC-8).

**Template inputs** (parameterized for reuse, satisfying AC-6):
```yaml
inputs:
  maven_opts:        string   # e.g. "-Xmx2g"
  artifact_repo:     string   # target artifact registry connector ID
  deploy_env:        string   # Harness environment ID for CD stage
  retry_attempts:    number   # default 3
  retry_interval:    string   # default "5s"
```

### 2. Project Pipeline (`.harness/dev-flow-pipeline.yaml`)

References the template above via a `template` block, supplying project-specific input values. This keeps individual projects in sync when the template is updated centrally (satisfies AC-6).

### 3. Harness Trigger (`.harness/trigger-pr.yaml`)

- Type: `webhook` (GitHub)
- Events: `push` (all branches) + `pull_request` (opened, synchronize, reopened)
- Pipeline to trigger: `dev-flow-pipeline`
- Maximum trigger-to-execution latency target: < 60 s (satisfies AC-1); GitHub webhook delivery is typically < 5 s; Harness queue processing adds < 30 s under normal load.

### 4. GitHub Branch Protection

Configure the following via GitHub repo settings (or `gh` / Terraform IaC):

- Required status check: `continuous-integration/harness` (the check name Harness posts back).
- **Require branches to be up to date before merging**: enabled.
- Block merge while check is `pending` or `failure` (satisfies AC-5).

### 5. Maven Lifecycle Integration

Lint and static analysis are wired into the Maven lifecycle rather than ad-hoc shell scripts:

- **Checkstyle** bound to `validate` phase via `maven-checkstyle-plugin`.
- **SpotBugs** bound to `verify` phase via `spotbugs-maven-plugin`.
- Integration test profile (`-P integration-tests`) activates the Kubernetes API integration test suite.

### 6. Artifact Upload

After a successful `mvn package`:

- Harness `upload_artifact` step uploads `target/*.jar` to the configured connector.
- Artifact metadata (commit SHA, branch, build number) is attached as labels.
- On feature branches, artifact is tagged `snapshot-<short-sha>`; on master, tagged `release-candidate-<build-number>`.

---

## Data Flow & State

```
Commit SHA
  → Harness trigger receives webhook payload (includes SHA, branch, PR number)
  → Pipeline run created; SHA stored as pipeline variable
  → Each step runs in a fresh container on the Harness runner
  → Test reports (JUnit XML) collected by Harness Test Intelligence step
  → JAR artifact uploaded with SHA label
  → GitHub Commit Status API updated (pending → success | failure)
```

All pipeline state is stored in Harness; no external state store is required for this iteration.

---

## Risks & Mitigations

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-----------|--------|------------|
| R-1 | Integration tests are slow (>15 min), violating AC-10 | Medium | High | Parallelize unit and integration test steps; set a 12-minute `timeout` on the CI stage to surface regressions immediately. If tests still exceed budget, split into a separate nightly pipeline. |
| R-2 | Flaky integration tests cause spurious PR blocks | High | High | Apply `retry: { attempts: 3 }` (AC-4). Mark known-flaky tests with `@Ignore` + Jira ticket until fixed. Harness Test Intelligence can auto-quarantine persistent failures. |
| R-3 | Harness runner capacity spikes during business hours | Low | Medium | Configure runner autoscaling group (min 2, max 10 replicas). Monitor queue depth in Harness dashboards. |
| R-4 | Template version drift — projects pin old template version | Medium | Medium | Enforce template version via OPA policy in Harness. Automated PR to bump template ref on template release. |
| R-5 | Artifact repository connector misconfiguration blocks uploads | Low | High | Test connector in staging before rollout. Upload step is non-blocking (allow_failure: false) but isolated to the last step so earlier pass/fail signal is not affected. |
| R-6 | GitHub status check name mismatch blocks or bypasses gate | Low | Critical | Document exact check name (`continuous-integration/harness`) and validate in a dry-run PR before enabling branch protection. |
| R-7 | CD stage fires on non-master merges due to trigger misconfiguration | Low | High | Double-gate: both trigger filter (`targetBranch: master`) and pipeline stage condition (`when: sourceBranch == "master"`) must pass. |

---

## Rollout Plan

1. **Phase 1 (this PR):** Add `docs/tech-spec.md`; no functional changes.
2. **Phase 2:** Add `.harness/dev-flow-template.yaml` and `.harness/dev-flow-pipeline.yaml` (v1 format).
3. **Phase 3:** Add `.harness/trigger-pr.yaml`; wire Harness ↔ GitHub webhook.
4. **Phase 4:** Enable GitHub branch protection required status check on `master`.
5. **Phase 5:** Monitor first 50 pipeline runs; tune retry policy and timeouts.

---

## Open Questions

- Q1: Which artifact registry (Harness internal vs. JFrog Artifactory) is the target for JAR uploads? Decision needed before Phase 2.
- Q2: Is there an existing Harness environment + infrastructure definition for the optional CD deploy stage, or does one need to be created?
- Q3: Should the integration test stage run on every PR, or only on merges to `master` to save runner minutes?
