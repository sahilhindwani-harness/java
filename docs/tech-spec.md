# Tech Spec: Developer Flow Automation for Java Spring Boot CI/CD

**Status:** Draft  
**Date:** 2026-08-18  
**Author:** ADLC Engineering  
**Related:** [Product Spec](./product-spec.md)

---

## Overview

This document describes the technical approach for implementing the Developer Flow Automation feature defined in the product spec. The goal is to replace ad-hoc Harness YAML files with a coherent, versioned pipeline architecture that enforces quality gates, automates observability wiring, and enables developer self-service.

---

## Architecture

### Component Diagram

```
GitHub PR / Push
      │
      ▼
Harness Webhook Trigger (trigger-pr.yaml)
      │
      ▼
┌─────────────────────────────────────────────┐
│  Pipeline: dev-flow-pipeline.yaml           │
│                                             │
│  Stage 1 — CI  (references template)       │
│    Step: Compile  (mvn compile)             │
│    Step: Unit Test (mvn test)               │
│    Step: Integration Test (mvn verify)      │
│    Step: Coverage Gate (JaCoCo threshold)   │
│    Step: Publish Coverage Report (artifact) │
│                                             │
│  Stage 2 — CD  (references template)       │
│    Step: K8s Rolling Deploy (stage1_V1)     │
│    Step: Health Check Wait                  │
│    Step: APM Connectivity Dry-run           │
│    On Failure: Auto Rollback                │
└─────────────────────────────────────────────┘
      │
      ▼
Dev Kubernetes Cluster
(AppDynamics agent injected via Harness secret expressions)
```

### Key Files

| File | Purpose |
|---|---|
| `.harness/dev-flow-template.yaml` | Reusable CI stage template (inputs: `coverageThreshold`, `mavenArgs`, `repeatTimes`) |
| `.harness/dev-flow-pipeline.yaml` | Project pipeline referencing the template for CI + K8s CD stage |
| `.harness/trigger-pr.yaml` | Webhook trigger for push (all branches) and PR-to-master events; `[skip ci]` filter |
| `.harness/stage1_V1.yaml` | Versioned K8s deployment stage template (image pull → rolling deploy → health check → rollback) |
| `.harness/step1_V1.yaml` | Versioned step template for AppDynamics APM injection |
| `configFile.yml` | Enriched with JaCoCo threshold, APM config keys, and pipeline budget params (no plaintext credentials) |
| `pom.xml` | Add `jacoco-maven-plugin` (`prepare-agent` + `report` goals); Surefire `forkCount=1C` |
| `docs/input-sets/` | One input set YAML per registered service (`dev-deploy-<service>.yaml`) |

---

## Key Changes

### 1. Harness Pipeline & Template Structure

- **Remove** all ad-hoc pipelines (`TestRun.yaml`, `DemoMohit.yaml`, etc.) and consolidate into `dev-flow-pipeline.yaml`.
- **Create** `dev-flow-template.yaml` as a Harness v1 stage template with parameterised inputs. All pipelines reference it via `templateRef` + `versionLabel: v1`.
- **Create** `stage1_V1.yaml` for the K8s deployment stage. This encapsulates rolling deploy logic, health-check wait (`waitForSteadyState`), and `AllErrors` rollback so no pipeline duplicates this logic inline.

### 2. CI Quality Gates (JaCoCo)

- Add `jacoco-maven-plugin` to `pom.xml` bound to the `verify` lifecycle phase.
- Pipeline reads `<+inputs.coverageThreshold>` (default `80`) from the template input.
- A dedicated **Coverage Gate** step runs `mvn jacoco:check` with the threshold expression; on failure it exports actual coverage % and failing class list to the step output, then marks the stage **Failed** (never `MarkAsSuccess`).
- The HTML report is published as a Harness artifact and linked in the execution UI.

### 3. Failure Strategy Standardisation

- All steps adopt: `retry: 3`, `retryIntervals: [5s]`, `onRetryFailure: StageRollback`.
- The `strategy.repeat.times: 10` loop in the old `TestRun.yaml` is removed. Where matrix testing is genuinely needed, it is replaced with an explicit `matrix` strategy over a named input variable.
- A linting step (Harness YAML schema validation) in the CI stage catches `MarkAsSuccess` misuse at authoring time.

