package com.hibernador.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hibernador.app.data.formatBytes
import com.hibernador.app.engine.AppResult
import com.hibernador.app.engine.HibernationEngine
import com.hibernador.app.engine.Phase
import com.hibernador.app.engine.Step
import com.hibernador.app.ui.theme.Danger
import com.hibernador.app.ui.theme.Success
import com.hibernador.app.ui.theme.Teal
import kotlin.math.roundToInt

@Composable
fun HibernationScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val state by HibernationEngine.state.collectAsStateWithLifecycle()

    // rememberSaveable evita reiniciar a hibernação ao girar a tela.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            if (!HibernationEngine.state.value.isActive) HibernationEngine.start(context)
        }
    }

    val cancelAndExit = {
        HibernationEngine.cancel()
        HibernationEngine.reset()
        onExit()
    }
    val finishAndExit = {
        HibernationEngine.reset()
        onExit()
    }
    BackHandler { if (state.isActive) cancelAndExit() else finishAndExit() }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when (state.phase) {
                Phase.SCANNING -> "Procurando apps em segundo plano…"
                Phase.RUNNING -> "Hibernando ${state.done + 1} de ${state.total}"
                Phase.DONE -> "Hibernação concluída"
                Phase.CANCELLED -> "Cancelado"
                Phase.ERROR -> "Erro"
                Phase.IDLE -> "Preparando…"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(24.dp))

        ProgressRing(progress = state.progress, modifier = Modifier.size(240.dp)) { animated ->
            if (state.phase == Phase.SCANNING) {
                CircularProgressIndicator(color = Teal, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(animated * 100).roundToInt()}%", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    if (state.phase == Phase.DONE) {
                        Text("liberados ${formatBytes(state.ramFreed)}", color = Success, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        when (state.phase) {
            Phase.DONE -> SummaryCard(
                ramFreed = state.ramFreed,
                cacheFreed = state.cacheFreed,
                apps = state.completed.size,
                protectedCount = state.protectedCount,
                cacheSupported = state.cacheSupported,
            )
            Phase.ERROR -> Text(
                state.error ?: "Falha desconhecida",
                color = Danger,
                textAlign = TextAlign.Center,
            )
            else -> CurrentApp(state.current?.packageName, state.current?.label, state.step, state.cacheSupported)
        }

        Spacer(Modifier.height(12.dp))

        val listState = rememberLazyListState()
        LaunchedEffect(state.completed.size) {
            if (state.completed.isNotEmpty()) listState.animateScrollToItem(state.completed.lastIndex)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.completed, key = { it.app.packageName }) { CompletedRow(it) }
        }

        Spacer(Modifier.height(12.dp))

        if (state.isActive) {
            OutlinedButton(
                onClick = cancelAndExit,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Cancelar", fontSize = 16.sp) }
        } else {
            Button(
                onClick = finishAndExit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Concluir", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun CurrentApp(packageName: String?, label: String?, step: Step, cacheSupported: Boolean) {
    AnimatedContent(
        targetState = packageName to label,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "current",
    ) { (pkg, name) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(56.dp)) {
            if (pkg != null) {
                AppIcon(pkg, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(name ?: pkg, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (step) {
                            Step.STOPPING -> "Parando app…"
                            Step.CLEARING_CACHE -> if (cacheSupported) "Limpando cache…" else "Finalizando…"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedRow(r: AppResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            AppIcon(r.app.packageName, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                val details = buildList {
                    if (r.ramFreed > 0) add("RAM ${formatBytes(r.ramFreed)}")
                    when (r.cacheCleared) {
                        true -> add(if (r.cacheFreed > 0) "cache ${formatBytes(r.cacheFreed)}" else "cache limpo")
                        false -> add("cache: falhou")
                        null -> {}
                    }
                }.joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            if (r.stopped) {
                Icon(Icons.Filled.Check, contentDescription = "Concluído", tint = Success)
            } else {
                Icon(Icons.Filled.Warning, contentDescription = "Falhou", tint = Danger)
            }
        }
    }
}

@Composable
private fun SummaryCard(ramFreed: Long, cacheFreed: Long, apps: Int, protectedCount: Int, cacheSupported: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RAM liberada", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(formatBytes(ramFreed), color = Success, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val cacheText = if (cacheSupported) "Cache limpo: ${formatBytes(cacheFreed)}" else "Limpeza de cache requer Android 14+"
            Text(
                "$apps apps hibernados · $protectedCount protegidos\n$cacheText",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
