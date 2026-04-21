# TDD Methodology (Test-Driven Development)

This project strictly follows TDD. You are not allowed to write tests and implementation at the same time. You must follow the 3-phase cycle (Red, Green, Refactor) respecting the established barriers.

**GOLDEN RULE:** Never proceed to the next phase without explicit user approval. To save execution resources and avoid environment issues, **never run tests automatically**. Always ask the user to run them or ask for explicit permission to execute them yourself.

## Test Writing Guidelines & Conventions
When generating or modifying tests, you must adhere to the following rules:
* **Behavior over Implementation:** Test the actual behavior and output of the component, not its internal implementation details.
* **Mocking Boundaries:** Use mocks and stubs ONLY for system boundaries (e.g., API calls, external services, databases, filesystem). **Never** mock internal logic or private methods. Rely on Dependency Injection.
* **Test Organization:** Prefer one test class per method under test (e.g., `FooServiceBarMethodTest.kt`) ONLY when the methods have significantly different setup, or when the class grows beyond ~400 lines. If the shared setup dominates, keep tests together or extract a base class.
* **Explicit "Given" Phase:** You must explicitly declare any value, mock behavior, or state that is the primary subject of the test, even if that exact value is already provided as a default by an Object Mother, Factory, or Base Class.
* **Descriptive Naming:** Test names must clearly state the behavior being verified and the context (e.g., in Kotest use clear string descriptions like `"should return error when input is negative"`).
* **Assertions:** For collections or complex objects, wrap assertions with `withClue("context message") { ... }` instead of relying solely on `shouldBe` diff output. The clue should describe what is being compared, not repeat the assertion. For simple scalar values, `shouldBe` alone is sufficient.

---

## Test Infrastructure

### Test Levels
The project has three distinct test layers. Use the right one for the right scope:

| Level | Annotation / Base class | Scope | When to use |
|---|---|---|---|
| **Unit** | plain JUnit5 + MockK | Single class in isolation | Domain logic, value objects, pure functions |
| **MVC Slice** | `@WebMvcTest` + `@Import(SecurityConfig::class)` | Controller + Spring Security only | HTTP mapping, request validation, auth, error responses |
| **Persistence Slice** | extends `AbstractDaoJdbcImplTest` (`@JdbcTest`) | JDBC layer + real Postgres via Testcontainers, no full context | Outbound adapter (DAO) behaviour, SQL queries, error mapping |
| **Integration** | extends `AbstractIntegrationTest` | Full Spring context + real Postgres + real Redis via Testcontainers | Verify wiring between all components — use sparingly. One test per flow covering the happy path. Exceptions are allowed only for error scenarios that span multiple adapters simultaneously (e.g. DB + Redis + HTTP) and cannot be covered by any single slice test. |

### Test Frameworks & Tools
- **Assertions:** [Kotest](https://kotest.io/) — `shouldBe`, `withClue { }`, `shouldBeRight`, `shouldBeLeft` (via `kotest-assertions-arrow`).
- **Mocking:** [MockK](https://mockk.io/) for plain unit tests; [SpringMockK](https://github.com/Ninja-Squad/springmockk) (`@MockkBean`, `@SpykBean`) for slice and integration tests.
- **HTTP testing (integration):** [RestAssured](https://rest-assured.io/) — `given().authenticated().get("/path")`.
- **HTTP testing (slice):** Spring `MockMvc` — `mockMvc.perform(get("/path").authenticated())`.
- **Infrastructure:** [Testcontainers](https://testcontainers.com/) spins up real PostgreSQL and Redis containers for integration tests.

### Authentication Helpers
Auth test utilities live in `src/test/kotlin/.../controllers/Utils.kt`:
```kotlin
// MockMvc (MVC slice tests)
mockMvc.perform(get("/your-endpoint").authenticated())

// RestAssured (integration tests)
given().authenticated().get("/your-endpoint")
given().wrongAuthentication().get("/your-endpoint")  // expects 401
```

### Object Mother Pattern
Test data factories live in `src/test/kotlin/.../domain/` (e.g., `AreasMother.kt`, `DevicesMother.kt`) and `persistence/jdbc/entities/EntitiesMother.kt`. Each Mother provides factory functions with sensible random defaults:
```kotlin
fun anAreaDto(uuid: UUID = UUID.randomUUID(), name: String = aRandomUniqueString(), ...) = AreaDto(...)
```
Use Mothers to build test fixtures. Always override only the fields that are the subject of the specific test (see *Explicit "Given" Phase* rule above).

---

## Phase 1: RED (Writing the Test)
1. Analyze the task requirements. Take **small steps**: write the smallest possible test that defines a single requirement or bug fix.
2. Write **only** the necessary tests for the requested behavior, strictly following the **Test Writing Guidelines** above. Do not touch production code.
3. **BARRIER - STOP AND ASK:** Ask the user: *"I have written the tests. Could you please run them locally to verify they fail (RED), or do you grant me permission to run them? Let me know if they fail in the expected way so I can proceed with the minimal implementation."*
4. **Do not proceed** until the user confirms the tests are RED in the right way.

## Phase 2: GREEN (Minimal Implementation)
1. Once approved, write the production code.
2. **Constraint:** Write *only* the simplest, minimal code necessary to make the test pass. Do not optimize, do not abstract, do not anticipate future cases.
3. **BARRIER - STOP AND ASK:** Ask the user: *"I have written the minimal implementation. Could you please run the tests to verify they pass (GREEN), or do you grant me permission to run them? Let me know if everything is OK so I can proceed to refactoring."*
4. **Do not proceed** until the user confirms the tests are GREEN and gives approval.

## Phase 3: REFACTOR (Cleanup)
1. Once the GREEN code is approved, analyze both the newly written production code **and** the test code.
2. Eliminate duplication, improve variable names, extract methods if necessary, and ensure the code complies with `docs/ARCHITECTURE.md`.
3. **Remove redundant or obsolete tests** that may have been introduced during the process.
4. Remind the user to run the test suite after your modifications to ensure they remain GREEN.
5. Conclude by summarizing the refactoring changes made.
