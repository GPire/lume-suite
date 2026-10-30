package com.weenas.castbay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.R

/** The CastBay mark beside the app's name (CastBay, or 映湾 in Chinese), as on the home and About screens. */
@Composable
fun BrandTitle(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_castbay_mark),
            contentDescription = null,
            modifier = Modifier.size(if (compact) 36.dp else 60.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 12.dp else 16.dp))
        Text(
            text = stringResource(R.string.app_name),
            fontSize = if (compact) 24.sp else 40.sp,
            lineHeight = if (compact) 30.sp else 48.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** The slogan (一投即达，自在映湾), in the app's lighter purple, under [BrandTitle]. */
@Composable
fun BrandSlogan(
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 24.sp,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null
) {
    Text(
        text = stringResource(R.string.app_slogan),
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = SLOGAN_COLOR,
        textAlign = textAlign,
        modifier = modifier
    )
}

private val SLOGAN_COLOR = Color(0xFFF2C14E)
