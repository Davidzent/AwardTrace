# 0001: Modular monolith with runtime roles

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

The system has five jobs: ingest, pipeline, indexer, enricher, and API. It has one developer, one host, and one event flow. Microservices solve team scaling and independent release problems this project doesn't have, and they cost five builds, five images, five memory footprints, and network calls where method calls would do.

## Decision

Build one Spring Boot application. Each job is a Spring Modulith module with its own profile, and a deployment chooses which profiles to run. In v1, one JVM runs all five.

## Consequences

- One build and one image to deploy.
- Roles can still run as separate processes by changing profiles.
- Module boundaries need enforcement, so `ModuleBoundaryTest` and `ProfileIsolationTest` are mandatory.
- A memory leak in one role affects all of them while they share a JVM.

## Alternatives considered

| Alternative | Why not |
|---|---|
| A separate service per role | Five times the operational surface for no benefit at this size |
| One application with no module structure | Nothing stops modules reaching into each other, so the roles could never be split |
