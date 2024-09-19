package org.agrfesta.sh.api.persistence.converters

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.agrfesta.sh.api.domain.devices.DeviceFeature

@Converter(autoApply = true)
class DeviceFeaturesSetConverter : AttributeConverter<Set<DeviceFeature>, Array<String>> {

    override fun convertToDatabaseColumn(attribute: Set<DeviceFeature>?): Array<String> = attribute
        ?.map { it.name }
        ?.toTypedArray()
        ?: emptyArray()

    override fun convertToEntityAttribute(dbData: Array<String>?): Set<DeviceFeature> = dbData
        ?.map { DeviceFeature.valueOf(it) }
        ?.toSet()
        ?: emptySet()

}
