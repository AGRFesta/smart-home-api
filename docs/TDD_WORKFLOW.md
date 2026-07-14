# TDD Methodology (Test-Driven Development)

This project strictly follows TDD. You are not allowed to write tests and implementation at the same time. You must follow the incremental cycle (Phase 0 to Phase 3).

**GOLDEN RULE:** The only human gate is the **Phase 0 test list**. Once the list is approved, you run the RED → GREEN → REFACTOR loop **autonomously**: you execute the tests yourself, you judge every cycle against its **pre-declared expectation** (see Execution Harness), and you stop early only when an escalation condition is met. Never proceed past a cycle whose observed outcome does not match its pre-declared expectation.

## Test Writing Guidelines & Conventions
When generating or modifying tests, you must adhere to the following rules:
* **Behavior over Implementation:** Test the actual behavior and output of the component, not its internal implementation details.
* **Mocking Boundaries:** Use mocks and stubs ONLY for system boundaries (e.g., API calls, external services, databases, filesystem). **Never** mock internal logic or private methods. Rely on Dependency Injection.
* **Test Organization:** Prefer one test class per method under test (e.g., `FooServiceBarMethodTest.kt`) ONLY when the methods have significantly different setup, or when the class grows beyond ~400 lines. If the shared setup dominates, keep tests together or extract a base class.
* **Visual Structure (Arrange-Act-Assert):** The body of the test MUST be visually separated into three distinct blocks (e.g., using `// Given`, `// When`, `// Then` comments or blank lines) to clearly separate data setup, execution, and verification.
* **Explicit "Given" Phase:**
    * **Subject setup — always explicit:** Declare any value, mock behavior, or state that is the **primary subject** of the test directly in the test body, even if the same value is already provided as a default by an Object Mother, `init` block, or `@BeforeEach`. The subject must be visible at a glance.
    * **Non-subject setup — centralize, don't repeat:** Setup that is shared across multiple tests and is **not the subject** of any of them must be extracted to a single point (`init` block or `@BeforeEach`). Repeating it in every test body is noise that obscures what each test is actually about.

> ❗ **Given phase checklist — apply at every Phase 3 (Refactor):**
> 1. Does any `@BeforeEach` / `init` block contain setup that IS the subject of at least one test? → move it into that test body.
> 2. Does any test body repeat setup that is shared and NOT the subject of any test? → extract it to `@BeforeEach` / `init`.
> 3. Is the SUT construction repeated in every test? → move it to a field, unless constructor args vary per test.
> 4. Could a reader understand *what* this test is about from the Given block alone, without reading the rest of the class? → if not, something is in the wrong place.
* **Descriptive Naming:** Test names must clearly state the behavior being verified and the context (e.g., in Kotest use clear string descriptions like `"should return error when input is negative"`).
* **Assertions & Arrow `Either`:** * For collections or complex objects, wrap assertions with `withClue("context message") { ... }` instead of relying solely on `shouldBe` diff output.
    * When asserting on Arrow `Either` results, **never** just check if it is left or right. You must assert the exact type of the domain error using strongly typed assertions (e.g., `result.shouldBeLeft().shouldBeInstanceOf<AreaCreationFailure.NameAlreadyExists>()`).

---

## Test Infrastructure

### Test Levels
The project has three distinct test layers. Use the right one for the right scope:

| Level | Annotation / Base class | Scope | When to use |
|---|---|---|---|
| **Unit** | plain JUnit5 + MockK | Single class in isolation | Domain logic, value objects, pure functions |
| **MVC Slice** | `@WebMvcTest` + `@Import(SecurityConfig::class)` | Controller + Spring Security only | HTTP mapping, request validation, auth, error responses |
| **Persistence Slice** | extends `AbstractDaoJdbcImplTest` (`@JdbcTest`) | JDBC layer + real Postgres via Testcontainers | Outbound adapter (DAO) behaviour, SQL queries, error mapping |
| **Integration** | extends `AbstractIntegrationTest` | Full Spring context + real Postgres + real Redis | Verify wiring between all components. One test per flow covering the happy path. |

