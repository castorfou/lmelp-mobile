package com.lmelp.mobile.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Issue de la demande d'installation d'un APK téléchargé (issue #162). */
enum class ApkInstallOutcome {
    /** L'installateur Android est affiché ; l'utilisateur confirme (ou annule) lui-même. */
    InstallerLaunched,

    /** LMELP n'a pas encore le droit d'installer des applications : l'écran de réglage a été ouvert. */
    NeedsPermission
}

/**
 * Lance l'installateur Android sur un APK de `<cacheDir>/updates/` (issue #162).
 * Requiert `REQUEST_INSTALL_PACKAGES` et le FileProvider déclarés dans le manifeste.
 * Android refuse la mise à jour si l'APK n'est pas signé avec la même clé que l'appli installée.
 */
object ApkInstaller {
    fun install(context: Context, apk: File): ApkInstallOutcome {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return ApkInstallOutcome.NeedsPermission
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(install)
        return ApkInstallOutcome.InstallerLaunched
    }
}
