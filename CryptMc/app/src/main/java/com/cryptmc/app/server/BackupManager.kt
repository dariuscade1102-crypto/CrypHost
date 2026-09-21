package com.cryptmc.app.server

import android.content.Context
import android.util.Base64
import com.cryptmc.app.data.BackupRecord
import com.cryptmc.app.data.BackupTrigger
import com.cryptmc.app.data.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Creates, restores, indexes, and prunes local server backups. */
object BackupManager {
    private const val PREFS = "cryptmc_backups"
    private const val RECORDS_KEY = "records"
    private val _backups = MutableStateFlow<List<BackupRecord>>(emptyList())
    val backups: StateFlow<List<BackupRecord>> = _backups.asStateFlow()
    private var prefs: android.content.SharedPreferences? = null
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _backups.value = decode<List<BackupRecord>>(prefs?.getString(RECORDS_KEY, null))
                .orEmpty().filter { File(it.filePath).isFile }
            initialized = true
            persist()
        }
    }

    fun backupsFor(serverId: String): List<BackupRecord> =
        _backups.value.filter { it.serverId == serverId && File(it.filePath).isFile }
            .sortedByDescending { it.createdAtEpochMs }

    suspend fun createBackup(config: ServerConfig, includePlugins: Boolean, trigger: BackupTrigger): Result<BackupRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val workingDir = File(config.workingDir)
            val worldDir = File(workingDir, "world")
            require(worldDir.isDirectory) { "No world folder found at ${worldDir.path}" }
            val backupsDir = File(workingDir, "backups").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
            val slug = config.name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
            val outFile = File(backupsDir, "${slug.ifBlank { "server" }}-$stamp-${UUID.randomUUID().toString().take(8)}.zip")
            ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
                addDirToZip(zip, worldDir, "world")
                listOf("world_nether", "world_the_end").forEach { name -> File(workingDir, name).takeIf { it.isDirectory }?.let { addDirToZip(zip, it, name) } }
                File(workingDir, "server.properties").takeIf { it.isFile }?.let { addFileToZip(zip, it, "server.properties") }
                if (includePlugins) listOf("plugins", "mods").forEach { name -> File(workingDir, name).takeIf { it.isDirectory }?.let { addDirToZip(zip, it, name) } }
            }
            val record = BackupRecord(UUID.randomUUID().toString(), config.id, outFile.name, outFile.absolutePath, System.currentTimeMillis(), outFile.length(), trigger)
            _backups.value = _backups.value.filterNot { it.filePath == record.filePath } + record
            pruneOldBackups(config.id, config.schedule.backupRetentionCount)
            persist()
            record
        }
    }

    suspend fun restoreBackup(record: BackupRecord, workingDir: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(File(record.filePath).isFile) { "Backup file is missing: ${record.filePath}" }
            val target = File(workingDir)
            val root = target.canonicalFile
            ZipFile(record.filePath).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val out = File(root, entry.name).canonicalFile
                    require(out == root || out.path.startsWith(root.path + File.separator)) { "Backup entry escapes target: ${entry.name}" }
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input -> out.outputStream().use { output -> input.copyTo(output) } }
                    }
                }
            }
        }
    }

    fun deleteBackup(record: BackupRecord) {
        File(record.filePath).delete()
        _backups.value = _backups.value.filterNot { it.id == record.id }
        persist()
    }

    private fun pruneOldBackups(serverId: String, keep: Int) {
        if (keep < 1) return
        backupsFor(serverId).filter { it.trigger == BackupTrigger.SCHEDULED }.drop(keep).forEach { deleteBackup(it) }
    }

    private fun addDirToZip(zip: ZipOutputStream, dir: File, prefix: String) {
        dir.walkTopDown().filter { it.isFile }.forEach { file -> addFileToZip(zip, file, "$prefix/${file.relativeTo(dir).path.replace(File.separatorChar, '/')}") }
    }
    private fun addFileToZip(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
    }
    private fun persist() { prefs?.edit()?.putString(RECORDS_KEY, encode(_backups.value))?.apply() }
    private fun encode(value: Any): String = runCatching {
        val bytes = ByteArrayOutputStream(); ObjectOutputStream(bytes).use { it.writeObject(value) }
        Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }.getOrDefault("")
    @Suppress("UNCHECKED_CAST")
    private fun <T> decode(value: String?): T? = runCatching {
        if (value.isNullOrBlank()) return null
        ObjectInputStream(ByteArrayInputStream(Base64.decode(value, Base64.DEFAULT))).use { it.readObject() as T }
    }.getOrNull()
}
