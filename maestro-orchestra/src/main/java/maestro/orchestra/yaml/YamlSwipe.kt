package maestro.orchestra.yaml

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.json.JsonMapper
import maestro.SwipeDirection
import maestro.directionValueOfOrNull

@JsonDeserialize(using = YamlSwipeDeserializer::class)
interface YamlSwipe {
    val duration: Long
}

data class YamlSwipeDirection(val direction: SwipeDirection, override val duration: Long = DEFAULT_DURATION_IN_MILLIS) : YamlSwipe
data class YamlCoordinateSwipe(val start: String, val end: String, override val duration: Long = DEFAULT_DURATION_IN_MILLIS) : YamlSwipe
data class YamlRelativeCoordinateSwipe(val start: String, val end: String, override val duration: Long = DEFAULT_DURATION_IN_MILLIS) : YamlSwipe

@JsonDeserialize(`as` = YamlSwipeElement::class)
data class YamlSwipeElement(
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES])
    val direction: SwipeDirection,
    val from: YamlElementSelectorUnion,
    override val duration: Long = DEFAULT_DURATION_IN_MILLIS
) : YamlSwipe

private const val DEFAULT_DURATION_IN_MILLIS = 400L

class YamlSwipeDeserializer : JsonDeserializer<YamlSwipe>() {

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): YamlSwipe {
        val mapper = (parser.codec as ObjectMapper)
        val root: TreeNode = mapper.readTree(parser)
        val input = root.fieldNames().asSequence().toList()
        val duration = getDuration(root)
        when {
            input.contains("start") || input.contains("end") -> {
                check(root.get("direction") == null) { "You cannot provide direction with start/end swipe." }
                check(root.get("start") != null && root.get("end") != null) {
                    "You need to provide both start and end coordinates, to swipe with coordinates"
                }
                return resolveCoordinateSwipe(root, duration)
            }
            input.contains("direction") -> {
                check(root.get("start") == null && root.get("end") == null) {
                    "You cannot provide start/end coordinates with directional swipe"
                }
                val direction = root.get("direction").toString().replace("\"", "")
                check(directionValueOfOrNull<SwipeDirection>(direction) != null) {
                    "Invalid direction provided to directional swipe: $direction. Direction can be either:\n" +
                        "1. RIGHT or right\n" +
                        "2. LEFT or left\n" +
                        "3. UP or up\n" +
                        "4. DOWN or down"
                }
                val isDirectionalSwipe = input == listOf("direction", "duration") || input == listOf("direction")
                return if (isDirectionalSwipe) {
                    YamlSwipeDirection(SwipeDirection.valueOf(direction.uppercase()), duration)
                } else {
                    mapper.convertValue(root, YamlSwipeElement::class.java)
                }
            }
            else -> {
                throw IllegalArgumentException(
                    "Swipe command takes either: \n" +
                        "\t1. direction: Direction based swipe with: \"RIGHT\", \"LEFT\", \"UP\", or \"DOWN\" or \n" +
                        "\t2. start and end: Coordinates based swipe with: \"start\" and \"end\" coordinates \n" +
                        "\t3. direction and element to swipe directionally on element\n" +
                        "It seems you provided invalid input with: $input"
                )
            }
        }
    }

    private fun resolveCoordinateSwipe(root: TreeNode, duration: Long): YamlSwipe {
        when {
            isRelativeSwipe(root) -> {
                val start = root.path("start").toString().replace("\"", "")
                val end = root.path("end").toString().replace("\"", "")
                check(start.contains("%") && end.contains("%")) {
                    "You need to provide start and end coordinates with %, Found: (${start}, ${end})"
                }
                val startPoints = start
                    .replace("%", "")
                    .split(",")
                    .map { it.trim().toInt() }
                val endPoints = end
                    .replace("%", "")
                    .split(",")
                    .map { it.trim().toInt() }
                check(startPoints[0] in 0..100 && startPoints[1] in 0..100) {
                    "Invalid start point: $start should be between 0 to 100"
                }
                check(endPoints[0] in 0..100 && endPoints[1] in 0..100) {
                    "Invalid start point: $end should be between 0 to 100"
                }

                return YamlRelativeCoordinateSwipe(
                    start,
                    end,
                    duration
                )
            }
            else -> return YamlCoordinateSwipe(
                root.path("start").toString().replace("\"", ""),
                root.path("end").toString().replace("\"", ""),
                duration
            )
        }
    }

    private fun isRelativeSwipe(root: TreeNode): Boolean {
        return root.get("start").toString().contains("%") || root.get("end").toString().contains("%")
    }

    private fun getDuration(root: TreeNode): Long {
        return if (root.path("duration").isMissingNode) {
            DEFAULT_DURATION_IN_MILLIS
        } else {
            root.path("duration").toString().replace("\"", "").toLong()
        }
    }

}