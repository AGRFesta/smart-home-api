# Technical Specification: Replace Temperature Type Alias with Value Class

## Task Complexity Assessment

**Difficulty**: **Medium**

**Rationale**:
- Moderate complexity with multiple integration points (domain, persistence, serialization)
- Requires careful handling of BigDecimal operations and scale normalization
- Need to verify behavior across database round-trips and JSON serialization
- Multiple files to modify but pattern is straightforward
- Some edge cases around JDBC and Jackson integration need testing
- Risk of breaking existing tests if equality semantics change unexpectedly

## Technical Context

### Language & Dependencies
- **Language**: Kotlin 1.9+ (JVM target 21)
- **Framework**: Spring Boot 3.x with Spring JDBC
- **Serialization**: Jackson (jackson-module-kotlin)
- **Persistence**: JDBC with NamedParameterJdbcTemplate, PostgreSQL database
- **Testing**: JUnit 5, Kotest assertions, Testcontainers
- **Build**: Gradle with Kotlin DSL

### Current Implementation

**Location**: `src/main/kotlin/org/agrfesta/sh/api/domain/commons/DataTypes.kt`

```kotlin
typealias Temperature = BigDecimal
```

**Problems**:
1. **Scale-dependent equality**: `BigDecimal("1.0") != BigDecimal("1")` but `compareTo() == 0`
2. **No type safety**: Any BigDecimal can be assigned to Temperature
3. **Test failures**: Kotest's `shouldBe` uses `equals()`, causing false negatives
4. **Database round-trips**: May alter scale, breaking equality checks
5. **JSON deserialization**: Different scale representations from API requests

### Reference Implementation

The codebase has a similar wrapper for `Percentage` at:
`src/main/kotlin/org/agrfesta/sh/api/domain/commons/Percentage.kt`

Key observations:
- Implemented as **data class** (not value class)
- Has validation logic in `init` block
- Provides multiple constructors (String, BigDecimal)
- Uses `stripTrailingZeros()` for normalization
- Has conversion methods (`toHundreds()`, `asText()`)

**Note**: Temperature should use **value class** instead of data class for zero runtime overhead, as it has no validation requirements.

## Implementation Approach

### 1. Core Value Class Design

Replace the typealias with a Kotlin inline value class:

```kotlin
@JvmInline
value class Temperature(val value: BigDecimal) : Comparable<Temperature> {
    
    constructor(temperature: String) : this(
        BigDecimal(temperature).stripTrailingZeros()
    )
    
    constructor(temperature: Double) : this(
        BigDecimal.valueOf(temperature).stripTrailingZeros()
    )
    
    constructor(temperature: Int) : this(
        BigDecimal(temperature)
    )
    
    override fun compareTo(other: Temperature): Int = 
        this.value.compareTo(other.value)
    
    operator fun plus(other: Temperature): Temperature = 
        Temperature(this.value + other.value)
    
    operator fun minus(other: Temperature): Temperature = 
        Temperature(this.value - other.value)
    
    operator fun times(other: Temperature): Temperature = 
        Temperature(this.value * other.value)
    
    operator fun times(factor: BigDecimal): Temperature = 
        Temperature(this.value * factor)
    
    operator fun div(divisor: BigDecimal): Temperature = 
        Temperature(this.value / divisor)
    
    operator fun unaryMinus(): Temperature = 
        Temperature(-this.value)
}
```

**Design Decisions**:
- **Value class**: Zero runtime overhead (inlined to BigDecimal at runtime)
- **Scale normalization**: All constructors use `stripTrailingZeros()` except Int (already normalized)
- **Comparable interface**: Enables natural ordering and range operations
- **Arithmetic operators**: Support common temperature calculations
- **No validation**: Unlike Percentage, Temperature has no inherent range restrictions (can be negative, very hot, etc.)

### 2. Extension Function Updates

**Current**: `Collection<Temperature>.average()` returns `Temperature?`

**Update Required**: Wrap result in Temperature value class

```kotlin
fun Collection<Temperature>.average(): Temperature? {
    if (isEmpty()) return null
    val average = fold(BigDecimal.ZERO) { acc, temp -> acc + temp.value }
        .divide(BigDecimal(size), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return Temperature(average)
}
```

**Changes**:
- Access `.value` property when accumulating
- Wrap result in `Temperature()` constructor
- Maintain existing scale normalization logic

