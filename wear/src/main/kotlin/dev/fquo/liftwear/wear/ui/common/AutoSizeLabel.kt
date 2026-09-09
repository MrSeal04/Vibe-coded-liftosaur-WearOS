package dev.fquo.liftwear.wear.ui.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import dev.fquo.liftwear.wear.ui.FontRange

/**
 * A label that shrinks rather than being cut.
 *
 * Exercise names are not this app's to choose - "Seated Dumbbell Shoulder Press" comes
 * straight from the program - and the widest line on a round screen is the one nearest the
 * top, which is where the name sits. Ellipsising it would hide which exercise you are on;
 * shrinking it by a couple of points does not.
 */
@Composable
fun AutoSizeLabel(
    text: String,
    range: FontRange,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = LocalContentColor.current,
            textAlign = TextAlign.Center,
            // Same trap as the numerals: the token's fixed lineHeight is not scaled by
            // auto-size, so a shrunk glyph is clipped by its own line box.
            lineHeight = TextUnit.Unspecified,
        ),
        maxLines = maxLines,
        // A backstop only. At the minimum size the text should already fit; if a future
        // string does not, it must degrade visibly rather than lose its last letter.
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = range.min,
            maxFontSize = range.max,
            stepSize = 1.sp,
        ),
    )
}