### Test Frameworks & Tools
- **Assertions:** [Kotest](https://kotest.io/) — `shouldBe`, `withClue { }`, `shouldBeRight`, `shouldBeLeft`.
- **Mocking:** [MockK](https://mockk.io/) for plain unit tests; [SpringMockK](https://github.com/Ninja-Squad/springmockk) (`@MockkBean`, `@SpykBean`) for slice and integration tests.
- **HTTP testing (integration):** [RestAssured](https://rest-assured.io/).
- **HTTP testing (slice):** Spring `MockMvc`.
- **Infrastructure:** [Testcontainers](https://testcontainers.com/).
- **Property-based testing:** [Kotest Property](https://kotest.io/docs/proptest/property-based-testing.html) — `checkAll`, `Arb`. Annotate the class/method with `@PropertyBasedTest`, use the shared `pbtConfig` (fixed seed, reproducible) and the domain `Arb`s in `core` test fixtures (`Arb.percentage()`, `Arb.temperature()`).

### Property-Based Tests (PBT): when and how

PBTs **complement, never replace** the example/table-based tests. The `pbt` tag is **excluded from pitest mutation analysis** (`excludedGroups = listOf("pbt")`), so the example tests remain the mutation oracle — every behaviour must still be pinned by at least one example test.

**When to add one.** Reach for a PBT only when all of these hold; otherwise an example test is enough:
- The subject is a **pure function, value object, or domain invariant** (no I/O, deterministic).
- The **input space is large** and example tests cannot exhaustively cover the boundaries (e.g. rounding/precision, ordering, ranges, round-trips).
- You can state an **invariant or contract** over that space — not just a single expected output.

**How it fits the cycle.** Two legitimate roles:
1. **RED-driving** — the property *is* the next behavioural increment: write it first, watch it fail (shrinking yields the minimal counterexample), then make it GREEN. Follows Phase 1→2 normally.
2. **Green-on-arrival guard** — added in **Phase 3** over the most critical logic, after an example test already drove the implementation. For a PBT guard, being GREEN the moment you write it is **expected and acceptable** — it is the documented exception to the "a test that is already GREEN is a smell" rule (that rule governs the RED-ordered example list, not guards).

**Avoid the tautology trap.** ❗️ The oracle must **not restate the production formula** — a PBT that recomputes the implementation expression co-evolves with its bugs and verifies nothing. Instead either:
- use an **independent computation** (e.g. exact integer cross-multiplication to check a `BigDecimal` comparison), or
- assert a **one-directional contract** (soundness, monotonicity, round-trip, bounds) and pin the opposite direction with an example test.

---

## Execution Harness (On-the-Loop Mode)

The user supervises **outcomes** ("on the loop"), not individual phases ("in the loop"). The user still owns: the Phase 0 gate, git (commits, branches, pushes), PRs, and issue definitions. Everything else in the loop is yours — under these mechanical, auditable rules:

### Preflight (before the first cycle)
- If the Phase 0 list includes persistence-slice or integration tests, verify Docker is running (Testcontainers requires it) **before starting**. If it is not available, STOP and report — do not start the loop and burn cycles on infrastructural failures.

### Per-cycle test execution
- Run only the test class(es) relevant to the current cycle: `./gradlew test --tests "ClassName"`.
- The full suite runs **once**, at the Definition of Done — never per cycle.

### Pre-declared RED (the substitute for human confirmation)
- **Before** running a newly written test, state the exact expected failure: the failing test name and the failure kind — a specific assertion mismatch, or a `NotImplementedError` from a specific `TODO("...")`.
- A RED is valid **only if** the observed output matches the declaration.
  - A **compilation error is never a valid RED**.
  - A test failing for a **different reason** than declared is not a valid RED — diagnose before proceeding.
  - A test that is **GREEN on arrival** is the over-implementation smell (see Phase 2 diagnostic check): stop and resolve it, never silently cross it off.

### Audit trail
- After each cycle, report a one-line summary with the relevant evidence (observed RED reason / GREEN confirmation), so the user can audit any cycle after the fact without having gated it.

### Escalation conditions — STOP and return to the user when:
- A planned test **cannot be made RED as declared** after one diagnosis attempt (wrong-reason RED twice in a row, or GREEN on arrival that is not explained by simple over-implementation): the Phase 0 list is probably wrong — present an **amended list for re-approval**.
- A test looks **wrong as a spec** (its expectation contradicts the approved plan or the domain docs). Never bend a test to fit the implementation, and never bend the plan silently.
- A failure is **infrastructural** (Docker down, container startup, port clash, flaky environment): it is neither a RED nor a broken GREEN — report it.
- The **Definition of Done** fails in a way whose fix would require new behaviour not covered by the approved list.

---

## Phase 0: PLANNING (The Test List) — HUMAN GATE
1. Before writing any code, analyze the task and create a **strictly ordered bulleted list** of test cases you plan to write.
2. **Order by RED-ability, not by complexity.** The purpose of the ordering is that each
   test, *at the moment it is written*, can be observed failing (RED) for a genuine reason:
   it must exercise a behaviour the current production code does not yet provide, and require
   the **smallest possible new increment** of production code to turn GREEN.
   - The RED must always be an **executed test failure** — an assertion mismatch, or a
     `NotImplementedError` from a `TODO("...")` stub — **never a compilation error**. Writing
     the minimal production scaffolding needed to compile (stubs, `TODO("...")`) is part of
     arranging the RED, not a violation of it (consistent with Phase 1).
   - A test that is already GREEN the moment you write it is a smell: it either belongs
     earlier in the list, or an earlier step over-implemented (this is the same thing the
     Phase 2 *diagnostic check* catches — ordering prevents it, the check intercepts it).
   - Starting from degenerate cases (null/empty/validation) is a useful *heuristic* that
     often yields the cleanest first REDs and the smallest increments — but it is a means,
     not the rule. Where complexity ordering and RED-ability conflict, **RED-ability wins**.
   - When two behaviours are so coupled that a later test cannot be made RED in isolation,
     **say so explicitly in the plan** rather than forcing an artificial order.
3. **Batch proposals live here.** If some consecutive tests are near-identical micro-variants of the
   same increment shape (e.g. infrastructure failure → typed error mapping across the methods of one
   adapter), propose them **in the list, explicitly marked as a batch**. Batch approval happens at this
   gate — it is never inferred mid-loop.
4. **BARRIER — STOP AND ASK:** Present this list to the user and ask for approval. **Do not write any
   code** until the list is approved or amended. This is the **only** barrier in the workflow: once the
   list is approved, the RED → GREEN → REFACTOR loop below runs autonomously through the whole list,
   ending with the Definition of Done.

## Phase 1: RED (Writing ONE Single Test)
1. Pick ONLY the **first uncompleted test** from the Phase 0 list.
2. Write the code for this **SINGLE test only**. Do not write tests for the other items on the list yet, strictly following the **Test Writing Guidelines** above. Do not touch production code beyond the bare minimum required to make the test compile. If you use Kotlin's `TODO()`, **always provide a descriptive message** (e.g., `TODO("Implement validation for negative amount")`) so the test fails with a specific `NotImplementedError`, confirming the correct execution path was hit.
3. **Pre-declare the expected failure** (see Execution Harness), then run the test yourself, scoped to its class.
4. **Judge the RED yourself:** proceed to Phase 2 only if the observed failure matches the declaration.
   On a mismatch, diagnose and fix the *arrangement* of the RED (test setup, stub placement) — if it
   still mismatches, or the test is GREEN on arrival, follow the **escalation conditions**.
5. **Batch execution (micro-variants):** only for batches **approved in the Phase 0 list**. Write the
   batch, pre-declare the expected failure of **each** test, and verify them in one grouped run. A test
   that turns out GREEN or RED for the wrong reason inside the batch must be pulled out and re-run
   through the normal single-test cycle.

## Phase 2: GREEN (Minimal Implementation)
1. Write the production code to make **only that specific test pass**.
2. **Constraint:** Write *only* the simplest, minimal code necessary. Do not optimize, do not abstract, do not anticipate future test cases from your Phase 0 list.
   - Do **not** add `if/when` branches that are not exercised by the current test.
   - Do **not** propagate `Either` results through multiple cases if the current test only verifies one — use a hardcoded return or `TODO()` for untested branches.
   - **Diagnostic check:** If the *next* test on the Phase 0 list is GREEN on arrival in its own Phase 1, you over-implemented here. Stop, revert the excess, and re-introduce it only when its test demands it.
3. **Verify GREEN yourself:** run the scoped test class and confirm it passes. If it does not, iterate
   on the **implementation** — the test is the spec. If the test itself looks wrong, that is an
   escalation, not a test edit.

## Phase 3: REFACTOR & LOOP (Cleanup and Next Steps)
1. Once GREEN, analyze both the newly written production code **and** the test code. Refactor to eliminate duplication and ensure compliance with `docs/ARCHITECTURE.md`.
2. **Constraint (No Behavior Change):** During refactoring, you are **strictly forbidden** from adding new business logic, new validations, or new conditional branches. You can only restructure existing code to improve readability and remove duplication.
3. **Re-run the scoped test class(es)** touched by the refactoring and confirm they are still GREEN.
4. **LOOP:** Cross off the completed test from the Phase 0 list, report the cycle's one-line audit
   summary, announce the next test, and loop back to **Phase 1**. When the list is exhausted, run the
   **Definition of Done**.

## Definition of Done (end of flow)
Run all of these after the last cycle, before handing back to the user:
1. `./gradlew build` — full build, all modules, all tests (this is the only full-suite run of the flow).
2. `./gradlew detekt` — run it **explicitly** (do not assume `build` covers it) and leave it clean. If a
   finding can only be fixed by changing behaviour, escalate instead of fixing.
3. **Docs & changelog** per `CLAUDE.md` conventions: if an endpoint was added or changed, update
   `docs/api/<resource>.md`, the `API_INDEX.md` table, **and the Bruno collection** (`bruno/<resource>/`
   — one `.bru` file per endpoint, mirroring the existing folder structure); then add the
   `CHANGELOG.md` entry under `## [Unreleased]`.
4. **Final report to the user:** the completed test list with each cycle's outcome, any deviation from
   the approved plan, and the build/detekt results. The user takes it from here (git, PR).
