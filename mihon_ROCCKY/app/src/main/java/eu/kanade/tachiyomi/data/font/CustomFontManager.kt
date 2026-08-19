package eu.kanade.tachiyomi.data.font

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import eu.kanade.domain.ui.UiPreferences
import java.io.File

class CustomFontManager(
    private val context: Context,
    private val uiPreferences: UiPreferences,
) {

    companion object {
        private const val FONT_DIR = "custom_fonts"
        private const val FONT_FILE_NAME = "custom_font.ttf"
    }

    private val fontDir: File get() = File(context.filesDir, FONT_DIR).also { it.mkdirs() }
    val fontFile: File get() = File(fontDir, FONT_FILE_NAME)

    fun hasCustomFont(): Boolean {
        return uiPreferences.customFontUri.get().isNotEmpty() && fontFile.exists()
    }

    fun getFontName(): String {
        return uiPreferences.customFontName.get()
    }

    fun saveFont(uri: Uri, displayName: String): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                fontFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            uiPreferences.customFontUri.set(uri.toString())
            uiPreferences.customFontName.set(displayName)
            fontFile.exists()
        } catch (e: Exception) {
            false
        }
    }

    fun loadTypeface(): Typeface? {
        if (!fontFile.exists()) return null
        return try {
            Typeface.createFromFile(fontFile)
        } catch (e: Exception) {
            null
        }
    }

    fun loadFontFamily(): FontFamily? {
        if (!fontFile.exists()) return null
        return FontFamily(Font(file = fontFile))
    }

    fun reset() {
        fontFile.delete()
        uiPreferences.customFontUri.set("")
        uiPreferences.customFontName.set("")
    }

    /**
     * Computes a scale factor to normalize the custom font against the system default font.
     * Measures the ascent ratio at a reference text size.
     * Returns 1f if no custom font is loaded or if measurement fails.
     */
    fun computeFontScaleFactor(): Float {
        val typeface = loadTypeface() ?: return 1f
        val defaultTypeface = Typeface.DEFAULT

        val paint = Paint().apply {
            textSize = 100f
            isAntiAlias = true
        }

        paint.typeface = defaultTypeface
        val defaultMetrics = paint.fontMetrics
        val defaultAscent = -defaultMetrics.ascent

        paint.typeface = typeface
        val customMetrics = paint.fontMetrics
        val customAscent = -customMetrics.ascent

        if (customAscent <= 0f) return 1f

        return (defaultAscent / customAscent).coerceIn(0.5f, 2f)
    }

    /**
     * Creates an adjusted [Typography] with the custom font applied to all text styles.
     * Font sizes and line heights are scaled proportionally to match the system font metrics.
     */
    fun createAdjustedTypography(baseTypography: Typography): Typography {
        val fontFamily = loadFontFamily() ?: return baseTypography
        val scaleFactor = computeFontScaleFactor()

        return Typography(
            displayLarge = adjustStyle(baseTypography.displayLarge, fontFamily, scaleFactor),
            displayMedium = adjustStyle(baseTypography.displayMedium, fontFamily, scaleFactor),
            displaySmall = adjustStyle(baseTypography.displaySmall, fontFamily, scaleFactor),
            headlineLarge = adjustStyle(baseTypography.headlineLarge, fontFamily, scaleFactor),
            headlineMedium = adjustStyle(baseTypography.headlineMedium, fontFamily, scaleFactor),
            headlineSmall = adjustStyle(baseTypography.headlineSmall, fontFamily, scaleFactor),
            titleLarge = adjustStyle(baseTypography.titleLarge, fontFamily, scaleFactor),
            titleMedium = adjustStyle(baseTypography.titleMedium, fontFamily, scaleFactor),
            titleSmall = adjustStyle(baseTypography.titleSmall, fontFamily, scaleFactor),
            bodyLarge = adjustStyle(baseTypography.bodyLarge, fontFamily, scaleFactor),
            bodyMedium = adjustStyle(baseTypography.bodyMedium, fontFamily, scaleFactor),
            bodySmall = adjustStyle(baseTypography.bodySmall, fontFamily, scaleFactor),
            labelLarge = adjustStyle(baseTypography.labelLarge, fontFamily, scaleFactor),
            labelMedium = adjustStyle(baseTypography.labelMedium, fontFamily, scaleFactor),
            labelSmall = adjustStyle(baseTypography.labelSmall, fontFamily, scaleFactor),
        )
    }

    private fun adjustStyle(
        style: TextStyle,
        fontFamily: FontFamily,
        scaleFactor: Float,
    ): TextStyle {
        val adjustedFontSize = style.fontSize * scaleFactor
        val adjustedLineHeight = style.lineHeight * scaleFactor
        return style.copy(
            fontFamily = fontFamily,
            fontSize = adjustedFontSize,
            lineHeight = adjustedLineHeight,
        )
    }
}
