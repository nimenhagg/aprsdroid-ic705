package org.aprsdroid.app.update

import android.os.Build
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.aprsdroid.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val canaryTimestamp: Long = 0L,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        if (canaryTimestamp > 0L || other.canaryTimestamp > 0L) {
            return canaryTimestamp.compareTo(other.canaryTimestamp)
        }
        return compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)
    }

    override fun toString(): String =
        if (canaryTimestamp > 0L) "Cancry-$canaryTimestamp" else "$major.$minor.$patch"
}

internal fun parseCancryTimestamp(value: String): Long? {
    val match = CANCRY_REGEX.find(value) ?: return null
    return match.groupValues[1].toLongOrNull()
}

internal fun parseAppVersion(value: String): AppVersion? {
    val canaryTs = parseCancryTimestamp(value)
    if (canaryTs != null) {
        return AppVersion(0, 0, 0, canaryTimestamp = canaryTs)
    }
    val match = VERSION_REGEX.find(value) ?: return null
    return AppVersion(
        major = match.groupValues[1].toIntOrNull() ?: return null,
        minor = match.groupValues[2].toIntOrNull() ?: return null,
        patch = match.groupValues[3].toIntOrNull() ?: return null,
    )
}

sealed interface UpdateCheckResult {
    data class UpToDate(val current: AppVersion, val latest: AppVersion) : UpdateCheckResult
    data class UpdateAvailable(
        val current: AppVersion,
        val latest: AppVersion,
        val releaseUrl: String,
        val deviceArchitecture: String,
        val releaseNotes: String,
        val isPromptSuppressed: Boolean = false,
    ) : UpdateCheckResult
    data class Failure(val message: String) : UpdateCheckResult
}

internal fun isUpdatePromptSuppressed(releaseNotes: String, releaseTitle: String = ""): Boolean {
    val suppressedPatterns = listOf(
        "[skip-update-prompt]",
        "[no-update-prompt]",
        "[skip-prompt]",
        "[no-prompt]",
        "[silent-update]",
        "[silent]",
        "[不提示更新]",
        "[静默更新]",
        "[忽略更新提示]",
        "<!-- aprsdroid:skip-prompt -->",
        "<!-- aprsdroid:no-prompt -->",
        "<!-- aprsdroid:silent -->",
        "<!-- skip-update -->",
    )
    return suppressedPatterns.any { pattern ->
        releaseNotes.contains(pattern, ignoreCase = true) || releaseTitle.contains(pattern, ignoreCase = true)
    }
}

internal fun formatDeviceArchitecture(abis: Array<String>): String {
    val abi = abis.firstOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
    val label = when (abi) {
        "arm64-v8a" -> "ARM64"
        "armeabi-v7a" -> "ARMv7"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        else -> "Unknown"
    }
    return "$label ($abi)"
}

internal fun findLatestCancryRelease(releasesJson: String): JSONObject? {
    val array = runCatching { JSONArray(releasesJson) }.getOrNull() ?: return null
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val tag = obj.optString("tag_name")
        if (parseCancryTimestamp(tag) != null) {
            return obj
        }
    }
    return null
}

/**
 * One-shot update checker. It never schedules work and never runs unless [check] is called.
 * Isolates official releases and debug canary releases into separate channels.
 */
object GitHubUpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/nimenhagg/aprsdroid-ic705/releases/latest"
    private const val ALL_RELEASES_URL =
        "https://api.github.com/repos/nimenhagg/aprsdroid-ic705/releases"

    private val client by lazy { OkHttpClient() }
    private val automaticCheckStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Runs at most once per application process. Failures and an up-to-date result are silent
     * to callers; only an available update is delivered.
     */
    fun checkAutomatically(currentVersionName: String, callback: (UpdateCheckResult.UpdateAvailable) -> Unit) {
        if (!automaticCheckStarted.compareAndSet(false, true)) return
        check(currentVersionName) { result ->
            if (result is UpdateCheckResult.UpdateAvailable && !result.isPromptSuppressed) {
                callback(result)
            }
        }
    }

    fun check(currentVersionName: String, callback: (UpdateCheckResult) -> Unit) {
        val isDebugTrack = runCatching { BuildConfig.IS_DEBUG_TRACK }.getOrDefault(false)
        val current = parseAppVersion(currentVersionName) ?: AppVersion(0, 0, 0, 0L)

        val targetUrl = if (isDebugTrack) ALL_RELEASES_URL else LATEST_RELEASE_URL

        val request = Request.Builder()
            .url(targetUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "APRSdroid-Mod/${current}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(UpdateCheckResult.Failure(e.message ?: "网络请求失败"))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(UpdateCheckResult.Failure("GitHub 返回 HTTP ${it.code}"))
                        return
                    }
                    val body = it.body.string()

                    val targetRelease = if (isDebugTrack) {
                        findLatestCancryRelease(body)
                    } else {
                        runCatching { JSONObject(body) }.getOrNull()
                    }

                    if (targetRelease == null) {
                        if (isDebugTrack) {
                            // In debug track, if no Cancry release has been published yet, consider it up-to-date
                            callback(UpdateCheckResult.UpToDate(current = current, latest = current))
                        } else {
                            callback(UpdateCheckResult.Failure("无法解析 GitHub 更新信息"))
                        }
                        return
                    }

                    val tag = targetRelease.optString("tag_name")
                    val title = targetRelease.optString("name")
                    val latest = parseAppVersion(tag)
                    val releaseUrl = targetRelease.optString("html_url")
                    val releaseNotes = targetRelease.optString("body").trim().ifBlank { "No release notes provided." }
                    val isSuppressed = isUpdatePromptSuppressed(releaseNotes, title)

                    if (latest == null || releaseUrl.isBlank()) {
                        callback(UpdateCheckResult.Failure("GitHub Release 缺少有效版本信息"))
                        return
                    }

                    if (latest > current) {
                        callback(
                            UpdateCheckResult.UpdateAvailable(
                                current = current,
                                latest = latest,
                                releaseUrl = releaseUrl,
                                deviceArchitecture = formatDeviceArchitecture(Build.SUPPORTED_ABIS),
                                releaseNotes = releaseNotes,
                                isPromptSuppressed = isSuppressed,
                            ),
                        )
                    } else {
                        callback(UpdateCheckResult.UpToDate(current = current, latest = latest))
                    }
                }
            }
        })
    }
}

private val VERSION_REGEX = Regex("(?:^|[^0-9])(\\d+)\\.(\\d+)\\.(\\d+)(?:-ic705)?")
private val CANCRY_REGEX = Regex("Can[ca]ry-(\\d{12})", RegexOption.IGNORE_CASE)
