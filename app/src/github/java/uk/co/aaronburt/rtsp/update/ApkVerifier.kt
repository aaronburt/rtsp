package uk.co.aaronburt.rtsp.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

sealed interface VerifyResult {
    data object Ok : VerifyResult
    data class Rejected(val reason: String) : VerifyResult
}

object CertFingerprint {
    fun sha256(encoded: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }

    fun sameSigners(installed: Set<String>, candidate: Set<String>): Boolean =
        installed.isNotEmpty() && installed == candidate
}

object ApkVerifier {
    fun verify(context: Context, apk: File): VerifyResult {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: return VerifyResult.Rejected("The downloaded file is not a valid APK")
        if (archive.packageName != context.packageName) {
            return VerifyResult.Rejected("The downloaded APK is for a different app")
        }
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            return VerifyResult.Rejected("The downloaded APK is not newer than the installed app")
        }
        if (!CertFingerprint.sameSigners(signers(installed), signers(archive))) {
            return VerifyResult.Rejected("The downloaded APK is signed with a different key than the installed app")
        }
        return VerifyResult.Ok
    }

    private fun signers(info: PackageInfo): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures.orEmpty().map { CertFingerprint.sha256(it.toByteArray()) }.toSet()
    }
}
