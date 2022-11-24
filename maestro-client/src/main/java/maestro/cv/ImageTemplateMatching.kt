package maestro.cv

import boofcv.abst.distort.FDistort
import boofcv.core.image.GeneralizedImageOps
import boofcv.factory.template.FactoryTemplateMatching
import boofcv.factory.template.TemplateScoreType
import boofcv.io.image.ConvertBufferedImage
import boofcv.struct.feature.Match
import boofcv.struct.image.GrayF32
import maestro.Bounds
import java.awt.image.BufferedImage

object ImageTemplateMatching {

    fun findTemplate(
        image: BufferedImage,
        template: BufferedImage,
    ): Bounds? {
        val imageGrayF32 = ConvertBufferedImage.convertFromSingle(
            image,
            null,
            GrayF32::class.java
        )
        val templateGrayF32 = ConvertBufferedImage.convertFromSingle(
            template,
            null,
            GrayF32::class.java
        )

        return findBestMatch(imageGrayF32, templateGrayF32)
    }

    private fun findBestMatch(
        image: GrayF32,
        template: GrayF32,
    ): Bounds? {
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
                scale to findMatches(scaledImage, template)
                    .maxByOrNull { it.score }
            }
            .maxByOrNull { (_, match) -> match?.score ?: Double.MAX_VALUE }
            ?: return null

        val m = bestMatch ?: return null

        val r = 2
        val w = ((template.width + 2 * r) / bestScale).toInt()
        val h = ((template.height + 2 * r) / bestScale).toInt()

        val x: Int = (m.x / bestScale).toInt() - r
        val y: Int = (m.y / bestScale).toInt() - r

        return Bounds(
            x = x,
            y = y,
            width = w,
            height = h,
        )
    }

    private fun findMatches(
        image: GrayF32,
        template: GrayF32,
    ): List<Match> {
        val matcher = FactoryTemplateMatching.createMatcher(
            TemplateScoreType.SUM_SQUARE_ERROR,
            GrayF32::class.java
        )

        matcher.setImage(image)
        matcher.setTemplate(template, null, 1)
        matcher.process()
        return matcher.results.toList()
    }

}