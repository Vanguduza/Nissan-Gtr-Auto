package co.zw.nissangtr.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import co.zw.nissangtr.ui.R

/** Official storefront logo (`res/drawable/gtr_logo`). */
@Composable
fun GtrLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "Nissan GTR Auto",
) {
    Image(
        painter = painterResource(R.drawable.gtr_logo),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