### 4. AppDynamics APM Wiring

- All AppDynamics credentials are moved to Harness secrets (`<+secrets.getValue("appdynamics_controller_key")>`, etc.).
- `configFile.yml` retains only non-sensitive config keys (`application-name` pattern, `tier-name` derivation expression).
- `application-name` and `tier-name` are computed at runtime: `<+service.name>-<+env.name>` — eliminating per-service manual edits.
- A **dry-run** step (`appdynamics-connectivity-check`) runs before the rolling deploy and fails fast if APM registration fails, preventing silent monitoring gaps.

### 5. Self-Service Input Sets

- One input set file per service under `docs/input-sets/dev-deploy-<service>.yaml`.
- Required user inputs: `serviceRef`, `imageTag`.
- Pre-filled: `environmentRef: dev`, `infrastructureRef: k8s-dev-cluster`, `coverageThreshold: 80`, all secret paths.
- Input sets are versioned alongside code; changes require a PR review to prevent config drift.

### 6. Webhook Trigger

- `trigger-pr.yaml` fires on: `push` to any branch, `pull_request` targeting `master`.
- A `[skip ci]` commit message filter is respected to allow documentation-only commits.
- Trigger payload expressions (`<+trigger.prNumber>`, `<+trigger.sourceBranch>`) are propagated as pipeline variables for GitHub status check reporting.

### 7. Dashboard (Observability)

- A Harness Dashboard is created with three widgets:
  1. **Build Success Rate** — 7-day rolling window, grouped by service.
  2. **Average CI Duration** — P50/P95 per service over 30 days.
  3. **JaCoCo Coverage Trend** — line chart sourced from pipeline step output variables published on each run.
- Data pipeline: step output variables → Harness execution metadata API → Dashboard data source. Latency target: ≤5 minutes post-execution.

---

## Migration Plan

1. **Phase 1 (this PR):** Add tech spec and product spec to `docs/`.
2. **Phase 2:** Create template files (`dev-flow-template.yaml`, `stage1_V1.yaml`, `step1_V1.yaml`) and update `pom.xml` with JaCoCo plugin.
3. **Phase 3:** Create `dev-flow-pipeline.yaml` and `trigger-pr.yaml`; wire secrets in Harness UI; validate end-to-end on a feature branch.
4. **Phase 4:** Create input sets per service; deprecate and remove old ad-hoc pipeline files.
5. **Phase 5:** Create Harness Dashboard; communicate rollout to all teams.

---

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| JaCoCo threshold blocks all PRs if baseline coverage is below 80% | High | High | Run coverage audit on `master` first; set initial threshold to current baseline, then ratchet up over 2 sprints. |
| Template version promotion breaks existing pipelines | Medium | High | Always test new `versionLabel` in a shadow pipeline before updating the canonical label. Gate promotion via PR review. |
| AppDynamics dry-run adds latency to every deploy | Medium | Low | Cap dry-run timeout at 30 s; make it skippable via `inputs.skipApmCheck: true` for hotfix scenarios. |
| Developers bypass the new pipeline by editing ad-hoc YAMLs | Medium | Medium | Add a CODEOWNERS rule for `.harness/` requiring platform-team approval on all pipeline YAML changes. |
| `[skip ci]` filter misused to avoid quality gates | Low | High | Restrict `[skip ci]` to paths that only affect `docs/` via trigger path filters; code changes always run CI. |
| Secret rotation causes APM injection failures silently | Low | High | Add APM connectivity assertion step; alert on step failure via Harness notification rule to the SRE on-call channel. |

---

## Open Questions

1. Should the coverage threshold be configurable per-service or enforced globally at 80%? (Decision needed from tech leads before Phase 3.)
2. Which Kubernetes namespace(s) does the Dev environment use? Required to finalise `infrastructureRef` defaults in input sets.
3. Is there an existing Harness Dashboard data source for step output variables, or do we need a custom webhook integration?
