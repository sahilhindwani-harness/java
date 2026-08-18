# Product Spec: Automated Developer CI/CD Flow for Kubernetes Java Client

**Author:** ADLC Product  
**Date:** 2026-08-18  
**Status:** Draft

---

## Problem Statement

The Kubernetes Java client library lacks a standardized, automated CI/CD workflow that covers the full developer lifecycle: build, test, quality gate, and observability. Developers currently rely on ad-hoc scripts and manual steps to build, run tests, retry on flakiness, and integrate with APM/coverage tooling (AppDynamics, JaCoCo). This creates inconsistency across contributors, slows down feedback loops, and makes it difficult to enforce quality gates at scale.

---

## Goals

1. Provide a single, repeatable Harness pipeline that automates the end-to-end developer flow: build → test → coverage → APM integration.
2. Reduce time-to-feedback for contributors by surfacing test results and coverage reports automatically on every PR/push.
3. Enforce quality gates (test pass rate, code coverage threshold) before changes can be merged.
4. Integrate with observability tooling (JaCoCo for coverage, AppDynamics for runtime APM) without manual configuration per developer.
5. Support configurable retry strategies for flaky tests to avoid false negatives blocking the pipeline.

---

## Non-Goals

- Replacing or modifying the existing code-generation workflow (openapi-generator / `gen` repo).
- Managing Kubernetes cluster provisioning or infra outside the pipeline scope.
- Supporting non-Java client libraries in this spec.
- Enforcing branch protection rules at the GitHub level (handled separately by repo admins).

---

## User Stories

### US-1 — Contributor: Automated Build & Test on Push
> As a **contributor**, I want my code to be built and tested automatically when I push to a branch, so that I get fast feedback without manually running `mvn install` locally.

### US-2 — Contributor: Flaky Test Resilience
> As a **contributor**, I want failing tests to be retried automatically (up to 3 times with a 5-second interval) before the pipeline marks a step as failed, so that transient/flaky test failures do not block my work.

### US-3 — Tech Lead: Code Coverage Gate
> As a **tech lead**, I want JaCoCo coverage reports generated and a minimum coverage threshold enforced on every pipeline run, so that untested code cannot be silently merged.

### US-4 — SRE / Developer: APM Integration
> As an **SRE or developer**, I want the pipeline to report runtime metrics to AppDynamics (non-prod tier) automatically, so that I can observe application behavior in CI without manual instrumentation.

### US-5 — Developer: Pipeline Config as Code
> As a **developer**, I want all pipeline and environment configuration stored in the repository (`.harness/`, `configFile.yml`), so that the CI/CD setup is version-controlled and auditable alongside the code.

### US-6 — Developer: On-Demand Parallel Test Runs
> As a **developer**, I want to be able to trigger multiple parallel test repetitions (e.g., run a test step N times concurrently) to stress-test reliability, so that I can validate stability of new features before release.

---

## Acceptance Criteria

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| AC-1 | Pipeline triggers automatically on push to any branch and on PR creation against `master`. | Observe pipeline execution in Harness on a test push. |
| AC-2 | Build step compiles the project via `mvn install` and fails fast if compilation errors exist. | Introduce a compile error; verify pipeline fails at build step. |
| AC-3 | Test step executes the full Maven test suite and retries each failed step up to 3 times with 5-second intervals before marking as failed. | Simulate a flaky test; confirm retry behavior in pipeline logs. |
| AC-4 | JaCoCo coverage report is generated and published as a pipeline artifact after each run. | Inspect artifacts tab in Harness after a successful pipeline run. |
| AC-5 | Pipeline fails the coverage gate if line coverage falls below the configured threshold (default: 80%). | Commit code with insufficient test coverage; verify pipeline fails at gate step. |
| AC-6 | AppDynamics APM credentials are sourced exclusively from Harness secrets (HashiCorp Vault) — no plaintext credentials in code or config files. | Audit `configFile.yml` and pipeline YAML; confirm all sensitive values use `<+secrets.getValue(...)>` expressions. |
| AC-7 | All pipeline and environment configuration is stored in `.harness/` and `configFile.yml` in the repository and applied without manual UI changes. | Delete pipeline in Harness UI; re-sync from repo; confirm pipeline is restored identically. |
| AC-8 | Pipeline supports a parameterized "repeat N times" strategy for the test step, configurable per run. | Trigger pipeline with `repeat.times=5`; verify 5 test executions appear in the run log. |
| AC-9 | Pipeline execution completes (build + test + coverage + APM report) within 15 minutes for a standard PR. | Measure wall-clock time across 5 consecutive PR pipeline runs. |
| AC-10 | Rollback step is defined for the deployment stage and executes automatically on `AllErrors`. | Introduce a deployment failure; confirm rollback step activates. |