### 3. Test Factory Updates

**Location**: `src/test/kotlin/org/agrfesta/test/mothers/BaseMothers.kt`

**Current**:
```kotlin
fun aRandomTemperature(scale: Int = 10): Temperature = 
    BigDecimal(Random.nextDouble(from = -100.0, until = 100.0))
        .setScale(scale, RoundingMode.CEILING)
        .stripTrailingZeros()
```

**Update Required**: Wrap result in Temperature constructor

```kotlin
fun aRandomTemperature(scale: Int = 10): Temperature = 
    Temperature(
        BigDecimal(Random.nextDouble(from = -100.0, until = 100.0))
            .setScale(scale, RoundingMode.CEILING)
    )
```

**Note**: `stripTrailingZeros()` now happens in Temperature constructor, no need to call it here.

### 4. Domain Model Updates

**Files** (No changes needed - automatic compatibility):
- `src/main/kotlin/org/agrfesta/sh/api/domain/AreaTemperatureSetting.kt`
- `src/main/kotlin/org/agrfesta/sh/api/domain/commons/ThermoHygroData.kt`

**Reason**: These files only declare Temperature as property types. Value class wrapping is transparent.

### 5. BigDecimal Operations Updates

**Location**: `src/main/kotlin/org/agrfesta/sh/api/domain/commons/AbsoluteHumidity.kt`

**Current**: Uses `temperature.add()`, `temperature.multiply()`, etc.

**Update Required**: Access `.value` property for BigDecimal operations

Example changes:
```kotlin
// Before
val temperatureKelvin = temperature.add(shiftForKelvin)
val exponent = b.multiply(temperature).divide(temperature.add(c), mc)

// After
val temperatureKelvin = temperature.value.add(shiftForKelvin)
val exponent = b.multiply(temperature.value).divide(temperature.value.add(c), mc)
```

### 6. JDBC Repository Updates

**Files**:
- `src/main/kotlin/org/agrfesta/sh/api/persistence/jdbc/repositories/TemperatureSettingRepository.kt`
- `src/main/kotlin/org/agrfesta/sh/api/persistence/jdbc/repositories/TemperatureIntervalRepository.kt`

**Current Pattern**:
```kotlin
// Reading
defaultTemperature = rs.getBigDecimal("default_temperature")

// Writing
"defaultTemperature" to setting.defaultTemperature
```

**Update Required**: Wrap reads, unwrap writes

```kotlin
// Reading
defaultTemperature = Temperature(rs.getBigDecimal("default_temperature"))

// Writing
"defaultTemperature" to setting.defaultTemperature.value
```

**Critical**: The `getBigDecimal()` may return values with varying scales from database. The Temperature constructor will normalize this automatically via `stripTrailingZeros()`.

### 7. Jackson Serialization

**Strategy**: Test if automatic serialization works, add custom serializer only if needed.

**Default Behavior**: Jackson's Kotlin module may serialize value class's `value` property automatically.

**If Custom Serialization Needed**:

Create serializer/deserializer:
```kotlin
class TemperatureSerializer : JsonSerializer<Temperature>() {
    override fun serialize(value: Temperature, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeNumber(value.value)
    }
}

class TemperatureDeserializer : JsonDeserializer<Temperature>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Temperature {
        return Temperature(p.decimalValue)
    }
}
```

Register with Jackson:
```kotlin
@JsonSerialize(using = TemperatureSerializer::class)
@JsonDeserialize(using = TemperatureDeserializer::class)
@JvmInline
value class Temperature(val value: BigDecimal) : Comparable<Temperature> { ... }
```

**Decision**: Implement only if integration tests fail with default serialization.

## Source Code Structure Changes

### Files to Modify

1. **`src/main/kotlin/org/agrfesta/sh/api/domain/commons/DataTypes.kt`**
   - Replace typealias with value class
   - Update `Collection<Temperature>.average()` extension

2. **`src/test/kotlin/org/agrfesta/test/mothers/BaseMothers.kt`**
   - Update `aRandomTemperature()` factory

3. **`src/main/kotlin/org/agrfesta/sh/api/domain/commons/AbsoluteHumidity.kt`**
   - Update BigDecimal operations to use `.value`

4. **`src/main/kotlin/org/agrfesta/sh/api/persistence/jdbc/repositories/TemperatureSettingRepository.kt`**
   - Wrap JDBC reads with Temperature constructor
   - Unwrap writes with `.value`

