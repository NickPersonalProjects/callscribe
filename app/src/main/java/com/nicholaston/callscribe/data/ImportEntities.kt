package com.nicholaston.callscribe.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "watched_folders", indices = [Index(value = ["tree_uri"], unique = true)])
data class WatchedFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tree_uri") val treeUri: String,
    val label: String,
    val parser: String = "AUTO",
    @ColumnInfo(name = "last_scan_at") val lastScanAt: Long? = null,
)

@Entity(
    tableName = "imported_files",
    indices = [Index(value = ["document_uri", "size", "last_modified"], unique = true)],
)
data class ImportedFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_uri") val documentUri: String,
    val size: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    @ColumnInfo(name = "call_id") val callId: Long? = null,
)
