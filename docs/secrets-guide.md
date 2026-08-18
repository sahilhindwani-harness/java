# Secrets Guide: Vault Path Conventions

This document describes the HashiCorp Vault path conventions for credentials used by the
Developer-Centric CI/CD Flow for Java services in this repository.

## AppDynamics Credentials

All AppDynamics credentials are stored under the `DCTRN/non-prod/appdynamics` path in Vault.

| Credential | Vault Key | Harness Expression |
|---|---|---|
| Account Name | `#accountname` | `<+secrets.getValue("hashicorpvault://appdctrn_hehv_p/DCTRN/non-prod/appdynamics#accountname")>` |
| Account Access Key | `#accountaccesskey` | `<+secrets.getValue("hashicorpvault://appdctrn_hehv_p/DCTRN/non-prod/appdynamics#accountaccesskey")>` |

The `application-name` and `tier-name` are **not** stored in Vault. They are derived at runtime
from Harness service and environment context: `<+service.name>-<+env.name>`.

The `host-name`, `port`, and `ssl-enabled` values are non-sensitive and live in `configFile.yml`.

## Linting Gate

A dedicated `lint-secrets` CI step (or pre-commit hook) blocks commits containing plaintext
credentials by running:

```bash
git diff --cached | grep -E 'password|accessKey|token|secret' --exit-code
```

Any match causes the step to fail. All credentials must use `<+secrets.getValue(...)>` expressions.

## Vault Path Rotation Procedure

If paths rotate (key rotation or team migration):

1. Update all `hashicorpvault://` expressions in `.harness/dev-flow-template.yaml`.
2. Run the `validate-secrets` CI step to confirm the new path is reachable before merging.
3. Update this guide with the new path and key name.
4. Announce the change to consumers with at least 30 days notice (see R-3 in `tech-spec.md`).

## Adding a New Secret

1. Store the value in Vault under the `DCTRN/non-prod/` namespace.
2. Add an entry to this table above.
3. Reference it in pipeline YAML only via `<+secrets.getValue(...)>` — never inline the value.
