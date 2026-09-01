package co.zw.nissangtr.customer.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.R
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.R as VisualR
import kotlinx.coroutines.delay

@Composable
fun CustomerPremiumSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(850)
        onFinished()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background),
    ) {
        Image(
            painter = painterResource(VisualR.drawable.gtr_hero_workshop_r35),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(320.dp),
            contentScale = ContentScale.Crop,
            alpha = .48f,
        )
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.gtr_logo_clear),
                contentDescription = "Nissan GTR Auto",
                modifier = Modifier.fillMaxWidth(.72f).height(90.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                "PARTS • PERFORMANCE • PRECISION",
                color = GtrPremiumColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
