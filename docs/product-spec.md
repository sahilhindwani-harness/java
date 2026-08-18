# Product Spec: APM & Code Coverage Integration in Harness CI/CD

**Author:** ADLC Product  
**Date:** 2026-08-18  
**Status:** Draft

---

## Problem Statement

Development teams using this Kubernetes Java client project rely on AppDynamics for application performance monitoring and JaCoCo for code coverage reporting. These tools are currently configured manually via `configFile.yml` and have no first-class integration with the Harness CI/CD pipeline. As a result:

- AppDynamics credentials and tier/application names are hard-coded or manually maintained, increasing the risk of misconfiguration across environments.
- JaCoCo coverage reports are generated during builds but not surfaced or enforced within the Harness pipeline, so coverage regressions go undetected until post-deployment.
- Engineers have no single pane of glass within Harness to review build health, test coverage trends, and APM metrics together.

---

## Goals

1. Automate ingestion and validation of AppDynamics configuration (host, account, tier, application) from Harness secrets at pipeline execution time.
2. Publish JaCoCo code coverage reports as a first-class artifact of every CI build, with configurable pass/fail thresholds.
3. Surface coverage metrics and AppDynamics health check status directly in the Harness pipeline execution UI.
4. Reduce credential sprawl by routing all APM secrets through Harness secret management (e.g., HashiCorp Vault), eliminating plaintext values in config files.

---

## Non-Goals

- Replacing AppDynamics or JaCoCo with alternative tools.
- Building a custom APM dashboard inside Harness (existing AppDynamics UI remains the source of truth for deep performance analysis).
- Enforcing APM integration for non-Java services or other repositories.
- Automated remediation of performance regressions detected by AppDynamics.

---

## User Stories

### US-1 — Secret-backed APM configuration
> As a platform engineer, I want AppDynamics credentials (account name, access key, host) to be injected into the pipeline from Harness secrets at runtime, so that no sensitive values are stored in the repository.

### US-2 — Automated JaCoCo threshold enforcement
> As a developer, I want the CI pipeline to fail automatically when code coverage drops below a defined threshold, so that I receive immediate feedback before a regression reaches production.

### US-3 — Coverage report artifact publishing
> As a developer, I want the JaCoCo HTML/XML report to be published as a build artifact in Harness after every CI run, so that I can review coverage details without leaving the Harness UI.

### US-4 — Per-environment APM tier configuration
> As a release engineer, I want each deployment stage (dev, staging, prod) to use the correct AppDynamics application name and tier name automatically, so that APM data is always attributed to the right environment without manual edits.

### US-5 — Pipeline health summary
> As an engineering manager, I want a summary step at the end of each pipeline run that reports the coverage percentage and the AppDynamics connectivity status, so that I can quickly assess build health in the Harness execution log.

---

## Acceptance Criteria

### AC-1 — Secret injection (US-1)
- [ ] AppDynamics `account-name`, `account-access-key`, `host-name`, and `port` are read exclusively from Harness secrets (HashiCorp Vault path `DCTRN/non-prod/appdynamics` or equivalent prod path).
- [ ] No plaintext credential values appear in any committed file or pipeline log.
- [ ] If a required secret is missing or inaccessible, the pipeline fails with a descriptive error message before the build step runs.

### AC-2 — JaCoCo threshold enforcement (US-2)
- [ ] The CI stage includes a step that reads the JaCoCo XML report and compares total line/branch coverage against a configurable threshold (default: 80% line coverage).
- [ ] The pipeline step is marked as failed and execution halts if coverage is below the threshold.
- [ ] The threshold value is configurable via a pipeline variable, requiring no YAML edits to change it.

### AC-3 — Coverage artifact publishing (US-3)
- [ ] After every successful (or failed-at-threshold) CI run, the JaCoCo HTML report is uploaded as a named artifact (`jacoco-coverage-report`) accessible from the Harness execution detail page.
- [ ] The artifact is retained for at least 30 days.

### AC-4 — Environment-aware APM configuration (US-4)
- [ ] The `application-name` and `tier-name` values passed to AppDynamics are derived from the target environment identifier (e.g., pipeline variable `<+env.name>`), not hard-coded.
- [ ] Deploying to dev, staging, and prod each resolves to distinct AppDynamics application/tier names without any manual override.

### AC-5 — Pipeline health summary (US-5)
- [ ] The final step of every pipeline run outputs a summary containing: coverage percentage (pass/fail), AppDynamics connectivity check result (reachable/unreachable), and the environment name.
- [ ] The summary is visible in the Harness execution console log and does not require opening any external tool.

---

## Out of Scope (Future Consideration)

- Bi-directional integration: triggering Harness pipelines from AppDynamics alerts.
- Historical coverage trend charts within Harness.
- Support for Sonar or other coverage tools.
