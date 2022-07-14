package conductor.android.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfoResponse(
    val widthPixels: Int,
    val heightPixels: Int,
)
