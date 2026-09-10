package dev.fquo.liftwear.wear.debuglog

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import dev.fquo.liftwear.wear.LiftWearApplication
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands the debug log to adb, and to nothing else.
 *
 *     adb shell content read --uri content://dev.fquo.liftwear.debuglog/log > liftwear-debug.log
 *
 * A release build is not debuggable, so `run-as` cannot reach the app's files, and backups are
 * off. This is exported, but both of its permissions are `android.permission.DUMP`, which the adb
 * shell holds and no installable app can: the log holds training history, and nothing else on
 * the watch has any business reading it.
 */
class DebugLogProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "text/plain"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("The debug log is read-only")
        val context = context ?: throw FileNotFoundException("No context")
        val app = context.applicationContext as? LiftWearApplication
            ?: throw FileNotFoundException("Not the LiftWear application")
        val export = app.debugLog.export(File(context.cacheDir, "debuglog-adb.txt"), exportHeader(context))
        return ParcelFileDescriptor.open(export, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