5. **`src/main/kotlin/org/agrfesta/sh/api/persistence/jdbc/repositories/TemperatureIntervalRepository.kt`**
   - Wrap JDBC reads with Temperature constructor
   - Unwrap writes with `.value`

### Files to Create (If Needed)

6. **`src/main/kotlin/org/agrfesta/sh/api/serialization/TemperatureSerializers.kt`** (conditional)
   - Only if Jackson doesn't handle value class automatically

### Files Unchanged (Automatic Compatibility)

- Domain models: `AreaTemperatureSetting.kt`, `ThermoHygroData.kt`, etc.
- Persistence entities: `TemperatureSettingEntity.kt`, `TemperatureIntervalEntity.kt`
- Controllers: Transparent through Jackson
- Most test files: Constructors provide compatibility

## Data Model / API / Interface Changes

### Type Signature Changes

**Before**: `Temperature` is `BigDecimal`

**After**: `Temperature` is value class wrapping `BigDecimal`

### API Compatibility

**JSON Serialization** (assuming default Jackson behavior works):
- **Input**: `{"defaultTemperature": 20.5}` → deserializes to `Temperature(BigDecimal("20.5"))`
- **Output**: `Temperature(BigDecimal("20.5"))` → serializes to `{"defaultTemperature": 20.5}`
- **No breaking changes**: JSON schema remains identical

**Database Schema**:
- No changes to database schema
- Column types remain `NUMERIC` or `DECIMAL`
- Values stored and retrieved identically

### Behavior Changes

**Equality Semantics**:
- **Before**: `Temperature("1.0") != Temperature("1")` (scale-dependent)
- **After**: `Temperature("1.0") == Temperature("1")` (scale-normalized)

**Comparison Operations**:
- Both before and after use `compareTo()` semantics
- No change in ordering behavior

## Verification Approach

### Unit Tests

1. **New Test File**: `src/test/kotlin/org/agrfesta/sh/api/domain/commons/TemperatureTest.kt`

Add test cases:
```kotlin
@TestFactory
fun `temperature equality is scale-independent`() = listOf(
    Temperature("1.0") to Temperature("1"),
    Temperature("20.00") to Temperature("20"),
    Temperature("21.5") to Temperature("21.50"),
    Temperature("-5.0") to Temperature("-5")
).map { (temp1, temp2) ->
    dynamicTest("$temp1 should equal $temp2") {
        temp1 shouldBe temp2
    }
}

@TestFactory
fun `temperature arithmetic operations`() = listOf(
    (Temperature("10") + Temperature("5")) to Temperature("15"),
    (Temperature("20.5") - Temperature("10.2")) to Temperature("10.3"),
    (Temperature("5") * BigDecimal("2")) to Temperature("10"),
    (-Temperature("10")) to Temperature("-10")
).map { (result, expected) ->
    dynamicTest("$result should equal $expected") {
        result shouldBe expected
    }
}

@TestFactory
fun `temperature comparison operations`() = listOf(
    Triple(Temperature("10"), Temperature("20"), -1),
    Triple(Temperature("20"), Temperature("10"), 1),
    Triple(Temperature("15.0"), Temperature("15"), 0)
).map { (temp1, temp2, expected) ->
    dynamicTest("$temp1 compareTo $temp2 should be $expected") {
        temp1.compareTo(temp2) shouldBe expected
    }
}
```

**Note**: The existing test file already has average calculation tests that should continue to pass.

2. **Verify Existing Tests Pass**

Run existing test suite to ensure no regressions:
```bash
./gradlew test
```

**Expected**: All existing tests should pass, especially:
- `TemperatureTest.averageCalculations()` (already exists)
- Integration tests in `HeatingAreasControllerIntegrationTest`

### Integration Tests

**Database Round-Trip Test**:

Add to `HeatingAreasControllerIntegrationTest`:
```kotlin
@Test 
fun `temperature equality persists through database round-trip`() {
    val original = Temperature("20.0")
    val area = anArea()
    areasDao.save(area)
    val setting = anAreaTemperatureSetting(
        areaId = area.uuid,
        defaultTemperature = original
    )
    
    tempSettingsDao.save(setting)
    val retrieved = tempSettingsDao.findAreaSetting(area.uuid)!!
    
    retrieved.defaultTemperature shouldBe original
    retrieved.defaultTemperature shouldBe Temperature("20")
}
```

