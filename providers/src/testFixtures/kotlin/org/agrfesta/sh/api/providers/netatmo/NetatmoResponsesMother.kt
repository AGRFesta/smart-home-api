package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.agrfesta.test.mothers.aRandomUniqueString

fun ObjectMapper.anEmptyHomeData(): JsonNode = readTree("""
        {
          "body": {
            "homes": [
              {
                "rooms": [],
                "modules": [],
                "temperature_control_mode": "heating",
                "therm_mode": "schedule",
                "therm_setpoint_default_duration": 180,
                "cooling_mode": "schedule",
                "schedules": []
              }
            ]
          },
          "status": "ok",
          "time_exec": 0.036750078201293945,
          "time_server": 1739097155
        }
    """.trimIndent())

fun ObjectMapper.aHomeData(
    name: String = aRandomUniqueString(),
    homeId: String = aRandomUniqueString(),
    roomId: String = aRandomUniqueString(),
    deviceId: String = aRandomUniqueString(),
    userId: String = aRandomUniqueString()
): JsonNode = readTree("""
        {
          "body": {
            "homes": [
              {
                "id": "$homeId",
                "name": "home-name",
                "altitude": 107,
                "coordinates": [],
                "country": "IT",
                "timezone": "Europe/Rome",
                "rooms": [
                  {
                    "id": "$roomId",
                    "name": "room-name",
                    "type": "dining_room",
                    "module_ids": [
                      "$deviceId"
                    ]
                  }
                ],
                "modules": [
                  {
                    "id": "$deviceId",
                    "type": "BNS",
                    "name": "$name",
                    "setup_date": 1733911388,
                    "room_id": "$roomId"
                  }
                ],
                "temperature_control_mode": "heating",
                "therm_mode": "schedule",
                "therm_setpoint_default_duration": 180,
                "cooling_mode": "schedule",
                "schedules": []
              }
            ],
            "user": {
              "email": "a@mail.com",
              "language": "it",
              "locale": "it-IT",
              "feel_like_algorithm": 0,
              "unit_pressure": 0,
              "unit_system": 0,
              "unit_wind": 0,
              "id": "$userId"
            }
          },
          "status": "ok",
          "time_exec": 0.036750078201293945,
          "time_server": 1739097155
        }
    """.trimIndent())

fun ObjectMapper.aHomeStatus(
    homeId: String = aRandomUniqueString(),
    roomId: String = aRandomUniqueString(),
    deviceId: String = aRandomUniqueString()
): JsonNode = readTree("""
        {
          "status": "ok",
          "time_server": 1739100358,
          "body": {
            "home": {
              "id": "$homeId",
              "rooms": [
                {
                  "id": "$roomId",
                  "reachable": true,
                  "anticipating": false,
                  "heating_power_request": 0,
                  "open_window": false,
                  "humidity": 54,
                  "therm_measured_temperature": 17.7,
                  "therm_setpoint_temperature": 18,
                  "therm_setpoint_start_time": 1738519112,
                  "therm_setpoint_end_time": null,
                  "therm_setpoint_mode": "home"
                }
              ],
              "modules": [
                {
                  "id": "$deviceId",
                  "type": "BNS",
                  "firmware_revision": 37,
                  "wifi_strength": 51,
                  "boiler_valve_comfort_boost": false,
                  "boiler_status": true,
                  "cooler_status": false
                }
              ]
            }
          }
        }
    """.trimIndent())
