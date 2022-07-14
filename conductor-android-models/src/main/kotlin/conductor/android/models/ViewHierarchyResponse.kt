package conductor.android.models

import kotlinx.serialization.Serializable

@Serializable
data class ViewHierarchyResponse(
    val hierarchy: String,
)
