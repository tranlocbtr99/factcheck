package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FactCheckVerdict

@Composable
fun VerdictBadge(
    verdict: FactCheckVerdict,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val icon: ImageVector = when (verdict) {
        FactCheckVerdict.DUNG -> Icons.Default.CheckCircle
        FactCheckVerdict.SAI -> Icons.Default.Close
        FactCheckVerdict.DUNG_MOT_PHAN -> Icons.Default.Info
        FactCheckVerdict.GAY_HIEU_LAM -> Icons.Default.WarningAmber
        FactCheckVerdict.CHUA_DU_BANG_CHUNG -> Icons.Default.Help
        FactCheckVerdict.KHONG_THE_KIEM_CHUNG -> Icons.Default.Warning
    }

    val iconSize = if (large) 20.dp else 14.dp
    val textStyle = if (large) {
        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
    } else {
        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }

    val padH = if (large) 14.dp else 10.dp
    val padV = if (large) 6.dp else 4.dp

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(verdict.color.copy(alpha = 0.18f))
            .border(1.dp, verdict.color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (large) 8.dp else 5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = verdict.titleVi,
            tint = verdict.color,
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = verdict.titleVi,
            color = verdict.color,
            style = textStyle
        )
    }
}
