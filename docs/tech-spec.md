# Tech Spec: Automated Developer CI/CD Flow for Kubernetes Java Client

**Author:** ADLC Engineering  
**Date:** 2026-08-18  
**Status:** Draft  
**Related:** [docs/product-spec.md](./product-spec.md)

---

## Overview

This document describes the technical approach for implementing the automated developer CI/CD flow specified in `docs/product-spec.md`. The implementation adds three Harness v1 YAML artifacts to the repository under `.harness/`, plus a config file (`configFile.yml`), covering the full build → test → coverage → APM pipeline triggered on every branch push and PR against `master`.

---

## Architecture

### Repository Layout

```
.harness/
  dev-flow-template.yaml   # Reusable stage template with input variables
  dev-flow-pipeline.yaml   # Project pipeline referencing the template
  trigger-pr.yaml          # Webhook trigger for push + PR events
configFile.yml             # Environment config (APM endpoints, thresholds)
docs/
  product-spec.md
  tech-spec.md             # (this file)
```

### Component Interactions

```
GitHub Push / PR
      │
      ▼
 trigger-pr.yaml  (Harness webhook trigger)
      │
      ▼
 dev-flow-pipeline.yaml
      │  uses template
      ▼
 dev-flow-template.yaml
  ├── Stage: Build       (mvn install)
  ├── Stage: Test        (mvn test + retry strategy)
  ├── Stage: Coverage    (JaCoCo report + gate)
  ├── Stage: APM Report  (AppDynamics non-prod)
  └── Stage: Deploy      (K8s apply + rollback on AllErrors)
```

### Key Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Pipeline format | Harness v1 (simplified) | Flat structure, `${{ }}` expressions, version-controlled in-repo |
| Config delivery | `configFile.yml` + Harness secrets | Separates non-sensitive config from credentials |
| Credential storage | HashiCorp Vault via `<+secrets.getValue(...)>` | AC-6: zero plaintext credentials |
| Retry strategy | `retryCount: 3`, `retryInterval: 5s` on test step | Addresses flaky test resilience (US-2, AC-3) |
| Coverage tooling | JaCoCo Maven plugin, published as pipeline artifact | Native Maven integration, no external tooling |
| Parallelism | `repeat` strategy on test stage with `times` input variable | Supports AC-8 parameterized stress runs |

---

## Key Changes

### 1. Harness Template — `dev-flow-template.yaml`

- Defines a reusable CI stage with typed inputs: `coverageThreshold` (default 80), `repeatTimes` (default 1), `mavenArgs`.
- **Build step:** `mvn install -DskipTests=false` — fails fast on compilation errors (AC-2).
- **Test step:** `mvn test ${{ inputs.mavenArgs }}` with `retryCount: 3` and `retryInterval: 5s` (AC-3).
- **Coverage step:** parses JaCoCo XML report; shell gate exits non-zero if line coverage < `${{ inputs.coverageThreshold }}` (AC-4, AC-5).
- **APM step:** posts a deployment marker to the AppDynamics REST API using credentials from `<+secrets.getValue("appdynamics_api_key")>` (AC-6).
- **Artifact upload:** JaCoCo HTML report archived after coverage step (AC-4).

### 2. Pipeline — `dev-flow-pipeline.yaml`

- References `dev-flow-template.yaml` for the CI stage.
- Adds a lightweight CD stage for K8s apply (`kubectl apply -f ...`) with a `rollback` step scoped to `onAllErrors` (AC-10).
- Execution time budget: build + test + coverage + APM target ≤ 15 min (AC-9); Maven daemon caching and parallel Surefire fork enabled.
- `repeat` strategy on test stage wired to pipeline input `repeat.times` (default 1, max 10) (AC-8).

### 3. Trigger — `trigger-pr.yaml`

- Webhook trigger type: `github`.
- Events: `push` (all branches) + `pull_request` targeting `master` (AC-1).
- Payload condition filters out `[skip ci]` commits.

### 4. Config File — `configFile.yml`

- Non-sensitive runtime values: AppDynamics controller URL, application name, tier name, JaCoCo threshold override.
- Consumed by pipeline steps via `<+configFile.getAsString("configFile.yml")>` expression.
- Sensitive values (API keys, Vault tokens) are **not** stored here — referenced via `<+secrets.getValue(...)>` only.

### 5. `pom.xml` Additions (minimal)

- Add JaCoCo Maven plugin (`jacoco-maven-plugin`) configured to run `prepare-agent` and `report` goals during `test` phase.
- Enable Surefire fork count (`forkCount=1C`) for parallel test execution.
- No changes to existing module structure or dependency versions.

---

## Sequence: PR Pipeline Run

```
1. Developer pushes branch / opens PR
2. GitHub webhook fires → trigger-pr.yaml activates pipeline
3. Pipeline clones repo, resolves configFile.yml
4. Build stage: mvn install (fail fast)
5. Test stage: mvn test [retry up to 3×]
6. Coverage stage: parse jacoco.xml → enforce threshold
7. APM stage: POST deployment event to AppDynamics (secrets from Vault)
8. Artifact stage: upload jacoco HTML report
9. (On merge to master) CD stage: kubectl apply → rollback on error
10. Pipeline status reported back to GitHub commit/PR check
```

---

## Risks & Mitigations

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-----------|--------|------------|
| R-1 | Maven build cache cold-start exceeds 15-min AC-9 budget on first run | Medium | Medium | Pre-warm Harness cache layer using `cacheKey: pom.xml`; Surefire parallel forks reduce test time |
| R-2 | JaCoCo report missing if tests fail before `report` goal | High | Medium | Configure JaCoCo `report` goal to run in `always()` condition so partial coverage is still published |
| R-3 | AppDynamics API rate-limit causing APM step failure blocks PR merge | Low | High | Set APM step `failOnError: false`; emit warning log rather than failing the pipeline |
| R-4 | Harness Vault integration not yet provisioned in target account | Medium | High | Document Vault secret path requirements in `configFile.yml` comments; add setup runbook to `docs/` |
| R-5 | `repeat.times` > 10 causes excessive runner utilization | Low | Medium | Cap input at `max: 10` in template input schema; document in pipeline description |
| R-6 | Rollback step (AC-10) requires `kubeconfig` secret pre-existing in Harness | Medium | Medium | Gate CD stage on secret existence check; surface clear error if missing |
| R-7 | Coverage threshold too strict at 80% for current codebase baseline | Medium | Low | Make threshold a configurable pipeline input (not hardcoded); default 80%, allow per-PR override |

---

## Out of Scope

- Changes to the `gen` / openapi-generator workflow.
- Kubernetes cluster provisioning or infra.
- Non-Java client libraries.
- GitHub branch protection rule configuration.

---

## Implementation Phases

| Phase | Deliverable | Owner | Target |
|-------|-------------|-------|--------|
| 1 | Product spec review & sign-off | Product + Tech Lead | Day 0 |
| 2 | `dev-flow-template.yaml` + `configFile.yml` | ADLC Eng | Day 1–2 |
| 3 | `dev-flow-pipeline.yaml` + `trigger-pr.yaml` | ADLC Eng | Day 2–3 |
| 4 | `pom.xml` JaCoCo + Surefire additions | Java team | Day 3 |
| 5 | Vault secret provisioning + smoke test | SRE | Day 4 |
| 6 | End-to-end validation against all ACs | QA / ADLC | Day 5 |
