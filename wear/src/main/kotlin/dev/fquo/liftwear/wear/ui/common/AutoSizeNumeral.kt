package dev.fquo.liftwear.wear.ui.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.LocalContentColor
import dev.fquo.liftwear.wear.ui.FontRange
import androidx.wear.compose.material3.MaterialTheme

/**
 * A number that fills the space it is given and no more.
 *
 * Wear M3's `Text` has no auto-size, and a fixed numeral token cannot work here: the same
 * slot holds "45lb" and "Seated Dumbbell Shoulder Press"'s "12", on screens from 192dp to
 * 240dp, at font scales up to 1.8. Shrinking to fit is the only way all of those stay on
 * screen without dropping a line the lifter needs mid-set.
 */
@Composable
fun AutoSizeNumeral(
    text: String,
    range: FontRange,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.numeralMedium,
) {
    BasicText(
        text = text,
        modifier = modifier,
        // The numeral tokens carry a fixed lineHeight. Auto-size shrinks fontSize but not
        // that, so a shrunk glyph gets clipped by its own oversized line box unless the
        // line height is released back to the font's own metrics.
        style = style.copy(
            color = LocalContentColor.current,
            textAlign = TextAlign.Center,
            lineHeight = TextUnit.Unspecified,
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = range.min,
            maxFontSize = range.max,
            stepSize = 1.sp,
        ),
    )
}
