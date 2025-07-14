package sp.sample.blescanner

import androidx.compose.ui.graphics.Color

internal data class Theme(
    val background: Color,
    val text: Color,
) {
    companion object {
        val Light = Theme(
            background = Color.White,
            text = Color.Black,
        )
        val Dark = Theme(
            background = Color.Black,
            text = Color.White,
        )
    }
}
