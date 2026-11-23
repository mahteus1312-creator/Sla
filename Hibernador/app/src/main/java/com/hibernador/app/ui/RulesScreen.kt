package com.hibernador.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hibernador.app.data.RulesRepository
import com.hibernador.app.ui.theme.Success

/** Editor do arquivo de configuração protection_rules.json. */
@Composable
fun RulesScreen(onBack: () -> Unit) {
    var text by remember { mutableStateOf(RulesRepository.currentText()) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text("Regras de proteção", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Pacotes e palavras-chave de bancos/carteiras e segurança que nunca serão tocados. " +
                "Use \"extraProtectedPackages\" para proteger qualquer outro app. Launcher, teclado, " +
                "telefone/SMS, SystemUI e Play Services são sempre protegidos.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; message = null },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        message?.let { (msg, ok) ->
            Text(msg, color = if (ok) Success else MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    RulesRepository.resetToDefault()
                    text = RulesRepository.currentText()
                    message = "Regras padrão restauradas." to true
                },
                modifier = Modifier.weight(1f),
            ) { Text("Restaurar padrão") }
            Button(
                onClick = {
                    val err = RulesRepository.save(text)
                    message = (err ?: "Regras salvas.") to (err == null)
                },
                modifier = Modifier.weight(1f),
            ) { Text("Salvar") }
        }
    }
}
