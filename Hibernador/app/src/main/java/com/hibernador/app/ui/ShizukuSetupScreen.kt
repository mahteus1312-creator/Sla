package com.hibernador.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hibernador.app.shizuku.ShizukuManager
import com.hibernador.app.shizuku.ShizukuManager.Status
import com.hibernador.app.ui.theme.Success
import com.hibernador.app.ui.theme.Teal

@Composable
fun ShizukuSetupScreen(onBack: () -> Unit, onReady: () -> Unit) {
    val context = LocalContext.current
    val status by ShizukuManager.status.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ShizukuManager.refresh() }

    val installed = status != Status.NOT_INSTALLED
    val running = status == Status.NO_PERMISSION || status == Status.READY
    val granted = status == Status.READY

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text("Ativar o Shizuku", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Parar outros apps e limpar o cache deles exige permissão de sistema. O Hibernador usa o Shizuku, " +
                "que concede esse acesso via Depuração sem fio (sem root) ou root.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StepCard(1, "Instalar o Shizuku", installed,
            "Baixe o Shizuku na Play Store ou no GitHub (RikkaApps/Shizuku).") {
            if (!installed) Button(onClick = { ShizukuManager.openStore(context) }) { Text("Instalar Shizuku") }
        }

        StepCard(2, "Iniciar o Shizuku", running,
            "Ative as Opções do desenvolvedor (toque 7× em \"Número da versão\"), ligue a \"Depuração sem fio\" " +
                "e, no app Shizuku, toque em \"Parear\" e depois em \"Iniciar\". Com root, basta tocar em \"Iniciar\". " +
                "Após reiniciar o celular é preciso iniciar de novo.") {
            if (installed && !running) {
                Button(onClick = { ShizukuManager.openShizukuApp(context) }) { Text("Abrir Shizuku") }
            }
        }

        StepCard(3, "Permitir o Hibernador", granted,
            "Autorize o Hibernador a usar o Shizuku.") {
            if (running && !granted) {
                if (ShizukuManager.isPermissionPermanentlyDenied()) {
                    Text(
                        "Permissão negada permanentemente. Abra o Shizuku → Apps autorizados e habilite o Hibernador.",
                        color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    )
                    OutlinedButton(onClick = { ShizukuManager.openShizukuApp(context) }) { Text("Abrir Shizuku") }
                } else {
                    Button(onClick = { ShizukuManager.requestPermission() }) { Text("Conceder permissão") }
                }
            }
        }

        if (status == Status.UNSUPPORTED) {
            Text("Versão do Shizuku muito antiga. Atualize o Shizuku.", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(4.dp))
        if (granted) {
            Button(onClick = onReady, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Tudo pronto — Hibernar", fontWeight = FontWeight.SemiBold)
            }
        } else {
            OutlinedButton(onClick = { ShizukuManager.refresh() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Verificar novamente")
            }
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    done: Boolean,
    description: String,
    actions: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Column(
                Modifier.size(30.dp).background(if (done) Success else Teal.copy(alpha = 0.2f), CircleShape),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (done) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(18.dp))
                else Text("$number", color = Teal, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                actions()
            }
        }
    }
}
