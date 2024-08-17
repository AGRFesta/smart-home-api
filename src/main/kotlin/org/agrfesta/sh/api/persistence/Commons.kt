package org.agrfesta.sh.api.persistence

data class PersistenceFailure(val exception: Exception): GetDeviceFailure, GetRoomFailure, RoomCreationFailure
