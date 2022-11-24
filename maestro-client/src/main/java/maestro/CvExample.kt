package maestro

import boofcv.abst.distort.FDistort
import boofcv.alg.misc.ImageStatistics
import boofcv.alg.misc.PixelMath
import boofcv.core.image.GeneralizedImageOps
import boofcv.factory.template.FactoryTemplateMatching
import boofcv.factory.template.TemplateScoreType
import boofcv.io.UtilIO
import boofcv.io.image.ConvertBufferedImage
import boofcv.io.image.UtilImageIO
import boofcv.struct.feature.Match
import boofcv.struct.image.GrayF32
import java.awt.BasicStroke
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel

fun main() {
    // Load image and templates
    val directory = UtilIO.pathExample(".")

    val image = UtilImageIO.loadImage(".", "screen.png", GrayF32::class.java)
    val templateCursor = UtilImageIO.loadImage(".", "img.png", GrayF32::class.java)!!
//    val maskCursor = UtilImageIO.loadImage(directory, "cursor_mask.png", GrayF32::class.java)
//    val templatePaint = UtilImageIO.loadImage(directory, "paint.png", GrayF32::class.java)!!

    // create output image to show results

    // create output image to show results
    val output = BufferedImage(image!!.width, image.height, BufferedImage.TYPE_INT_BGR)
    ConvertBufferedImage.convertTo(image, output)
    val g2 = output.createGraphics()

    // Now it's try finding the cursor without a mask. it will get confused when the background is black
    g2.color = Color.BLUE
    g2.stroke = BasicStroke(2F)
    drawRectangles(g2, image, templateCursor, null, 1)

    showImage("Found Matches", output)
}

/**
 * Helper function will is finds matches and displays the results as colored rectangles
 */
private fun drawRectangles(
    g2: Graphics2D,
    image: GrayF32,
    template: GrayF32,
    mask: GrayF32?,
    expectedMatches: Int
) {
//    val sizes = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
//    val sizes = listOf(0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0)
//    val sizes = listOf(2.0, 3.0, 5.0)
//    val sizes = listOf(0.25, 0.5, 0.75, 1.0)
    val sizes = (0..9).map { 1.0 - it * 0.1 }
    val (bestScale, bestMatch) = sizes
        .map {
            val scaled = GeneralizedImageOps.createSingleBand(
                GrayF32::class.java,
                (image.width * it).toInt(),
                (image.height * it).toInt()
            )
            FDistort(image, scaled)
                .scaleExt()
                .apply()

            it to scaled
        }
        .map { (scale, scaledImage) ->
            scale to findMatches(scaledImage, template, mask, expectedMatches)
                .maxByOrNull { it.score }
                ?.also { println("scale: $scale, score: ${it.score}") }
        }
        .maxByOrNull { (_, match) -> match?.score ?: Double.MAX_VALUE }
        ?: return

    val r = 2
    val w = ((template.width + 2 * r) / bestScale).toInt()
    val h = ((template.height + 2 * r) / bestScale).toInt()

    val m = bestMatch ?: return

    System.out.printf("Match %3d %3d    score = %6.2f\n", m.x, m.y, m.score)

    // the return point is the template's top left corner
    val x0: Int = (m.x / bestScale).toInt() - r
    val y0: Int = (m.y / bestScale).toInt() - r
    val x1 = x0 + w
    val y1 = y0 + h
    g2.drawLine(x0, y0, x1, y0)
    g2.drawLine(x1, y0, x1, y1)
    g2.drawLine(x1, y1, x0, y1)
    g2.drawLine(x0, y1, x0, y0)
}

/**
 * Demonstrates how to search for matches of a template inside an image
 *
 * @param image Image being searched
 * @param template Template being looked for
 * @param mask Mask which determines the weight of each template pixel in the match score
 * @param expectedMatches Number of expected matches it hopes to find
 * @return List of match location and scores
 */
private fun findMatches(
    image: GrayF32,
    template: GrayF32,
    mask: GrayF32?,
    expectedMatches: Int
): List<Match> {
    // create template matcher.
    val matcher = FactoryTemplateMatching.createMatcher(
        TemplateScoreType.SUM_SQUARE_ERROR,
        GrayF32::class.java
    )

    // Find the points which match the template the best
    matcher.setImage(image)
    matcher.setTemplate(template, mask, expectedMatches)
    matcher.process()
    return matcher.results.toList()
}

/**
 * Computes the template match intensity image and displays the results. Brighter intensity indicates
 * a better match to the template.
 */
private fun showMatchIntensity(image: GrayF32, template: GrayF32?, mask: GrayF32?) {
    // create algorithm for computing intensity image
    val matchIntensity = FactoryTemplateMatching.createIntensity(TemplateScoreType.SUM_SQUARE_ERROR, GrayF32::class.java)

    // apply the template to the image
    matchIntensity.setInputImage(image)
    matchIntensity.process(template, mask)

    // get the results
    val intensity = matchIntensity.intensity

    // White will indicate a good match and black a bad match, or the reverse
    // depending on the cost function used.
    val min = ImageStatistics.min(intensity)
    val max = ImageStatistics.max(intensity)
    val range = max - min
    PixelMath.plus(intensity, -min, intensity)
    PixelMath.divide(intensity, range, intensity)
    PixelMath.multiply(intensity, 255.0f, intensity)
    val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_BGR)
    showImage("Match Intensity", output)
}

fun showImage(title: String, image: Image) {
    val frame = JFrame()
    frame.title = title
    frame.contentPane.layout = FlowLayout()
    frame.contentPane.add(
        JLabel(
            ImageIcon(
                image.getScaledInstance(
                    image.getWidth(null) / 4,
                    image.getHeight(null) / 4,
                    Image.SCALE_SMOOTH
                )
            )
        )
    )
    frame.pack()
    frame.isVisible = true
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
}
