package uk.co.aaronburt.rtsp.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.aaronburt.rtsp.BuildConfig

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAtMs: Long) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val bytes: Long, val total: Long) : UpdateState
    data class Ready(val release: ReleaseInfo, val file: File, val needsPermission: Boolean = false) : UpdateState
    data class Installing(val release: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String, val release: ReleaseInfo?) : UpdateState
}

object UpdateManager {
    const val AUTO_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

    private const val PREFS = "updater"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_DISMISSED_TAG = "dismissed_tag"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val mutablePrompt = MutableStateFlow<ReleaseInfo?>(null)

    private lateinit var app: Application
    private lateinit var prefs: SharedPreferences
    private var job: Job? = null

    val state: StateFlow<UpdateState> = mutableState.asStateFlow()
    val prompt: StateFlow<ReleaseInfo?> = mutablePrompt.asStateFlow()

    fun init(application: Application) {
        app = application
        prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun checkOnLaunch() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (UpdateDecision.shouldAutoCheck(last, System.currentTimeMillis(), AUTO_CHECK_INTERVAL_MS)) {
            check(auto = true)
        }
    }

    fun check(auto: Boolean = false) {
        if (isBusy()) return
        job = scope.launch {
            mutableState.value = UpdateState.Checking
            try {
                val release = withContext(Dispatchers.IO) { GitHubReleases.latest() }
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                if (release != null && UpdateDecision.isNewer(release.tag, BuildConfig.VERSION_NAME)) {
                    mutableState.value = UpdateState.Available(release)
                    if (!auto || prefs.getString(KEY_DISMISSED_TAG, null) != release.tag) {
                        mutablePrompt.value = release
                    }
                } else {
                    mutableState.value = UpdateState.UpToDate(now)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = if (auto) {
                    UpdateState.Idle
                } else {
                    UpdateState.Failed(e.message ?: "Could not check for updates", null)
                }
            }
        }
    }

    fun download(release: ReleaseInfo) {
        if (isBusy()) return
        job = scope.launch {
            mutableState.value = UpdateState.Downloading(release, 0L, release.apkSize)
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val target = File(dir, "rtsp-${release.tag}.apk")
                    GitHubReleases.download(release, target) { bytes, total ->
                        mutableState.value = UpdateState.Downloading(release, bytes, total)
                    }
                    target
                }
                when (val result = withContext(Dispatchers.IO) { ApkVerifier.verify(app, file) }) {
                    VerifyResult.Ok -> mutableState.value = UpdateState.Ready(release, file)
                    is VerifyResult.Rejected -> {
                        file.delete()
                        mutableState.value = UpdateState.Failed(result.reason, release)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = UpdateState.Failed(e.message ?: "Download failed", release)
            }
        }
    }

    fun install() {
        val ready = mutableState.value as? UpdateState.Ready ?: return
        if (!app.packageManager.canRequestPackageInstalls()) {
            mutableState.value = ready.copy(needsPermission = true)
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${app.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(settings)
            return
        }
        job = scope.launch {
            mutableState.value = UpdateState.Installing(ready.release, ready.file)
            try {
                withContext(Dispatchers.IO) {
                    when (val result = ApkVerifier.verify(app, ready.file)) {
                        VerifyResult.Ok -> ApkInstaller.install(app, ready.file)
                        is VerifyResult.Rejected -> throw UpdateException(result.reason)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = UpdateState.Failed(e.message ?: "Install failed", ready.release)
            }
        }
    }

    fun onInstallFinished(status: Int, message: String?) {
        val installing = mutableState.value as? UpdateState.Installing ?: return
        mutableState.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Ready(installing.release, installing.file)
            else -> UpdateState.Failed(message ?: "Install failed", installing.release)
        }
    }

    fun dismissPrompt() {
        mutablePrompt.value?.let { prefs.edit().putString(KEY_DISMISSED_TAG, it.tag).apply() }
        mutablePrompt.value = null
    }

    fun retry() {
        val failed = mutableState.value as? UpdateState.Failed ?: return
        val release = failed.release
        if (release == null) check() else download(release)
    }

    private fun isBusy(): Boolean = when (mutableState.value) {
        UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> true
        else -> false
    }
}
