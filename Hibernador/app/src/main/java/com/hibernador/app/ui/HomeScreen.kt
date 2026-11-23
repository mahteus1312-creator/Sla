package com.hibernador.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hibernador.app.data.LastResultStore
import com.hibernador.app.data.MemoryReader
import com.hibernador.app.data.formatBytes
import com.hibernador.app.shizuku.ShizukuManager
import com.hibernador.app.ui.theme.Danger
import com.hibernador.app.ui.theme.Indigo
import com.hibernador.app.ui.theme.Success
import com.hibernador.app.ui.theme.Teal

@Composable
fun HomeScreen(onHibernate: () -> Unit, onOpenRules: () -> Unit, onOpenShizuku: () -> Unit) {
    val context = LocalContext.current
    val memFlow = remember { MemoryReader.flow(context) }
    val mem by memFlow.collectAsStateWithLifecycle(initialValue = MemoryReader.read(context))
    val lastFreed by LastResultStore.lastFreed.collectAsStateWithLifecycle()
    val shizuku by ShizukuManager.status.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hibernador", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                ShizukuChip(shizuku, onOpenShizuku)
            }
            IconButton(onClick = onOpenRules) {
                Icon(Icons.Filled.Settings, contentDescription = "Regras de proteção")
            }
        }

        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Memória RAM", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatBytes(mem.used), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "  de ${formatBytes(mem.total)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                RamBar(mem.usedFraction, Modifier.fillMaxWidth().height(14.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "${(mem.usedFraction * 100).toInt()}% em uso",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Stat("Total", formatBytes(mem.total), MaterialTheme.colorScheme.onSurface)
                    Stat("Em uso", formatBytes(mem.used), Teal)
                    Stat("Livre", formatBytes(mem.available), Indigo)
                    Stat("Liberada", formatBytes(lastFreed), Success)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
                onClick = onHibernate,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color(0xFF00201C)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                modifier = Modifier.size(200.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("☾", fontSize = 44.sp)
                    Text("Hibernar", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            "Para apps em segundo plano e limpa o cache deles. Bancos, carteiras, jogos, apps de segurança e serviços essenciais nunca são tocados.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun ShizukuChip(status: ShizukuManager.Status, onClick: () -> Unit) {
    val ready = status == ShizukuManager.Status.READY
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Box(Modifier.size(8.dp).background(if (ready) Success else Danger, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            if (ready) "Shizuku ativo" else "Shizuku inativo — toque para ativar",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
