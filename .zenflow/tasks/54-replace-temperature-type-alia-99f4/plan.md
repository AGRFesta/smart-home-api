# Spec and build

## Configuration
- **Artifacts Path**: {@artifacts_path} → `.zenflow/tasks/{task_id}`

---

## Agent Instructions

Ask the user questions when anything is unclear or needs their input. This includes:
- Ambiguous or incomplete requirements
- Technical decisions that affect architecture or user experience
- Trade-offs that require business context

Do not make assumptions on important decisions — get clarification first.

If you are blocked and need user clarification, mark the current step with `[!]` in plan.md before stopping.

---

## Workflow Steps

### [x] Step: Technical Specification
<!-- chat-id: b9a1b14f-35df-4bdc-9a95-e462713ba783 -->

Assess the task's difficulty, as underestimating it leads to poor outcomes.
- easy: Straightforward implementation, trivial bug fix or feature
- medium: Moderate complexity, some edge cases or caveats to consider
- hard: Complex logic, many caveats, architectural considerations, or high-risk changes

Create a technical specification for the task that is appropriate for the complexity level:
- Review the existing codebase architecture and identify reusable components.
- Define the implementation approach based on established patterns in the project.
- Identify all source code files that will be created or modified.
- Define any necessary data model, API, or interface changes.
- Describe verification steps using the project's test and lint commands.

Save the output to `{@artifacts_path}/spec.md` with:
- Technical context (language, dependencies)
- Implementation approach
- Source code structure changes
- Data model / API / interface changes
- Verification approach

If the task is complex enough, create a detailed implementation plan based on `{@artifacts_path}/spec.md`:
- Break down the work into concrete tasks (incrementable, testable milestones)
- Each task should reference relevant contracts and include verification steps
- Replace the Implementation step below with the planned tasks

Rule of thumb for step size: each step should represent a coherent unit of work (e.g., implement a component, add an API endpoint, write tests for a module). Avoid steps that are too granular (single function).

Important: unit tests must be part of each implementation task, not separate tasks. Each task should implement the code and its tests together, if relevant.

Save to `{@artifacts_path}/plan.md`. If the feature is trivial and doesn't warrant this breakdown, keep the Implementation step below as is.

---

### [x] Step: Implement Temperature Value Class and Core Extensions
<!-- chat-id: 6cb0b2e0-d6a7-4c37-ace0-8730517298bd -->

Implement the core Temperature value class in `DataTypes.kt` with scale normalization, arithmetic operators, and update the `average()` extension function.

**Changes**:
- Replace `typealias Temperature = BigDecimal` with `@JvmInline value class Temperature`
- Add constructors: String, Double, Int (with stripTrailingZeros normalization)
- Implement `Comparable<Temperature>` interface
- Add arithmetic operators: `plus`, `minus`, `times`, `div`, `unaryMinus`
- Update `Collection<Temperature>.average()` to access `.value` and wrap result

**Unit Tests**:
- Add tests for scale-independent equality (e.g., `Temperature("1.0") == Temperature("1")`)
- Add tests for arithmetic operations
- Add tests for comparison operations
- Verify existing average calculation tests still pass

**Verification**:
- Run: `./gradlew test --tests "TemperatureTest"`
- Confirm no compilation errors in DataTypes.kt

---

### [x] Step: Update Test Factory and Domain Calculations
<!-- chat-id: e607f9e0-8e23-4f44-b815-6ce1f44a2238 -->

Update the test factory function and domain classes that perform BigDecimal operations on Temperature.

**Changes**:
- `src/test/kotlin/org/agrfesta/test/mothers/BaseMothers.kt`: Update `aRandomTemperature()` to wrap result in Temperature constructor
- `src/main/kotlin/org/agrfesta/sh/api/domain/commons/AbsoluteHumidity.kt`: Update BigDecimal operations to access `temperature.value`

**Verification**:
- Run: `./gradlew test --tests "AbsoluteHumidityTest"`
- Verify test factories compile and work correctly

---

### [x] Step: Update JDBC Repository Mappings
<!-- chat-id: 6cb0b2e0-d6a7-4c37-ace0-8730517298bd -->

Update JDBC repositories to wrap database reads and unwrap writes for the Temperature value class.

**Changes**:
- `src/main/kotlin/org/agrfesta/sh/api/persistence/jdbc/repositories/TemperatureSettingRepository.kt`:
  - Wrap `rs.getBigDecimal()` reads with `Temperature()` constructor
  - Unwrap writes by accessing `.value` property
- `src/main/kotlin/org/agrfesta/sh/api/persistence/jdbc/repositories/TemperatureIntervalRepository.kt`:
  - Same wrapping/unwrapping pattern

**Verification**:
- Run persistence-related tests
- Confirm repositories compile correctly

---

### [ ] Step: Integration Testing and Jackson Verification

Verify database round-trips, JSON serialization/deserialization, and full integration test suite.

**Integration Tests**:
- Add database round-trip test to verify Temperature equality persists through save/retrieve cycle
- Add JSON serialization test to verify different scales deserialize to equal values
- If Jackson fails to serialize value class automatically, implement custom TemperatureSerializer/TemperatureDeserializer

**Full Test Suite**:
- Run: `./gradlew test`
- Verify all existing tests pass without modification
- Verify new integration tests pass

**Success Criteria**:
- All tests pass
- Database round-trip: `Temperature("20.0")` saved and retrieved equals `Temperature("20")`
- JSON deserialization: `{"defaultTemperature": 20.0}` equals `Temperature("20")`

---

### [ ] Step: Final Verification and Report

Run complete test suite, verify build succeeds, and document the implementation.

**Final Verification**:
- Run: `./gradlew clean build`
- Verify no compilation errors
- Verify no test failures
- Verify no runtime overhead (value class inlining confirmed by successful build)

**Report**: Write to `{@artifacts_path}/report.md`:
- Summary of changes made
- Test results (all tests passing)
- Any issues encountered and how they were resolved
- Confirmation of success criteria met
