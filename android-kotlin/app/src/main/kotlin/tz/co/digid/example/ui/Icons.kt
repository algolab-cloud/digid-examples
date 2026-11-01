package tz.co.digid.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes

import androidx.compose.ui.unit.dp

// Icons from Tabler Icons (MIT, © Paweł Kuna, see THIRD_PARTY_NOTICES.md): path data only, in a
// 24 x 24 box, the same as the Flutter SDK's lib/src/ui/icons.dart. Outline icons are stroked
// (width 2, round caps), filled ones filled. Draw them with `Icon(…, tint = …)`.

object Tabler {
    /** shield-check, shield-exclamation, shield-x: the result's status. */
    val shieldCheck = outline(
        "shield-check",
        "M11.46 20.846a12 12 0 0 1 -7.96 -14.846a12 12 0 0 0 8.5 -3a12 12 0 0 0 8.5 3a12 12 0 0 1 -.09 7.06",
        "M15 19l2 2l4 -4",
    )
    val shieldExclamation = outline(
        "shield-exclamation",
        "M15.04 19.745c-.942 .551 -1.964 .976 -3.04 1.255a12 12 0 0 1 -8.5 -15a12 12 0 0 0 8.5 -3a12 12 0 0 0 8.5 3a12 12 0 0 1 .195 6.015",
        "M19 16v3",
        "M19 22v.01",
    )
    val shieldX = outline(
        "shield-x",
        "M13.252 20.601c-.408 .155 -.826 .288 -1.252 .399a12 12 0 0 1 -8.5 -15a12 12 0 0 0 8.5 -3a12 12 0 0 0 8.5 3a12 12 0 0 1 -.19 7.357",
        "M22 22l-5 -5",
        "M17 22l5 -5",
    )

    /** circle-check, alert-circle, circle-x (filled): each line of what was checked. */
    val circleCheckFilled = filled(
        "circle-check-filled",
        "M17 3.34a10 10 0 1 1 -14.995 8.984l-.005 -.324l.005 -.324a10 10 0 0 1 14.995 -8.336zm-1.293 5.953a1 1 0 0 0 -1.32 -.083l-.094 .083l-3.293 3.292l-1.293 -1.292l-.094 -.083a1 1 0 0 0 -1.403 1.403l.083 .094l2 2l.094 .083a1 1 0 0 0 1.226 0l.094 -.083l4 -4l.083 -.094a1 1 0 0 0 -.083 -1.32z",
    )
    val alertCircleFilled = filled(
        "alert-circle-filled",
        "M12 2c5.523 0 10 4.477 10 10a10 10 0 0 1 -19.995 .324l-.005 -.324l.004 -.28c.148 -5.393 4.566 -9.72 9.996 -9.72zm.01 13l-.127 .007a1 1 0 0 0 0 1.986l.117 .007l.127 -.007a1 1 0 0 0 0 -1.986l-.117 -.007zm-.01 -8a1 1 0 0 0 -.993 .883l-.007 .117v4l.007 .117a1 1 0 0 0 1.986 0l.007 -.117v-4l-.007 -.117a1 1 0 0 0 -.993 -.883z",
    )
    val circleXFilled = filled(
        "circle-x-filled",
        "M17 3.34a10 10 0 1 1 -14.995 8.984l-.005 -.324l.005 -.324a10 10 0 0 1 14.995 -8.336zm-6.489 5.8a1 1 0 0 0 -1.218 1.567l1.292 1.293l-1.292 1.293l-.083 .094a1 1 0 0 0 1.497 1.32l1.293 -1.292l1.293 1.292l.094 .083a1 1 0 0 0 1.32 -1.497l-1.292 -1.293l1.292 -1.293l.083 -.094a1 1 0 0 0 -1.497 -1.32l-1.293 1.292l-1.293 -1.292l-.094 -.083z",
    )

    /** The back arrow of the top bars. */
    val arrowLeft = outline("arrow-left", "M5 12l14 0", "M5 12l6 6", "M5 12l6 -6")

    /** The check over the camera when a capture is accepted, and on a success mark. */
    val check = outline("check", "M5 12l5 5l10 -10")

    /** A failure mark. */
    val x = outline("x", "M18 6l-12 12", "M6 6l12 12")

    /** The torch toggle over the camera. */
    val bolt = outline("bolt", "M13 3l0 7l6 0l-8 11l0 -7l-6 0l8 -11")

    /** The camera permission and camera problem screens. */
    val camera = outline(
        "camera",
        "M5 7h1a2 2 0 0 0 2 -2a1 1 0 0 1 1 -1h6a1 1 0 0 1 1 1a2 2 0 0 0 2 2h1a2 2 0 0 1 2 2v9a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-9a2 2 0 0 1 2 -2",
        "M9 13a3 3 0 1 0 6 0a3 3 0 0 0 -6 0",
    )

    private fun outline(name: String, vararg paths: String) = icon(name) {
        for (d in paths) {
            addPath(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathData = addPathNodes(d),
            )
        }
    }

    private fun filled(name: String, vararg paths: String) = icon(name) {
        for (d in paths) addPath(fill = SolidColor(Color.Black), pathData = addPathNodes(d))
    }

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()
}
