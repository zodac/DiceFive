package net.zodac.dicefive.ui.game

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import net.zodac.dicefive.resources.Res
import net.zodac.dicefive.resources.common_cpu_cd
import net.zodac.dicefive.ui.common.stringResource

/**
 * The little processor chip shown before a CPU player's name, wherever players are listed (the
 * in-game header, the results page), so a computer seat reads as one at a glance. Sized by the
 * caller to match the name's text beside it, and tinted like that text unless told otherwise.
 */
@Composable
fun CpuPlayerIcon(size: Dp, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(
        imageVector = Icons.Filled.Memory,
        contentDescription = stringResource(Res.string.common_cpu_cd),
        tint = tint,
        modifier = modifier.size(size),
    )
}
