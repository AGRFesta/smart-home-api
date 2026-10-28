---
name: version-bump
description: "Use when cutting a release or changing the project version — \"bump the version\", \"release 1.5.0\", \"promote Unreleased\". Carries the release procedure: the version in app/build.gradle.kts, the image tag pinned in docker-compose.yml, and the changelog."
---

# Version bump

This file owns the release procedure. The version is one fact written in two places, plus the
changelog that dates it, so a bump touches all three:

1. `app/build.gradle.kts` — the `version` field.
2. `docker-compose.yml` — the default image tag (`agrfesta/smart-home-api:<version>`).
3. `CHANGELOG.md` — promote `## [Unreleased]` to `## [<version>] - <date>` and add a new empty
   `## [Unreleased]` on top.

❗️ **Nothing checks that steps 1 and 2 agree.** A tag left behind in `docker-compose.yml` deploys the
previous image under a release that says otherwise. After the edit, search both files for the old
version and confirm it no longer appears.

The user owns the commit, the tag and the deploy.
