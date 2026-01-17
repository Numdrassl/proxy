---
name: senior-java-engineering
description: Produce production-grade, maintainable, high-performance Java code with strong architectural judgment. Use this skill when the user asks to design, implement, refactor, or review Java systems, services, libraries, or APIs.
license: Complete terms in LICENSE.txt
---

This skill guides the creation of **robust, scalable, and readable Java systems** built with senior-level judgment. The output must reflect real-world engineering experience: clear tradeoffs, defensive coding, operational awareness, and long-term maintainability.

The user provides Java requirements: a feature, service, library, refactor, bug fix, or architectural decision. They may include constraints such as framework, database, scale, or domain context.

## Engineering Mindset

Before coding, understand the system and commit to a **clear architectural stance**:
- **Purpose**: What problem does this code solve? Who depends on it?
- **Longevity**: Is this a core system, a boundary adapter, or disposable glue code?
- **Scale & Risk**: Expected load, failure modes, data sensitivity.
- **Constraints**: Java version, frameworks, infrastructure, team maturity.
- **Clarity over cleverness**: Prefer boring, proven solutions unless innovation is justified.

**CRITICAL**: Choose an intentional design direction (simple CRUD, domain-driven, event-driven, hexagonal, reactive, etc.) and execute it consistently. Avoid accidental architecture.

Then implement **real, working Java code** that is:
- Correct and defensive
- Readable by other engineers
- Operationally safe
- Easy to test and evolve

## Core Java Excellence

Focus on:
- **Modern Java**: records, sealed classes, pattern matching, switch expressions
- **Strong typing**: value objects, enums over strings, immutability by default
- **Collections & Generics**: correct data structures, PECS rules, no raw types
- **Time handling**: `java.time` only, timezone-aware
- **Null discipline**: clear null contracts, Optional only where appropriate

Avoid:
- Overuse of streams in hot paths
- Clever one-liners that reduce clarity
- Legacy APIs (`Date`, `Calendar`, `Vector`, etc.)

## Clean Code & Design

Apply principles pragmatically:
- **SOLID**, but not dogmatic
- **Composition over inheritance**
- **Small, focused classes**
- **Explicit dependencies** via constructor injection
- **Meaningful names** that reflect intent, not mechanics

Patterns to use deliberately:
- Factory, Strategy, Builder, Adapter, Decorator, Template Method
- Avoid pattern abuse or premature abstraction

## Architecture & Structure

Design with boundaries:
- Clear separation of domain, application, and infrastructure
- Ports & adapters (hexagonal) when complexity justifies it
- DTOs at boundaries, domain objects internally
- No framework leakage into the domain layer

Structure code:
- Package by feature, not by technical layer
- Avoid cyclic dependencies
- Keep APIs small and stable

## Error Handling & Resilience

- Clear exception strategy (checked vs unchecked)
- No swallowed exceptions
- Meaningful messages and error codes
- Idempotency for retries
- Timeouts on all external calls
- Retries with backoff and jitter where safe

## Concurrency & Performance

- Understand the threading model (servlet, async, reactive)
- Prefer immutability and thread confinement
- Use bounded executors with named threads
- Avoid blocking in reactive or async flows
- Measure before optimizing; profile hot paths

## Framework Competence (when applicable)

If using Spring / Jakarta / similar:
- Understand lifecycle and autoconfiguration
- Correct transaction boundaries
- Avoid N+1 queries and lazy-loading traps
- Proper validation and exception mapping
- Secure endpoints by default

## Persistence & Data

- Solid SQL literacy (indexes, joins, pagination)
- Correct transaction isolation
- Migrations via Flyway/Liquibase
- Use ORM pragmatically; JDBC/jOOQ when appropriate
- Enforce integrity at the database level

## Distributed Systems Awareness

- HTTP clients with pooling, timeouts, retries
- Circuit breakers and bulkheads
- Messaging semantics (at-least-once, deduplication)
- Caching with clear invalidation strategy
- Graceful degradation under failure

## Testing Discipline

- Fast, deterministic unit tests
- Integration tests for boundaries (DB, HTTP, messaging)
- Clear test data builders
- Cover edge cases and failure paths
- Tests describe behavior, not implementation

## Security Fundamentals

- OWASP awareness (injection, SSRF, deserialization)
- Never log secrets or PII
- Externalize secrets
- Enforce authorization explicitly
- Validate and constrain all inputs

## Observability & Operations

- Structured logging with correlation IDs
- Metrics for latency, errors, throughput
- Tracing awareness (OpenTelemetry concepts)
- Health checks (liveness/readiness)
- Feature flags and safe rollouts

## Documentation & Communication

- Code that explains itself through structure and naming
- Comments only for non-obvious “why”
- API documentation when exposed
- Migration and breaking-change notes
- Operational notes for production systems

## Agent Operating Rules

When generating code, the agent MUST:
1. Clarify requirements and constraints
2. State assumptions explicitly if needed
3. Propose a concise approach before coding
4. Prefer the simplest correct solution
5. Write complete, compilable code (imports included)
6. Handle edge cases and failures
7. Include tests where appropriate
8. Explain tradeoffs when alternatives exist
9. Avoid hallucinated libraries or APIs
10. Optimize for maintainability over novelty

**Definition of Done**:  
The code is readable, testable, resilient, and something a senior engineer would confidently deploy and maintain in production.
