package org.agrfesta.sh.api.cache.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.agrfesta.sh.api.core.application.ports.outbounds.Cache
import org.agrfesta.sh.api.core.domain.failures.BatteryLookupError
import org.agrfesta.sh.api.domain.aSensor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate

class DeviceBatteryCacheAdapterTest : AbstractCacheAdapterTest() {

    @Autowired private lateinit var sut: DeviceBatteryCacheAdapter
    @Autowired private lateinit var cache: Cache
    @Autowired private lateinit var stringRedisTemplate: StringRedisTemplate

    @Test fun `findBy() returns Right(null) when no battery is cached for the device`() {
        val device = aSensor()

        sut.findBy(device).shouldBeRight().shouldBeNull()
    }

    @Test fun `findBy() returns the cached battery level for the device`() {
        val device = aSensor()
        cache.set(device.getBatteryKey(), "88")

        sut.findBy(device).shouldBeRight() shouldBe 88
    }

    @Test fun `findBy() returns Right(null) when another device has a battery value but this one does not`() {
        val device = aSensor()
        val otherDevice = aSensor()
        cache.set(otherDevice.getBatteryKey(), "88")

        sut.findBy(device).shouldBeRight().shouldBeNull()
    }

    @Test fun `findBy() returns Left(BatteryLookupError) when cached value is not a valid int`() {
        val device = aSensor()
        cache.set(device.getBatteryKey(), "not-a-number")

        sut.findBy(device)
            .shouldBeLeft()
            .shouldBeInstanceOf<BatteryLookupError>()
    }

    @Test fun `save() stores the battery level at the device battery cache key`() {
        val device = aSensor()

        sut.save(device, 88).shouldBeRight()

        cache.get(device.getBatteryKey()).shouldBeRight() shouldBe "88"
    }

    @Test fun `save() sets a positive TTL on the battery key`() {
        val device = aSensor()

        sut.save(device, 88).shouldBeRight()

        val ttlSeconds = stringRedisTemplate.getExpire(device.getBatteryKey())
        withClue("expected a positive, bounded TTL on the battery key, but was $ttlSeconds") {
            (ttlSeconds in 1..300) shouldBe true
        }
    }

    @Test fun `save() overwrites a previously stored battery level for the same device`() {
        val device = aSensor()
        sut.save(device, 50)

        sut.save(device, 88).shouldBeRight()

        cache.get(device.getBatteryKey()).shouldBeRight() shouldBe "88"
    }
}
