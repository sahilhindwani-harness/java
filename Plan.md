# README Improvement Plan

A short plan for making the project README more useful to new contributors and users.

## High priority

- Add a "Quick Start" section near the top with the minimal steps to get a working example running, before the deeper installation/versioning details.
  - Acceptance criteria: a new contributor can copy-paste the Quick Start snippet and get a working example running without reading any other section first.
- Consolidate the scattered example links into a single categorized table (Configuration, Basics, Streaming, Advanced) with one-line descriptions for faster scanning.
  - Acceptance criteria: every example currently linked in the README appears exactly once in the table, with a working link and a one-line description.
- Move the detailed Kubernetes version compatibility matrix into its own `COMPATIBILITY.md` and link to it, keeping the main README focused on getting started.
  - Acceptance criteria: `COMPATIBILITY.md` exists, contains the full matrix, and the README links to it correctly.

## Nice to have

- Add badges for build status, latest release version, and license directly under the title for at-a-glance project health.
  - Acceptance criteria: badges render correctly and each links to the corresponding CI/release/license page.
- Include a short "Getting Help" section pointing to issues, discussions, and the contributing guide.
  - Acceptance criteria: the section links to the issues page, a discussions/community channel, and CONTRIBUTING.md, and all links resolve.
