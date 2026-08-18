# Product Spec: Developer Flow Automation for Java Spring Boot CI/CD

**Status:** Draft  
**Date:** 2026-08-18  
**Author:** ADLC Product

---

## Problem Statement

Development teams working on the Java Spring Boot application face friction across the software delivery lifecycle. Pipeline definitions are scattered across multiple ad-hoc YAML files (`.harness/TestRun.yaml`, `.harness/DemoMohit.yaml`, etc.) with inconsistent retry strategies, no standardised test quality gates, and manual configuration of observability integrations (AppDynamics, JaCoCo). As a result, developers spend significant time debugging pipeline failures, coverage reports are unreliable, and there is no single enforced path from code commit to production deployment. The goal of this feature is to establish a well-defined, automated developer flow that reduces toil, enforces quality standards, and gives every team member clear visibility from PR to deploy.

---

## Goals

1. **Standardise the CI pipeline** — provide a single, reusable Harness pipeline template that covers build, unit test, integration test, and code-coverage reporting for all Java microservices in this repo.
2. **Enforce quality gates** — block merges and deployments when test coverage drops below an agreed threshold (e.g., 80% line coverage via JaCoCo) or when tests fail.
3. **Automate observability wiring** — automatically configure AppDynamics APM agent injection and JaCoCo reporting as part of every Kubernetes deployment stage, eliminating manual `configFile.yml` edits.
4. **Reduce pipeline failures from misconfiguration** — replace ad-hoc inline shell scripts with versioned, parameterised stage and step templates (`stage1_V1.yaml`, `step1_V1.yaml`) validated at authoring time.
5. **Provide developer self-service** — allow developers to trigger the full dev flow (build → test → deploy to dev) via a single Harness input set, without requiring platform-team intervention.

---

## Non-Goals

- This spec does not cover production promotion or change-management approval workflows.
- This spec does not redesign the Kubernetes infrastructure or Helm charts.
- This spec does not introduce a new secrets management system; existing HashiCorp Vault integration remains unchanged.
- This spec does not address pipelines for non-Java services in other repositories.
- Multi-region or canary deployment strategies are out of scope for this iteration.

---

## User Stories

### US-1 — Developer: Consistent CI on every PR
> As a developer, I want every pull request to automatically trigger a standardised CI pipeline (compile → unit test → integration test → coverage check), so that I get pass/fail feedback within 10 minutes without configuring the pipeline myself.

### US-2 — Developer: Coverage gate enforcement
> As a developer, I want the pipeline to fail fast with a clear message when JaCoCo line coverage drops below the team threshold, so that I know exactly what to fix before the PR can be merged.

### US-3 — Tech Lead: Reusable pipeline templates
> As a tech lead, I want all teams to consume a shared, versioned stage template for Kubernetes deployment, so that platform changes (retry logic, rollback steps) propagate to all pipelines without manual edits in each repo.

### US-4 — SRE / Platform Engineer: Automated APM wiring
> As an SRE, I want AppDynamics credentials and tier configuration to be injected automatically from the environment's config file during each deploy, so that I no longer need to manually update `configFile.yml` per environment or service.

### US-5 — Developer: Self-service dev deployment
> As a developer, I want to trigger a full build-and-deploy to the Dev environment by providing only the service name and target image tag as inputs, so that I can validate my changes end-to-end without waiting for platform-team assistance.

### US-6 — Engineering Manager: Pipeline health visibility
> As an engineering manager, I want a consolidated dashboard showing build success rates, test durations, and coverage trends per service over time, so that I can identify teams or services with recurring delivery bottlenecks.

---

## Acceptance Criteria

### AC-1 — CI trigger on PR
- [ ] A Harness trigger is configured so that opening or updating a PR against `master` automatically starts the standardised CI pipeline within 2 minutes.
- [ ] The pipeline stages are: **Compile → Unit Test → Integration Test → Coverage Report**.
- [ ] PR status checks on GitHub reflect pass/fail for each stage.

### AC-2 — JaCoCo coverage gate
- [ ] The pipeline reads a `coverage.threshold` input (default `80`) and fails the Coverage Report stage if line coverage is below that value.
- [ ] Failure output includes the actual coverage percentage and the list of classes below threshold.
- [ ] Passing the gate produces a published HTML report accessible from the Harness execution UI.

### AC-3 — Versioned deployment template
- [ ] A `stage1_V1` Harness stage template exists that encapsulates: image pull, K8s rolling deploy, health check wait, and rollback on failure.
- [ ] All `.harness/*.yaml` pipeline files reference this template by `templateRef` + `versionLabel`; no inline duplication of rollout/rollback logic.
- [ ] Template upgrades are tested in a non-production pipeline before the version label is promoted.

### AC-4 — Automated AppDynamics injection
- [ ] The deployment stage reads AppDynamics credentials exclusively from Harness secrets (no plaintext in `configFile.yml`).
- [ ] `application-name` and `tier-name` are derived from the service identifier at runtime using a Harness expression, removing the need for per-service manual edits.
- [ ] A dry-run option in the pipeline validates APM connectivity before proceeding to deploy.

### AC-5 — Self-service input set
- [ ] A Harness input set named `dev-deploy-<service>` is available for each registered service, requiring only `serviceRef` and `imageTag` as user-supplied values.
- [ ] All other parameters (environment, infrastructure, secrets paths) are pre-filled from defaults defined in the input set.
- [ ] Execution from the input set completes a full build-and-deploy to the Dev environment without additional configuration.

### AC-6 — Retry and failure strategy standardisation
- [ ] All pipeline steps use a consistent failure strategy: retry 3 times with 5 s intervals; on final failure, mark stage as failed (not `MarkAsSuccess`).
- [ ] The `strategy.repeat` loop (currently `times: 10` in `TestRun.yaml`) is replaced with a data-driven matrix or removed where not intentional.
- [ ] No step silently swallows failures via `MarkAsSuccess` unless explicitly approved and documented.

### AC-7 — Dashboard (observability)
- [ ] A Harness Dashboard is created with widgets for: build success rate (7-day rolling), average CI duration per service, and JaCoCo coverage trend per service.
- [ ] Data is available within 5 minutes of pipeline completion.
