# Product Spec: Developer-Centric CI/CD Flow for Java Services

**Status:** Draft
**Date:** 2026-08-18
**Author:** ADLC Product

---

## Problem Statement

Developers working on Java services (Kubernetes-deployed, Spring-based) spend disproportionate time
configuring and debugging Harness pipelines rather than writing and shipping code. Pipeline
configuration is scattered across manually maintained YAML files, credentials are hard-coded or
improperly referenced via secrets, test runs lack retry intelligence, and there is no standard
developer-facing flow for iterating from local code change to verified production deployment.
The result is slow feedback loops, inconsistent pipeline quality, and elevated toil for both
developers and platform engineers.

---

## Goals

1. Provide a guided, opinionated CI/CD pipeline template for Java (Maven/Spring) services targeting
   Kubernetes, covering build → test → deploy → rollback.
2. Enable developers to iterate on pipelines in-repo (`.harness/` YAML) with immediate, meaningful
   feedback on failures — including smart retry policies and failure-strategy defaults.
3. Standardize secrets and config-file consumption (AppDynamics, JaCoCo, Vault-backed secrets) so
   developers never hard-code credentials.
4. Surface code-coverage (JaCoCo) and APM (AppDynamics) signals directly in pipeline execution
   results, making quality gates self-service.
5. Reduce time-to-first-green-pipeline for a new Java service from days to under 2 hours.

---

## Non-Goals

- This spec does not cover non-Java (Python, Node.js, Go) service pipelines.
- It does not replace or redesign the Harness platform secrets manager or Vault integration.
- It does not address multi-cloud or non-Kubernetes deployment targets.
- It does not define test authoring standards beyond pipeline-level quality gates.
- It does not cover production traffic management (canary, blue/green) in this iteration.

---

## User Stories

### US-1 — New Java Service Onboarding
> As a Java developer onboarding a new Spring service, I want a pipeline template I can drop into
> `.harness/` that builds, tests, and deploys my service to Kubernetes, so that I don't start from
> a blank YAML file.

**Acceptance Criteria:** See AC-1.

### US-2 — Reliable Test Execution with Smart Retries
> As a developer, I want flaky test steps to retry automatically with configurable backoff, and I
> want the pipeline to mark a step as succeeded only when the retry budget is exhausted and the
> failure is confirmed, so that I spend less time manually re-running pipelines.

**Acceptance Criteria:** See AC-2.

### US-3 — Secrets and Config-File Consumption
> As a developer, I want to reference AppDynamics credentials and other service credentials via
> Vault-backed secret expressions (`<+secrets.getValue(...)>`) in a documented, validated pattern,
> so that no plaintext credentials ever appear in YAML committed to the repo.

**Acceptance Criteria:** See AC-3.

### US-4 — Code Coverage Gate
> As a developer or tech lead, I want the pipeline to enforce a JaCoCo coverage threshold (e.g.,
> 80% line coverage) as a quality gate, failing the build if coverage drops below the threshold, so
> that coverage regressions are caught before merge.

**Acceptance Criteria:** See AC-4.

### US-5 — APM Integration Validation
> As a platform engineer, I want the pipeline to verify that the AppDynamics agent is reachable and
> the application tier is registered after each deployment, so that observability gaps are caught
> at deploy time rather than discovered during incidents.

**Acceptance Criteria:** See AC-5.

### US-6 — Rollback on Deployment Failure
> As a developer, I want a Kubernetes deployment that fails health checks to trigger an automatic
> stage rollback, so that a bad release does not persist in the environment.

**Acceptance Criteria:** See AC-6.

---

## Acceptance Criteria

### AC-1 — Pipeline Template for Java/K8s
- [ ] A reference pipeline template (`pipeline-java-k8s.yaml`) exists in `.harness/` and is
      documented in `docs/`.
- [ ] The template covers: Maven build, JUnit test run, Docker image build/push, Kubernetes
      rolling deployment, and a rollback step.
- [ ] The template compiles without errors when imported into a Harness project with a valid service
      and environment configured.
- [ ] README or inline comments explain every required input (serviceRef, environmentRef,
      infraRef).

### AC-2 — Retry Policy
- [ ] Test steps configure a `failureStrategies` block with at least 2 retry attempts and
      configurable retry intervals (default: 5 s, 10 s).
- [ ] After retry exhaustion the pipeline fails (not marks-as-success) unless explicitly overridden
      by the developer.
- [ ] Retry count and intervals are exposed as pipeline-level inputs so teams can tune them without
      editing step YAML.

### AC-3 — Secrets Pattern
- [ ] All credential references use `<+secrets.getValue("hashicorpvault://...")>` expressions; no
      plaintext values are present in any committed YAML.
- [ ] A `docs/secrets-guide.md` documents the Vault path conventions for AppDynamics and JaCoCo
      credentials used by this service.
- [ ] A linting check (CI step or pre-commit hook) flags any committed YAML that contains a
      literal password, access key, or token string.

### AC-4 — JaCoCo Coverage Gate
- [ ] The pipeline includes a post-test step that reads the JaCoCo XML report and evaluates line
      coverage against a configurable threshold (default: 80%).
- [ ] If coverage is below the threshold, the step fails with a clear message stating actual vs.
      required coverage percentage.
- [ ] The threshold is configurable as a pipeline variable without editing the step definition.

### AC-5 — AppDynamics Tier Validation
- [ ] After a successful Kubernetes deployment, a shell step pings the AppDynamics REST API to
      confirm the application tier (`DCTRN-DEV` or equivalent) is reporting as alive.
- [ ] If the tier is unreachable or not registered within a configurable timeout (default: 5 min),
      the step fails and triggers the rollback strategy.
- [ ] The AppDynamics host, port, account name, and application name are sourced exclusively from
      the `configFile.yml` / Vault secrets pattern (AC-3).

### AC-6 — Kubernetes Rollback
- [ ] The deployment stage includes a `rollbackSteps` block with a Kubernetes rollback step.
- [ ] If the deployment step or post-deploy health check fails, the rollback executes automatically
      without manual intervention.
- [ ] A rollback event is logged to the Harness pipeline execution log with the previous image tag
      that was restored.