**JSON Serialization Test**:

Add to `HeatingAreasControllerIntegrationTest`:
```kotlin
@Test 
fun `temperature with different scales deserialize to equal values`() {
    val area = anArea()
    areasDao.save(area)
    
    // Create with "20.0"
    given()
        .contentType(ContentType.JSON)
        .authenticated()
        .body("""{"defaultTemperature": 20.0, "temperatureSchedule": []}""")
        .post("/heating/areas/${area.uuid}")
        .then()
        .statusCode(201)
    
    val retrieved = tempSettingsDao.findAreaSetting(area.uuid)!!
    retrieved.defaultTemperature shouldBe Temperature("20")
    retrieved.defaultTemperature shouldBe Temperature("20.00")
}
```

### Manual Verification

**Test Commands** (Kotlin/Gradle standard):
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "TemperatureTest"

# Run integration tests
./gradlew test --tests "*IntegrationTest"

# Build project
./gradlew build
```

**Verification Checklist**:
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Database round-trip preserves equality
- [ ] JSON deserialization normalizes scales
- [ ] Arithmetic operations work correctly
- [ ] Comparison operations work correctly
- [ ] No compilation errors
- [ ] Build succeeds

## Risk Assessment

### Low Risks
- **Value class runtime behavior**: Well-documented Kotlin feature, zero overhead guarantee
- **Domain model changes**: Transparent due to value class inlining
- **Test factory updates**: Straightforward wrapper addition

### Medium Risks
- **JDBC integration**: Need to verify that wrapping/unwrapping doesn't cause issues
  - *Mitigation*: Add explicit database round-trip tests
- **Jackson serialization**: May need custom serializer if default doesn't work
  - *Mitigation*: Test early, add custom serializer if needed
- **BigDecimal operations**: Need to update all usages to access `.value`
  - *Mitigation*: Compilation will catch all missing `.value` accesses

### High Risks
- None identified

## Migration Strategy

### Phase 1: Core Implementation
1. Implement Temperature value class in `DataTypes.kt`
2. Update `Collection<Temperature>.average()` extension
3. Update `aRandomTemperature()` factory
4. Add unit tests for Temperature value class

### Phase 2: Integration Updates
5. Update `AbsoluteHumidity` BigDecimal operations
6. Update JDBC repositories (wrap reads, unwrap writes)
7. Test Jackson serialization, add custom serializer if needed

### Phase 3: Verification
8. Run full test suite
9. Add database round-trip integration tests
10. Add JSON serialization integration tests
11. Verify all existing tests pass

### Phase 4: Edge Case Testing
12. Test edge cases (negative temps, very large/small values, zero)
13. Test comparison operations in production-like scenarios
14. Verify no performance regression (value class should have zero overhead)

## Success Criteria

- [ ] All existing tests pass without modification to assertions
- [ ] `Temperature("1.0") == Temperature("1")` returns `true`
- [ ] `Temperature("20.00") == Temperature("20")` returns `true`
- [ ] Database round-trip maintains equality regardless of scale
- [ ] JSON serialization/deserialization maintains equality regardless of scale
- [ ] Arithmetic operations work correctly
- [ ] Comparison operations work correctly
- [ ] No compilation errors
- [ ] Build succeeds
- [ ] No runtime overhead (confirmed by value class inlining)

## Notes

### Alternative Approaches Considered

1. **Keep typealias, override equals() globally**: Not possible with typealiases
2. **Use data class like Percentage**: More overhead than value class, unnecessary for Temperature
3. **Normalize in repositories only**: Doesn't solve test and calculation issues
4. **Use compareTo() everywhere**: Requires changing all test assertions and is error-prone

### Decision Rationale

**Value class chosen because**:
- Zero runtime overhead (inlined to BigDecimal)
- Type safety (distinct type from BigDecimal)
- Structural equality (solves scale problem)
- Consistent with modern Kotlin idioms
- Minimal migration effort (constructors provide compatibility)

### Future Enhancements

Potential future additions to Temperature value class:
- Unit conversion methods (Celsius ↔ Fahrenheit ↔ Kelvin)
- Range validation (optional, context-dependent)
- Formatting methods (`toDisplayString()`, etc.)
- Mathematical functions (min, max, clamp, etc.)

These are not required for the current task but could be added later if needed.
