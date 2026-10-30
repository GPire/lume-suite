package com.weenas.castbay.service

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException

/** What the Health screen shows. */
sealed interface HealthPhase {
    object Checking : HealthPhase
    object Authorizing : HealthPhase
    object NotFire : HealthPhase
    object AdbOff : HealthPhase
    data class Ready(val todo: Int, val disabled: Int) : HealthPhase
    /** This TV's debugging service does not take commands from the TV itself (Fire OS 8.1): TV Doctor on a computer does it. */
    data class Manual(val todo: Int, val disabled: Int) : HealthPhase
    data class Working(val done: Int, val total: Int) : HealthPhase
    data class Done(val before: Int, val after: Int, val changed: Int, val rolledBack: Boolean) : HealthPhase
    data class Failed(val message: String) : HealthPhase
}

/**
 * The same careful cleanup as TV Doctor, run from the TV itself: turns off (never uninstalls) Amazon's
 * shopping, tutorial and promotional apps, in groups of four with a health check after each and an automatic
 * put-back, and can undo everything. Fire TV only; other TVs use TV Doctor on a computer.
 */
class HealthModel(private val context: Context) {
    var phase: HealthPhase by mutableStateOf(HealthPhase.Checking)
        private set
    var freeMb: Int by mutableStateOf(0)
        private set
    var totalMb: Int by mutableStateOf(1)
        private set

    private val prefs = context.getSharedPreferences("health", Context.MODE_PRIVATE)
    private val keyFile = File(context.filesDir, "adb_key")

    private var todoNow = 0
    private var offNow = 0

    /** What can be seen without any special access: which of the safe-list apps are on this TV and which are off. */
    private fun readPackages() {
        val pm = context.packageManager
        val here = SAFE.mapNotNull { runCatching { pm.getApplicationInfo(it, 0) }.getOrNull() }
        offNow = here.count { !it.enabled }; todoNow = here.size - offNow
    }

    private fun readMemory() {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        freeMb = (info.availMem / 1048576L).toInt(); totalMb = (info.totalMem / 1048576L).toInt().coerceAtLeast(1)
    }

    private fun packages(s: SelfAdb, flag: String): Set<String> =
        s.shell("pm list packages $flag").lines().filter { it.startsWith("package:") }.map { it.removePrefix("package:").trim() }.toSet()

    private fun healthy(s: SelfAdb): Boolean =
        s.shell("getprop sys.boot_completed") == "1" &&
            s.shell("cmd package resolve-activity --brief -c android.intent.category.HOME").contains("/")

    private suspend fun <T> connected(block: suspend (SelfAdb) -> T): T? {
        return try {
            withContext(Dispatchers.IO) { SelfAdb.open(keyFile) }.use { block(it) }
        } catch (e: ConnectException) {
            phase = HealthPhase.AdbOff; null
        } catch (e: java.net.SocketTimeoutException) {
            phase = HealthPhase.Failed(e.message ?: "timeout"); null
        } catch (e: java.io.IOException) {
            phase = HealthPhase.Manual(todoNow, offNow); null
        } catch (e: Exception) {
            phase = HealthPhase.Failed(e.message ?: e.javaClass.simpleName); null
        }
    }

    suspend fun refresh() {
        readMemory(); readPackages()
        if (!android.os.Build.MANUFACTURER.equals("Amazon", ignoreCase = true)) { phase = HealthPhase.NotFire; return }
        if (todoNow == 0) { phase = HealthPhase.Ready(0, offNow); return }
        phase = HealthPhase.Checking
        val slow = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            delay(2500); if (phase == HealthPhase.Checking) phase = HealthPhase.Authorizing
        }
        connected { s ->
            withContext(Dispatchers.IO) {
                val installed = packages(s, ""); val disabled = packages(s, "-d")
                val todo = SAFE.count { it in installed && it !in disabled }
                phase = HealthPhase.Ready(todo, disabled.size)
            }
        }
        slow.cancel()
    }

    suspend fun optimize() {
        connected { s ->
            withContext(Dispatchers.IO) {
                readMemory(); val before = freeMb
                val installed = packages(s, ""); val disabled = packages(s, "-d")
                val todo = SAFE.filter { it in installed && it !in disabled }
                val changed = mutableListOf<String>()
                var rolledBack = false
                for (batch in todo.chunked(4)) {
                    batch.forEach { s.shell("pm disable-user --user 0 $it") }
                    delay(1500)
                    if (!healthy(s)) { batch.forEach { s.shell("pm enable $it") }; rolledBack = true; break }
                    changed += batch; phase = HealthPhase.Working(changed.size, todo.size)
                }
                tune(s)
                s.shell("sync")
                prefs.edit().putStringSet("changed", (prefs.getStringSet("changed", emptySet())!! + changed).toSet()).apply()
                delay(3000); readMemory()
                phase = HealthPhase.Done(before, freeMb, changed.size, rolledBack)
            }
        }
    }

    suspend fun undo() {
        connected { s ->
            withContext(Dispatchers.IO) {
                val disabled = packages(s, "-d")
                val mine = (prefs.getStringSet("changed", emptySet())!! + SAFE).filter { it in disabled }
                mine.forEach { s.shell("pm enable $it") }
                if (prefs.getBoolean("tuned", false)) {
                    listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").forEach { s.shell("settings put global $it 1.0") }
                    prefs.edit().putBoolean("tuned", false).apply()
                }
                s.shell("sync")
                prefs.edit().remove("changed").apply()
            }
            refresh()
        }
    }

    /** Calmer transitions on a small-memory stick; left alone when already changed or when memory is plentiful. */
    private fun tune(s: SelfAdb) {
        val scale = when { totalMb < 1300 -> "0.5"; totalMb < 2200 -> "0.7"; else -> return }
        val now = s.shell("settings get global window_animation_scale")
        if (now != "1.0" && now != "1" && now != "null") return
        listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").forEach { s.shell("settings put global $it $scale") }
        prefs.edit().putBoolean("tuned", true).apply()
    }

    companion object {
        /** Same list as TV Doctor's "safe" tier for Fire OS: turned off, never removed, always undoable. */
        val SAFE = listOf(
            "com.amazon.shoptv.firetv.client", "com.amazon.shoptv.client", "com.amazon.ods.kindleconnect",
            "com.amazon.bueller.music", "com.amazon.dummy.music", "com.amazon.bueller.photos",
            "com.amazon.minitv.android.app", "com.amazon.tahoe", "com.amazon.gamehub",
            "com.amazon.tv.website_launcher", "com.amazon.smarthomemapviewapp", "com.amazon.storm.lightning.tutorial",
            "com.amazon.tmm.tutorial", "com.amazon.tv.releasenotes", "com.amazon.sneakpeek"
        )
    }
}
