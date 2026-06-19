package com.codeup.util

import com.codeup.settings.CodeupSettings
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.BrowserUtil
import com.intellij.notification.*
import com.intellij.openapi.project.Project
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class UpdateChecker(private val project: Project, private val currentVersion: String) {

    private val mapper = jacksonObjectMapper()
    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    fun checkOnActivation() {
        val settings = CodeupSettings.getInstance().state
        if (!settings.updateCheckEnabled) return
        val lastChecked = PropertiesHelper.getLong("codeup.updateCheck.lastChecked", 0L)
        val intervalMs = settings.updateCheckIntervalHours * 3_600_000L
        if (!dueForCheck(lastChecked, intervalMs, System.currentTimeMillis())) return
        checkNow()
    }

    fun checkNow() {
        Thread {
            try {
                PropertiesHelper.setLong("codeup.updateCheck.lastChecked", System.currentTimeMillis())
                val req = Request.Builder()
                    .url("https://api.github.com/repos/johncoleman-thoughtworks/codeup-idea/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "codeup-intellij-plugin/$currentVersion")
                    .build()
                val response = http.newCall(req).execute()
                if (!response.isSuccessful) return@Thread
                @Suppress("UNCHECKED_CAST")
                val raw = mapper.readValue(response.body?.string() ?: "{}", Map::class.java) as Map<String, Any?>
                val release = parseRelease(raw) ?: return@Thread
                if (release.prerelease) return@Thread
                if (!isNewer(release.tag, currentVersion)) return@Thread
                val dismissed = PropertiesHelper.getString("codeup.updateCheck.dismissedTag", "")
                if (dismissed == release.tag) return@Thread
                showUpdateNotification(release)
            } catch (e: Exception) { /* silent */ }
        }.also { it.isDaemon = true }.start()
    }

    private fun showUpdateNotification(release: ParsedRelease) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup("Codeup") ?: return
        val notification = group.createNotification(
            "Codeup ${release.tag} is available",
            "A new version of the Codeup plugin is available.",
            NotificationType.INFORMATION,
        )
        notification.addAction(NotificationAction.createSimple("View Release") {
            BrowserUtil.browse(release.htmlUrl)
            notification.expire()
        })
        notification.addAction(NotificationAction.createSimple("Dismiss") {
            PropertiesHelper.setString("codeup.updateCheck.dismissedTag", release.tag)
            notification.expire()
        })
        notification.notify(project)
    }
}

object PropertiesHelper {
    private val props = com.intellij.ide.util.PropertiesComponent.getInstance()
    fun getLong(key: String, default: Long) = props.getLong(key, default)
    fun setLong(key: String, v: Long) = props.setValue(key, v.toString())
    fun getString(key: String, default: String) = props.getValue(key, default)
    fun setString(key: String, v: String) = props.setValue(key, v)
}