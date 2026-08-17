package app.rocat.ui.components

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource

/**
 * Script-driven rich-text HTML preview card (Tahap 22.2). Converts the HTML with
 * `android.text.Html.fromHtml` (bold / italic / underline / links / lists) and renders
 * it as an [AnnotatedString] inside a compact, scrollable block — no heavyweight
 * WebView needed. Tapping a link opens the system browser.
 */
@Composable
fun HtmlPreviewCard(
    htmlContent: String,
    title: String = "",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val linkInteraction: (LinkAnnotation) -> Unit = { link ->
        val url = (link as? LinkAnnotation.Clickable)?.tag as? String
        if (url != null) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }

    val spanned = remember(htmlContent) {
        runCatching { Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT) }
            .getOrNull() ?: SpannableStringBuilder(htmlContent)
    }
    val annotated = remember(spanned, linkStyle, linkInteraction) {
        spannedToAnnotated(spanned, linkStyle, linkInteraction)
    }
    val plainText = remember(spanned) { spanned.toString() }

    ScriptCanvasCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (plainText.isNotBlank()) {
                        CopyIconButton(
                            text = plainText,
                            label = stringResource(StringKey.copyText),
                            message = stringResource(StringKey.textCopied),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Box {
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                )
                if (title.isBlank() && plainText.isNotBlank()) {
                    CopyIconButton(
                        text = plainText,
                        label = stringResource(StringKey.copyText),
                        message = stringResource(StringKey.textCopied),
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
    }
}

/** Converts an [android.text.Spanned] into an [AnnotatedString], preserving
 *  bold/italic/underline styling and turning [URLSpan]s into clickable [LinkAnnotation]s. */
private fun spannedToAnnotated(
    spanned: Spanned,
    linkStyle: SpanStyle,
    onClick: (LinkAnnotation) -> Unit,
): AnnotatedString = buildAnnotatedString {
    append(spanned.toString())
    spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        if (start < 0 || end < 0 || end <= start) return@forEach
        when (span) {
            is StyleSpan -> when (span.style) {
                Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                Typeface.BOLD_ITALIC -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    start,
                    end,
                )
            }
            is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            is URLSpan -> {
                addLink(
                    LinkAnnotation.Clickable(
                        tag = span.url,
                        styles = TextLinkStyles(style = linkStyle),
                        linkInteractionListener = onClick,
                    ),
                    start,
                    end,
                )
            }
        }
    }
}
