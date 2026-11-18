package com.example.data.sync

import com.example.data.local.ContactEntity
import com.example.domain.model.ContactRecord
import com.example.domain.model.SyncState
import com.example.util.ContactDeduplicator

enum class ConflictResolution {
    KEEP_LOCAL_PUSH,
    ACCEPT_REMOTE,
    MERGE_AND_PUSH,
}

/**
 * Deterministic timestamp + pending-outbox conflict resolver for Offline-First Two-Way Sync.
 * Ensures unpushed local mutations or soft-delete tombstones are never overwritten by stale cloud snapshots.
 */
object SyncConflictResolver {

    fun resolve(
        localUpdatedAtMs: Long,
        remoteUpdatedAtMs: Long,
        hasPendingLocalEdit: Boolean,
        localIsDeleted: Boolean,
    ): ConflictResolution {
        if (localIsDeleted) {
            return ConflictResolution.KEEP_LOCAL_PUSH
        }
        if (hasPendingLocalEdit) {
            return when {
                remoteUpdatedAtMs > localUpdatedAtMs + 5_000L -> ConflictResolution.MERGE_AND_PUSH
                else -> ConflictResolution.KEEP_LOCAL_PUSH
            }
        }
        return if (remoteUpdatedAtMs > localUpdatedAtMs) {
            ConflictResolution.ACCEPT_REMOTE
        } else {
            ConflictResolution.ACCEPT_REMOTE
        }
    }

    fun resolveContact(
        local: ContactEntity?,
        remote: ContactRecord,
        hasPendingLocalEdit: Boolean,
    ): Pair<ContactEntity, Boolean> {
        val remoteUpdatedMs = remote.clientUpdatedAtMs.takeIf { it > 0L }
            ?: remote.updatedAt?.toDate()?.time
            ?: System.currentTimeMillis()

        if (local == null) {
            return ContactEntity.fromDomain(
                record = remote.copy(syncedToDevice = false),
                nowMs = remoteUpdatedMs,
                syncState = SyncState.SYNCED,
            ) to false
        }

        val resolution = resolve(
            localUpdatedAtMs = local.updatedAtEpochMs,
            remoteUpdatedAtMs = remoteUpdatedMs,
            hasPendingLocalEdit = hasPendingLocalEdit,
            localIsDeleted = local.isDeleted,
        )

        return when (resolution) {
            ConflictResolution.KEEP_LOCAL_PUSH -> {
                local.copy(syncState = SyncState.PENDING.name) to true
            }
            ConflictResolution.ACCEPT_REMOTE -> {
                val mergedPhoto = remote.photoUri?.takeIf { it.isNotBlank() } ?: local.photoUri
                ContactEntity.fromDomain(
                    record = remote.copy(
                        syncedToDevice = false,
                        photoUri = mergedPhoto,
                        clientUpdatedAtMs = remoteUpdatedMs,
                    ),
                    nowMs = remoteUpdatedMs,
                    syncState = SyncState.SYNCED,
                ) to false
            }
            ConflictResolution.MERGE_AND_PUSH -> {
                val localDomain = local.toDomain()
                val merged = ContactDeduplicator.deduplicateAndMerge(
                    listOf(remote.copy(id = local.id, syncedToDevice = false), localDomain),
                    enforceBilingual = false,
                ).mergedContacts.firstOrNull() ?: localDomain
                val latestMs = maxOf(local.updatedAtEpochMs, remoteUpdatedMs) + 1L
                ContactEntity.fromDomain(
                    record = merged.copy(
                        id = local.id,
                        syncedToDevice = false,
                        isFavorite = localDomain.isFavorite || remote.isFavorite,
                        blocked = localDomain.blocked,
                        muted = localDomain.muted,
                        lastContactedEpochMs = maxOf(localDomain.lastContactedEpochMs, remote.lastContactedEpochMs),
                        clientUpdatedAtMs = latestMs,
                    ),
                    nowMs = latestMs,
                    syncState = SyncState.PENDING,
                ) to true
            }
        }
    }
}
