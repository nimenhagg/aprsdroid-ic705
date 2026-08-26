package org.aprsdroid.app.update

import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

internal data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"
}

internal fun parseAppVersion(value: String): AppVersion? {
    val match = VERSION_REGEX.find(value) ?: return null
    return AppVersion(
        major = match.groupValues[1].toIntOrNull() ?: return null,
        minor = match.groupValues[2].toIntOrNull() ?: return null,
        patch = match.groupValues[3].toIntOrNull() ?: return null,
    )
}

internal sealed interface UpdateCheckResult {
    data class UpToDate(val current: AppVersion, val latest: AppVersion) : UpdateCheckResult
    data class UpdateAvailable(
        val current: AppVersion,
        val latest: AppVersion,
        val releaseUrl: String,
    ) : UpdateCheckResult
    data class Failure(val message: String) : UpdateCheckResult
}

/**
 * One-shot update checker. It never schedules work and never runs unless [check] is called.
 */
internal object GitHubUpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/nimenhagg/aprsdroid-ic705/releases/latest"

    private val client by lazy { OkHttpClient() }

    fun check(currentVersionName: String, callback: (UpdateCheckResult) -> Unit) {
        val current = parseAppVersion(currentVersionName)
        if (current == null) {
            callback(UpdateCheckResult.Failure("无法识别当前版本：$currentVersionName"))
            return
        }

        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
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
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    if (json == null) {
                        callback(UpdateCheckResult.Failure("无法解析 GitHub 更新信息"))
                        return
                    }
                    val tag = json.optString("tag_name")
                    val latest = parseAppVersion(tag)
                    val releaseUrl = json.optString("html_url")
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
