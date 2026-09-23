package com.miladtak.japo.backup

interface BackupManager {
    fun createBackup(projectId: String): String
}
