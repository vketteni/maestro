package ios.hierarchy

import com.google.gson.annotations.SerializedName

data class Frame(
    @SerializedName("Width") val Width: Float,
    @SerializedName("Height") val Height: Float,
    @SerializedName("Y") val Y: Float,
    @SerializedName("X") val X: Float
)