# TDD Methodology (Test-Driven Development)

This project strictly follows TDD. You are not allowed to write tests and implementation at the same time. You must follow the incremental cycle (Phase 0 to Phase 3) respecting the established barriers.

**GOLDEN RULE:** Never proceed to the next phase without explicit user approval. To save execution resources and avoid environment issues, **never run tests automatically**. Always ask the user to run them or ask for explicit permission to execute them yourself.

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

---

## Phase 0: PLANNING (The Test List)
1. Before writing any code, analyze the task and create a **strictly ordered bulleted list** of test cases you plan to write.
2. Order the list logically: start with the absolute simplest degenerate case (e.g., null inputs, empty strings, validations), then progress to base success cases, and finally complex edge cases or external API faults.
3. **BARRIER - STOP AND ASK:** Present this list to the user and ask for approval. **Do not write any code** until the list is approved or amended.

## Phase 1: RED (Writing ONE Single Test)
1. Pick ONLY the **first uncompleted test** from the Phase 0 list.
2. Write the code for this **SINGLE test only**. Do not write tests for the other items on the list yet, strictly following the **Test Writing Guidelines** above. Do not touch production code beyond the bare minimum required to make the test compile. If you use Kotlin's `TODO()`, **always provide a descriptive message** (e.g., `TODO("Implement validation for negative amount")`) so the test fails with a specific `NotImplementedError`, confirming the correct execution path was hit.
3. **BARRIER - STOP AND ASK:** Ask the user: *"I have written the test for the first case. Could you please run it locally to verify it fails (RED) for the expected domain reason, or do you grant me permission to run it?"*
4. **Do not proceed** until the user confirms the single test is RED in the right way.

## Phase 2: GREEN (Minimal Implementation)
1. Once approved, write the production code to make **only that specific test pass**.
2. **Constraint:** Write *only* the simplest, minimal code necessary. Do not optimize, do not abstract, do not anticipate future test cases from your Phase 0 list.
3. **BARRIER - STOP AND ASK:** Ask the user: *"I have written the minimal implementation. Could you please run the tests to verify they pass (GREEN)?"*
4. **Do not proceed** until the user confirms the tests are GREEN.

## Phase 3: REFACTOR & LOOP (Cleanup and Next Steps)
1. Once GREEN, analyze both the newly written production code **and** the test code. Refactor to eliminate duplication and ensure compliance with `docs/ARCHITECTURE.md`.
2. **Constraint (No Behavior Change):** During refactoring, you are **strictly forbidden** from adding new business logic, new validations, or new conditional branches. You can only restructure existing code to improve readability and remove duplication.
3. Remind the user to run the test suite after modifications.
4. **LOOP:** Once refactoring is approved, explicitly **cross off the completed test** from the Phase 0 list, announce the next test on the list, and loop back to **Phase 1** for that specific test.
