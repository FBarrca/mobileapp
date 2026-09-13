package coredevices.ring.pluskey

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PlusKeySettingsEntry() {
    val context = LocalContext.current
    ListItem(headlineContent = { Text("OnePlus Plus Key") },
        supportingContent = { Text("Hold to record with your phone; release to process in Index") },
        modifier = Modifier.clickable { context.startActivity(Intent(context, IndexPlusKeyActivity::class.java)) })
}
