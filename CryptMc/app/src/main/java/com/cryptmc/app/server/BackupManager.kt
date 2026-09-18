package com.cryptmc.app.server

import com.cryptmc.app.data.BackupRecord
import com.cryptmc.app.data.BackupTrigger
import com.cryptmc.app.data.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Zips a server's world (and optionally plugins/mods, config files) into
 * `<workingDir>/backups/`, and prunes old ones per ScheduleConfig's
 * retention count. Manual backups (Backups tab "Back up now" button) and
 * scheduled ones (ServerScheduler) both go through [createBackup].
 *
 * In-memory index like ServerRepository/AdminRepository elsewhere in this
 * scaffold — swap for a Room table keyed by serverId if you need the list
 * to survive a process death (the zip files on disk already do).
 */
object BackupManager {

    private val _backups = MutableStateFlow<List<BackupRecord>>(emptyList())
    val backups: StateFlow<List<BackupRecord>> = _backups.asStateFlow()

    fun backupsFor(serverId: String): List<BackupRecord> =
        _backups.value.filter { it.serverId == serverId }.sortedByDescending { it.createdAtEpochMs }

    /**
     * Must be called with the server STOPPED (or at minimum with `save-off` +
     * `save-all` issued via RCON/console first) — zipping a live world folder
     * risks a corrupt region file if a chunk is mid-write. Wire that check
     * into ServerScheduler / the tab's "Back up now" handler, not here, since
     * this class has no reference to ServerProcessManager's running state.
     */
    suspend fun createBackup(
        config: ServerConfig,
        includePlugins: Boolean,
        trigger: BackupTrigger
    ): Result<BackupRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val workingDir = File(config.workingDir)
            val worldDir = File(workingDir, "world")
            require(worldDir.exists()) { "No world folder found at ${worldDir.path}" }

            val backupsDir = File(workingDir, "backups").apply { mkdirs() }
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm").format(java.util.Date())
            val fileName = "${config.name.lowercase().replace(Regex("\\s+"), "-")}-$timestamp.zip"
            val outFile = File(backupsDir, fileName)

            ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
                addDirToZip(zip, worldDir, "world")
                listOf("world_nether", "world_the_end").forEach { name ->
                    File(workingDir, name).takeIf { it.exists() }?.let { addDirToZip(zip, it, name) }
                }
                File(workingDir, "server.properties").takeIf { it.exists() }
                    ?.let { addFileToZip(zip, it, "server.properties") }
                if (includePlugins) {
                    listOf("plugins", "mods").forEach { name ->
                        File(workingDir, name).takeIf { it.exists() }?.let { addDirToZip(zip, it, name) }
                    }
                }
            }

            val record = BackupRecord(
                id = UUID.randomUUID().toString(),
                serverId = config.id,
                fileName = fileName,
                filePath = outFile.path,
                createdAtEpochMs = System.currentTimeMillis(),
                sizeBytes = outFile.length(),
                trigger = trigger
            )
            _backups.value = _backups.value + record
            pruneOldBackups(config.id, config.schedule.backupRetentionCount)
            record
        }
    }

    suspend fun restoreBackup(record: BackupRecord, workingDir: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(workingDir)
                java.util.zip.ZipFile(record.filePath).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        val outFile = File(target, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                outFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
            }
        }

    fun deleteBackup(record: BackupRecord) {
        File(record.filePath).delete()
        _backups.value = _backups.value.filterNot { it.id == record.id }
    }

    /** Keeps the newest [keep] backups for a server (scheduled ones only — MANUAL/PRE_UPDATE are exempt). */
    private fun pruneOldBackups(serverId: String, keep: Int) {
        if (keep <= 0) return
        val scheduled = backupsFor(serverId).filter { it.trigger == BackupTrigger.SCHEDULED }
        scheduled.drop(keep).forEach { deleteBackup(it) }
    }

    private fun addDirToZip(zip: ZipOutputStream, dir: File, entryPrefix: String) {
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = "$entryPrefix/${file.relativeTo(dir).path.replace(File.separatorChar, '/')}"
            addFileToZip(zip, file, relative)
        }
    }

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
