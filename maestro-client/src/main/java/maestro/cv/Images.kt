package maestro.cv

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

object Images {

    fun base64ToImage(base64String: String): BufferedImage {
        val imageBytes = Base64.getDecoder().decode(base64String)
        val bis = ByteArrayInputStream(imageBytes)
        return ImageIO.read(bis)
    }

}