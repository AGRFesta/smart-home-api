# Technical Specification: GET Endpoint for Area Heating Configuration

## Task Difficulty Assessment
**EASY** - Straightforward implementation following established patterns. The service method already exists, and we're just adding a GET endpoint with standard error handling.

## Technical Context

### Language & Framework
- **Language**: Kotlin (JVM 21)
- **Framework**: Spring Boot 3.x with Spring Web
- **Error Handling**: Arrow library (`Either<L, R>` monad for functional error handling)
- **Testing**: JUnit 5, Kotest assertions, MockK for mocking, Rest-Assured for integration tests

### Dependencies
- Spring Boot Starter Web
- Arrow Core (functional programming)
- Jackson (JSON serialization)
- Spring Security (authentication required on all endpoints)

## Implementation Approach

### 1. Endpoint Definition
Add a new GET endpoint to the existing `HeatingAreasController`:

```kotlin
@GetMapping("/{areaId}")
fun getHeatingSchedule(@PathVariable areaId: UUID): ResponseEntity<Any>
```

### 2. Service Integration
The service layer already has the required method:
```kotlin
fun findAreaSetting(areaId: UUID): Either<PersistenceFailure, AreaTemperatureSetting?>
```

This method returns:
- `Either.Right(AreaTemperatureSetting?)` - Success (nullable, can be null if no setting exists)
- `Either.Left(PersistenceFailure)` - Database/persistence error

### 3. Response Mapping

**Success (200 OK)**: Map `AreaTemperatureSetting` to `TemperatureSettings` DTO
```kotlin
data class TemperatureSettings(
    val defaultTemperature: Temperature,
    val temperatureSchedule: Collection<TemperatureInterval>
)
```

**Not Found (404)**: When `findAreaSetting` returns `null`
```kotlin
status(NOT_FOUND).body(MessageResponse("No heating schedule found for area '$areaId'!"))
```

**Internal Server Error (500)**: When `findAreaSetting` returns `Left(PersistenceFailure)`
```kotlin
status(INTERNAL_SERVER_ERROR).body(MessageResponse("Unable to retrieve setting for area '$areaId'!"))
```

### 4. Error Handling Pattern
Follow the established pattern used in POST and DELETE endpoints:
- Use `onLeft { }` to handle `Either.Left` cases
- Return early with appropriate HTTP status
- Log persistence failures
- Return success response at the end

## Source Code Structure Changes

### Files to Modify
1. **`src/main/kotlin/org/agrfesta/sh/api/controllers/HeatingAreasController.kt`**
   - Add `@GetMapping("/{areaId}")` endpoint method
   - Import `GetMapping` annotation if not already present

### Files to Create/Modify for Testing
2. **`src/test/kotlin/org/agrfesta/sh/api/controllers/HeatingAreasControllerUnitTest.kt`**
   - Add unit tests for GET endpoint:
     - Returns 401 when auth is missing
     - Returns 401 when token is empty
     - Returns 401 when token is invalid
     - Returns 404 when no setting exists for the area
     - Returns 500 on persistence failure
     - Returns 200 with correct DTO when setting exists

3. **`src/test/kotlin/org/agrfesta/sh/api/controllers/HeatingAreasControllerIntegrationTest.kt`**
   - Add integration tests for GET endpoint:
     - Returns 404 when no setting exists
     - Returns 200 with complete TemperatureSettings when setting exists

## Data Model / API / Interface Changes

### API Changes
**New Endpoint**: `GET /heating/areas/{areaId}`
- **Path Parameter**: `areaId` (UUID)
- **Authentication**: Required (Bearer token)
- **Content-Type**: `application/json`

### Response Schemas

**Success Response (200)**:
```json
{
  "defaultTemperature": 21.5,
  "temperatureSchedule": [
    {
      "temperature": 18.0,
      "startTime": "22:00",
      "endTime": "06:00"
    },
    {
      "temperature": 22.0,
      "startTime": "06:00",
      "endTime": "08:00"
    }
  ]
}
```

**Not Found Response (404)**:
```json
{
  "message": "No heating schedule found for area '{areaId}'!"
}
```

**Server Error Response (500)**:
```json
{
  "message": "Unable to retrieve setting for area '{areaId}'!"
}
```

**Unauthorized Response (401)** - handled by SecurityConfig:
```json
{
  "message": "Missing Authorization header" | "Empty token" | "Invalid token"
}
```

## Verification Approach

### Unit Tests
- **Framework**: Spring MockMvc with `@WebMvcTest`
- **Mocking**: Use `@MockkBean` for repository mocks
- **Assertions**: Kotest matchers (`shouldBe`)
- **Coverage**: All response scenarios (200, 404, 500, 401)

### Integration Tests
- **Framework**: Testcontainers with PostgreSQL and Redis
- **Client**: Rest-Assured for HTTP calls
- **Verification**: Database state validation using `TemperatureSettingsDao`
- **Coverage**: Real database scenarios

### Test Execution
Run tests using Gradle:
```bash
./gradlew test
```

### Manual Verification (Optional)
1. Start the application
2. Create a heating schedule: `POST /heating/areas/{areaId}`
3. Retrieve it: `GET /heating/areas/{areaId}`
4. Verify response matches created data
5. Delete it: `DELETE /heating/areas/{areaId}`
6. Try to retrieve again: should return 404

## Implementation Notes

### Consistency with Existing Code
- Follow the exact error message format used in POST/DELETE endpoints
- Use the same logging approach for persistence failures
- Maintain the same authentication pattern (handled by SecurityConfig)
- Use `status()` builder from `ResponseEntity`
- Return `ResponseEntity<Any>` to allow different response types

### Edge Cases Handled
- No setting exists for the area (returns 404, not empty schedule)
- Database connection issues (returns 500)
- Invalid UUID format (handled by Spring's type conversion)
- Missing/invalid authentication (handled by SecurityConfig)

### No Breaking Changes
This is a new endpoint addition with no modifications to existing endpoints or data structures.
