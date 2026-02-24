package com.netswiss.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class AppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable?,
    val installTime: Long,
    val updateTime: Long,
    val permissions: List<String>,
    val apkPath: String,
    val apkSize: Long,
    val isSplit: Boolean,
    val splitPaths: List<String>
)

object InstalledAppsProvider {

    fun getInstalledUserApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        return packages
            .filter { it.applicationInfo != null && !isSystemApp(it.applicationInfo!!) }
            .mapNotNull { pkg -> packageInfoToAppInfo(pm, pkg) }
            .sortedBy { it.name.lowercase() }
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    private fun packageInfoToAppInfo(pm: PackageManager, pkg: PackageInfo): AppInfo? {
        val appInfo = pkg.applicationInfo ?: return null
        val name = pm.getApplicationLabel(appInfo).toString()
        val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null }
        val permissions = pkg.requestedPermissions?.toList() ?: emptyList()

        val apkFile = java.io.File(appInfo.sourceDir)
        val apkSize = apkFile.length()

        val splitPaths = appInfo.splitSourceDirs?.toList() ?: emptyList()
        val isSplit = splitPaths.isNotEmpty()

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkg.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkg.versionCode.toLong()
        }

        return AppInfo(
            name = name,
            packageName = pkg.packageName,
            versionName = pkg.versionName ?: "Unknown",
            versionCode = versionCode,
            icon = icon,
            installTime = pkg.firstInstallTime,
            updateTime = pkg.lastUpdateTime,
            permissions = permissions,
            apkPath = appInfo.sourceDir,
            apkSize = apkSize,
            isSplit = isSplit,
            splitPaths = splitPaths
        )
    }

    fun getAppInfo(context: Context, packageName: String): AppInfo? {
        val pm = context.packageManager
        val pkg = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return packageInfoToAppInfo(pm, pkg)
    }
}
