package conductor.android.models

import kotlinx.serialization.Serializable

@Serializable
data class TapRequest(
    val x: Int,
    val y: Int,
)
