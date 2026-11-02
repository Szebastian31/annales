package com.annales.journal.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup files in Kalendae's format: { app, schema, exported } and either the state in the clear, or
 * locked: AES-256-GCM with a key from PBKDF2-SHA256 (250 000 rounds, 16-byte salt, 12-byte IV), all base64.
 * The state holds journal.json, collections.json, the settings, and every photo and stamp as base64.
 */
object Backup {
    private const val SCHEMA = 1
    private val DIRS = listOf("photos", "stamps", "originals")
    private val FILES = listOf("journal.json", "collections.json")

    class WrongPassword : Exception()
    class NotABackup : Exception()

    fun write(context: Context, to: Uri, password: String?) {
        val root = context.filesDir
        val state = JSONObject()
        FILES.forEach { f -> File(root, f).takeIf { it.exists() }?.let { state.put(f, it.readText()) } }
        val prefs = context.getSharedPreferences("annales", Context.MODE_PRIVATE)
        state.put("settings", JSONObject(prefs.all.mapValues { it.value.toString() }))
        val files = JSONObject()
        DIRS.forEach { dir ->
            File(root, dir).listFiles()?.forEach { f -> files.put("$dir/${f.name}", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)) }
        }
        state.put("files", files)

        val head = JSONObject().put("app", "annales").put("schema", SCHEMA).put("exported", Instant.now().toString())
        val out = if (password.isNullOrEmpty()) head.put("state", state) else {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
            head.put("locked", true).put("salt", b64(salt)).put("iv", b64(iv)).put("data", b64(c.doFinal(state.toString().toByteArray())))
        }
        context.contentResolver.openOutputStream(to, "wt")?.use { it.write(out.toString().toByteArray()) }
    }

    /** True if the file needs a password. Throws [NotABackup] for anything that isn't an Annales backup. */
    fun locked(context: Context, from: Uri): Boolean = read(context, from).optBoolean("locked")

    /** Replaces everything on this phone with the backup. Throws [WrongPassword] or [NotABackup]. */
    fun restore(context: Context, from: Uri, password: String?) {
        val o = read(context, from)
        val state = if (!o.optBoolean("locked")) o.getJSONObject("state") else {
            if (password.isNullOrEmpty()) throw WrongPassword()
            try {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key(password, unb64(o.getString("salt"))), GCMParameterSpec(128, unb64(o.getString("iv"))))
                JSONObject(String(c.doFinal(unb64(o.getString("data")))))
            } catch (e: Exception) { throw WrongPassword() }
        }
        val root = context.filesDir
        DIRS.forEach { dir -> File(root, dir).apply { listFiles()?.forEach { it.delete() }; mkdirs() } }
        state.optJSONObject("files")?.let { files ->
            files.keys().forEach { k -> if (DIRS.any { k.startsWith("$it/") } && !k.contains("..")) File(root, k).writeBytes(unb64(files.getString(k))) }
        }
        FILES.forEach { f -> if (state.has(f)) File(root, f).writeText(state.getString(f)) else File(root, f).delete() }
        state.optJSONObject("settings")?.let { s ->
            val bools = setOf("saveToPhotos", "reminder", "keepOriginals", "haptics", "cleanedGallery")
            context.getSharedPreferences("annales", Context.MODE_PRIVATE).edit().apply {
                clear()
                s.keys().forEach { k -> if (k in bools) putBoolean(k, s.getString(k).toBoolean()) else putString(k, s.getString(k)) }
            }.commit()
        }
    }

    private fun read(context: Context, from: Uri): JSONObject {
        val text = context.contentResolver.openInputStream(from)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: throw NotABackup()
        val o = runCatching { JSONObject(text) }.getOrNull() ?: throw NotABackup()
        if (o.optString("app") != "annales") throw NotABackup()
        return o
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 250_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.DEFAULT)
}
