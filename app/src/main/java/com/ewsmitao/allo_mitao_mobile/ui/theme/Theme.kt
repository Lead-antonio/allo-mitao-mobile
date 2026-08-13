package com.ewsmitao.allo_mitao_mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SireneColorScheme = darkColorScheme(
    primary             = YellowAlert,
    onPrimary           = NavyDeep,
    primaryContainer    = NavyLight,
    onPrimaryContainer  = White,
    secondary           = NavyLight,
    onSecondary         = White,
    background          = NavyDeep,
    onBackground        = White,
    surface             = NavyMid,
    onSurface           = White,
    surfaceVariant      = NavySurface,
    onSurfaceVariant    = WhiteSoft,
    outline             = WhiteDisabled,
    error               = StatusFail,
    onError             = White,
)

@Composable
fun Sirene_managerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SireneColorScheme,
        typography  = Typography,
        content     = content
    )
}