# Tech Spec: Developer-Centric CI/CD Flow for Java Services

**Status:** Draft
**Date:** 2026-08-18
**Author:** ADLC Engineering
**Linked Spec:** [docs/product-spec.md](./product-spec.md)

---

## Overview

This document describes the technical approach for implementing the Developer-Centric CI/CD Flow
described in `docs/product-spec.md`. It covers the architecture of the new Harness pipeline
artifacts, the key code and configuration changes required, and the principal risks.

---

## Architecture

### 1. Harness Artifact Layout

All new YAML artifacts live under `.harness/` in the repository root, following Harness
in-repo pipeline-as-code conventions:

```
.harness/
  dev-flow-template.yaml       # Reusable CI stage template (US-1, US-2, US-4, US-5)
  dev-flow-pipeline.yaml       # Project pipeline consuming the template + K8s CD stage (US-1, US-6)
  trigger-pr.yaml              # Webhook trigger for push / PR-to-master events
docs/
  product-spec.md              # Product requirements
  tech-spec.md                 # This document
  secrets-guide.md             # Vault path conventions (AC-3)
  input-sets/
    dev-deploy-java-client.yaml  # Self-service input set for developers (US-1)
```

### 2. Pipeline Template (`dev-flow-template.yaml`)

A Harness v1 **stage template** with the following steps, in order:

| Step | Type | Notes |
|------|------|-------|
| `compile` | `Run` | `mvn -B compile` |
| `unit-test` | `Run` | `mvn -B test`; failure strategy: retry 3× at 5 s intervals; fails pipeline on exhaustion |
| `integration-test` | `Run` | `mvn -B verify -Pfailsafe`; same retry policy |
| `coverage-gate` | `Run` | Reads `target/site/jacoco/jacoco.xml`; fails if line coverage < `$COVERAGE_THRESHOLD` |
| `publish-report` | `Run` | Uploads JaCoCo HTML report as a Harness artifact |
| `apm-validate` | `Run` | Calls AppDynamics REST API; fails + triggers rollback if tier unreachable within timeout |

**Template inputs** (exposed as pipeline-level variables):
- `coverageThreshold` — integer, default `80`
- `retryTimes` — integer, default `3`, max `10`
- `retryInterval` — string, default `5s`
- `mavenArgs` — string, default `""` (passed to every Maven step)

All credential references use `<+secrets.getValue("hashicorpvault://...")>` — no plaintext
values anywhere in YAML (AC-3).

### 3. Pipeline (`dev-flow-pipeline.yaml`)

A top-level v1 pipeline that:

1. Consumes the stage template for the CI phase.
2. Adds a Kubernetes CD stage (`K8sRollingDeploy`) wired to `serviceRef`, `environmentRef`, and
   `infraRef` — all provided as pipeline inputs.
3. Configures a `rollbackSteps` block containing a `K8sRollingRollback` step (AC-6).
4. Uses `failureStrategies: [{onFailure: {errors: [AllErrors], action: StageRollback}}]` at the
   CD stage level so any health-check failure triggers automatic rollback.

### 4. Trigger (`trigger-pr.yaml`)

A Harness v1 webhook trigger that fires the pipeline on:
- **Push** to any branch
- **Pull request** targeting `master`

A commit-message filter (`[skip ci]`) suppresses the trigger for documentation-only commits.

### 5. Secrets & Config Pattern

- AppDynamics credentials (host, port, account name, access key, application name, tier name) are
  stored in HashiCorp Vault and referenced via `<+secrets.getValue("hashicorpvault://appdynamics/...")>`.
- `configFile.yml` holds **non-secret** runtime parameters (JaCoCo threshold, pipeline budget,
  AppDynamics host/port) consumed via `<+configFile.getAsString("configFileId")>`.
- A `docs/secrets-guide.md` documents the Vault path schema for this service.
- A pre-commit hook (or dedicated CI `lint-secrets` step) runs `git diff --cached | grep -E
  'password|accessKey|token' --exit-code` and blocks commits containing plaintext credentials.

### 6. Build Tooling (`pom.xml`)

- Add `jacoco-maven-plugin` with `prepare-agent` (bound to `initialize`) and `report` (bound to
  `verify`) goals so the coverage XML is always produced before the gate step reads it.
- Set `maven-surefire-plugin` `forkCount=1C` (one fork per CPU core) to parallelise test execution
  and reduce CI wall-clock time.

---

## Key Changes

| File | Change |
|------|--------|
| `.harness/dev-flow-template.yaml` | New — reusable CI stage template |
| `.harness/dev-flow-pipeline.yaml` | New — full CI + K8s CD pipeline |
| `.harness/trigger-pr.yaml` | New — push + PR webhook trigger |
| `configFile.yml` | Updated — remove hardcoded APM values; add JaCoCo threshold + budget params |
| `pom.xml` | Updated — add JaCoCo plugin; add Surefire `forkCount=1C` |
| `docs/secrets-guide.md` | New — documents Vault path conventions |
| `docs/input-sets/dev-deploy-java-client.yaml` | New — self-service input set |

---

## Risks

### R-1: JaCoCo Report Availability
**Risk:** The coverage gate step assumes `target/site/jacoco/jacoco.xml` exists. If the build
workspace is not persisted between the `unit-test` and `coverage-gate` steps the file may be
missing, causing a false failure.
**Mitigation:** Use a shared Harness volume mount or a `cache` step to persist `target/` between
steps within the stage. Document this requirement in `dev-flow-template.yaml` inline comments.

### R-2: AppDynamics Tier Registration Latency
**Risk:** APM tier registration after a Kubernetes rolling deploy can take longer than the
default 5-minute timeout in low-resource environments, causing spurious rollbacks.
**Mitigation:** Expose `apmValidationTimeoutMinutes` as a template input (default `5`, max `15`)
so teams can tune it. Log a warning (not a failure) if the tier is unregistered but the pod is
healthy, and document this edge case.

### R-3: Vault Secret Path Drift
**Risk:** If Vault paths rotate (key rotation, team migration) the pipeline breaks silently at
runtime rather than at YAML validation time.
**Mitigation:** Add a `validate-secrets` CI step that calls `vault kv get <path>` for each
expected secret path and fails early if a path is missing. Document the rotation procedure in
`docs/secrets-guide.md`.

### R-4: Template Version Coupling
**Risk:** Future changes to `dev-flow-template.yaml` (e.g. new required inputs) break all
pipelines that reference the template without an explicit version pin.
**Mitigation:** Version the template with a `versionLabel` field. Pipelines reference a specific
version; a CHANGELOG in `docs/` tracks breaking changes. Deprecate versions with a 30-day notice.

### R-5: Retry Budget and Test Flakiness Masking
**Risk:** A generous retry policy (e.g. `retryTimes=10`) may mask persistent test flakiness
instead of surfacing it, leading to inflated pipeline pass rates.
**Mitigation:** Cap `retryTimes` input at `10`. Emit a warning annotation in the pipeline
execution log for every retry so flaky tests are visible even when the step ultimately passes.

---

## Out of Scope

- Non-Java (Python, Node.js, Go) pipelines
- Canary / blue-green deployment strategies
- Redesign of the Harness Vault integration or secrets manager
- Non-Kubernetes (ECS, bare metal) deployment targets
