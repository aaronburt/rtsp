package uk.co.aaronburt.rtsp.ui.settings

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.aaronburt.rtsp.MainActivity

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 35])
class AppIconManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = context.packageManager

    private fun launcherNames() = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName),
        0,
    ).map { it.activityInfo.name }

    @Test
    fun freshInstallHasOnlyTheViewfinderLauncher() {
        assertEquals(AppIcon.Viewfinder, AppIconManager(context).current)
        assertEquals(listOf("${context.packageName}.LauncherViewfinder"), launcherNames())
    }

    @Test
    fun everySelectionReplacesTheLauncherAndSurvivesManagerRecreation() {
        val manager = AppIconManager(context)
        for (icon in AppIcon.entries.reversed() + AppIcon.Viewfinder) {
            manager.select(icon)
            val expectedName = "${context.packageName}.${icon.alias}"
            assertEquals(listOf(expectedName), launcherNames())
            assertEquals(expectedName, packageManager.getLaunchIntentForPackage(context.packageName)?.component?.className)
            assertEquals(icon, AppIconManager(context).current)
            val alias = packageManager.getActivityInfo(ComponentName(context.packageName, expectedName), 0)
            assertEquals("${context.packageName}.MainActivity", alias.targetActivity)
            assertTrue(packageManager.getActivityInfo(
                ComponentName(context.packageName, "${context.packageName}.MainActivity"), 0,
            ).enabled)
        }
    }

    @Test
    fun launcherAliasesOpenTheVisibleMainActivityDirectly() {
        val main = ComponentName(context, MainActivity::class.java).className
        for (icon in AppIcon.entries) {
            val alias = packageManager.getActivityInfo(
                ComponentName(context.packageName, "${context.packageName}.${icon.alias}"),
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
            assertEquals(main, alias.targetActivity)
            assertTrue(alias.exported)
        }
        // MainActivity is only reachable through the launcher aliases.
        assertFalse(packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0).exported)
    }

    /**
     * Guards against reintroducing the v0.7.0 launcher trampoline. An invisible activity that
     * hides from recents, combined with toggling launcher components at runtime, matches
     * icon-hiding malware heuristics and is the likely reason Play Protect flagged v0.7.0 on install.
     */
    @Test
    fun noDeclaredActivityIsInvisibleOrHiddenFromRecents() {
        val activities = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS,
        ).activities.orEmpty()
        assertTrue(activities.isNotEmpty())
        for (activity in activities) {
            assertTrue("${activity.name} uses Theme.NoDisplay", activity.theme != android.R.style.Theme_NoDisplay)
            assertTrue(
                "${activity.name} is excluded from recents",
                activity.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS == 0,
            )
        }
    }

    @Test
    fun everyChoiceHasAdaptiveAndThemedArtwork() {
        for (icon in AppIcon.entries) {
            val drawable = ContextCompat.getDrawable(context, icon.image)
            assertTrue(drawable is AdaptiveIconDrawable)
            drawable as AdaptiveIconDrawable
            assertNotNull(drawable.background)
            assertNotNull(drawable.foreground)
            if (Build.VERSION.SDK_INT >= 33) assertNotNull(drawable.monochrome)
        }
    }
}
