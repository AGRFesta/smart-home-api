package org.agrfesta.sh.api.persistence.jdbc.adapters

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import org.agrfesta.sh.api.CleanSmartHomeDatabase
import org.agrfesta.sh.api.TestContainersConfig
import org.agrfesta.sh.api.persistence.jdbc.repositories.ActuatorsAssignmentsJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasWithDevicesJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.PropertyJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsAssignmentsJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsHistoryDataJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.TemperatureIntervalRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.TemperatureSettingRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.context.annotation.Import

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    TestContainersConfig::class,
    // DAO implementations
    AreasJdbcAdapter::class,
    DevicesJdbcAdapter::class,
    SensorsAssignmentsJdbcAdapter::class,
    ActuatorsAssignmentsJdbcAdapter::class,
    TemperatureSettingsJdbcAdapter::class,
    AreasWithDevicesRepositoryJdbcImpl::class,
    PropertyJdbcAdapter::class,
    SensorsHistoryDataJdbcAdapter::class,
    // Repositories
    AreasJdbcRepository::class,
    DevicesJdbcRepository::class,
    SensorsAssignmentsJdbcRepository::class,
    ActuatorsAssignmentsJdbcRepository::class,
    TemperatureSettingRepository::class,
    TemperatureIntervalRepository::class,
    AreasWithDevicesJdbcRepository::class,
    PropertyJdbcRepository::class,
    SensorsHistoryDataJdbcRepository::class
)
@CleanSmartHomeDatabase
abstract class AbstractJdbcAdapterTest {

    @SpykBean protected lateinit var areasRepo: AreasJdbcRepository
    @SpykBean protected lateinit var devicesRepo: DevicesJdbcRepository
    @SpykBean protected lateinit var sensorsAssignmentsRepo: SensorsAssignmentsJdbcRepository
    @SpykBean protected lateinit var actuatorsAssignmentsRepo: ActuatorsAssignmentsJdbcRepository
    @SpykBean protected lateinit var tempSettingsRepo: TemperatureSettingRepository
    @SpykBean protected lateinit var tempIntervalsRepo: TemperatureIntervalRepository
    @SpykBean protected lateinit var areasWithDevicesRepo: AreasWithDevicesJdbcRepository
    @SpykBean protected lateinit var propertyRepo: PropertyJdbcRepository
    @SpykBean protected lateinit var historyDataRepository: SensorsHistoryDataJdbcRepository

    @MockkBean protected lateinit var timeProvider: TimeProvider

}
