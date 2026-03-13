package io.github.dioxd_dev.twa.plugins

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.getcapacitor.*
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import org.json.JSONArray
import org.json.JSONObject

@CapacitorPlugin(
    name = "MusicScanner",
    permissions = [
        Permission(strings = [Manifest.permission.READ_EXTERNAL_STORAGE], alias = "publicStorage"),
        Permission(strings = ["android.permission.READ_MEDIA_AUDIO"], alias = "mediaAudio")
    ]
)
class MusicScannerPlugin : Plugin() {

    @PluginMethod
    fun requestPermission(call: PluginCall) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            "android.permission.READ_MEDIA_AUDIO"
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        val status = ContextCompat.checkSelfPermission(context, permission)
        if (status == PackageManager.PERMISSION_GRANTED) {
            call.resolve(JSObject().put("status", "granted")); return
        }
        requestPermissionForAlias(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "mediaAudio" else "publicStorage",
            call, "onPermissionResult"
        )
    }

    @PermissionCallback
    private fun onPermissionResult(call: PluginCall) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            "android.permission.READ_MEDIA_AUDIO"
        else Manifest.permission.READ_EXTERNAL_STORAGE
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        call.resolve(JSObject().put("status", if (granted) "granted" else "denied"))
    }

    @PluginMethod
    fun scan(call: PluginCall) {
        val minDuration = call.getLong("minDurationMs", 30000L) ?: 30000L
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val songs = JSONArray()
        try {
            val cursor = context.contentResolver.query(
                collection, projection, selection,
                arrayOf(minDuration.toString()),
                "${MediaStore.Audio.Media.TITLE} ASC"
            )
            cursor?.use { c ->
                val idCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val nameCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (c.moveToNext()) {
                    songs.put(JSONObject().apply {
                        put("id",       c.getLong(idCol))
                        put("title",    c.getString(titleCol)  ?: "")
                        put("artist",   c.getString(artistCol) ?: "")
                        put("album",    c.getString(albumCol)  ?: "")
                        put("duration", c.getLong(durCol) / 1000.0)
                        put("size",     c.getLong(sizeCol))
                        put("mimeType", c.getString(mimeCol)   ?: "audio/mpeg")
                        put("path",     c.getString(dataCol)   ?: "")
                        put("fileName", c.getString(nameCol)   ?: "")
                    })
                }
            }
        } catch (e: Exception) {
            call.reject("Scan gagal: ${e.message}"); return
        }
        call.resolve(JSObject().put("songs", songs))
    }
}
