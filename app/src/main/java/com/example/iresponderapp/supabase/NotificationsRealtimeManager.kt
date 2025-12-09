package com.example.iresponderapp.supabase

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages realtime notifications subscription via Supabase Realtime.
 * Notifies listeners when new notifications arrive.
 */
class NotificationsRealtimeManager(
    private val client: SupabaseClient,
    private val notificationsRepo: SupabaseNotificationsRepository
) {
    companion object {
        private const val TAG = "NotificationsRealtime"
    }

    /**
     * Listener interface for notification events
     */
    interface Listener {
        fun onNewNotification(notification: AppNotification)
        fun onUnreadCountChanged(count: Int)
    }

    private val listeners = mutableListOf<Listener>()
    private var subscriptionJob: Job? = null
    private var isSubscribed = false

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Start listening for realtime notifications.
     * Call this after user logs in.
     */
    fun start() {
        if (isSubscribed) {
            Log.d(TAG, "Already subscribed to realtime notifications")
            return
        }

        val userId = client.auth.currentUserOrNull()?.id
        if (userId == null) {
            Log.w(TAG, "Cannot start realtime: user not authenticated")
            return
        }

        subscriptionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val channelName = "notifications_$userId"
                val channel = client.channel(channelName)

                // Subscribe to INSERT events on notifications table for this user
                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "notifications"
                    filter("recipient_id", FilterOperator.EQ, userId)
                }.onEach { change ->
                    Log.d(TAG, "Received realtime notification insert")
                    handleNewNotification(change)
                }.launchIn(this)

                // Subscribe to the channel
                channel.subscribe()
                isSubscribed = true
                Log.d(TAG, "Subscribed to realtime notifications for user: $userId")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to subscribe to realtime: ${e.message}", e)
            }
        }
    }

    /**
     * Stop listening for realtime notifications.
     * Call this when user logs out.
     */
    fun stop() {
        subscriptionJob?.cancel()
        subscriptionJob = null
        isSubscribed = false
        Log.d(TAG, "Stopped realtime notifications subscription")
    }

    private suspend fun handleNewNotification(change: PostgresAction.Insert) {
        try {
            // Parse the notification from the change record
            val record = change.record
            val notification = parseNotificationFromRecord(record)

            if (notification != null) {
                // Notify all listeners on main thread
                withContext(Dispatchers.Main) {
                    listeners.forEach { it.onNewNotification(notification) }
                }

                // Also update unread count
                val count = notificationsRepo.getUnreadCount()
                withContext(Dispatchers.Main) {
                    listeners.forEach { it.onUnreadCountChanged(count) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification: ${e.message}", e)
        }
    }

    private fun parseNotificationFromRecord(record: Map<String, Any?>): AppNotification? {
        return try {
            AppNotification(
                id = when (val idVal = record["id"]) {
                    is Number -> idVal.toLong()
                    is String -> idVal.toLongOrNull() ?: 0L
                    else -> 0L
                },
                recipientId = record["recipient_id"]?.toString() ?: "",
                incidentId = record["incident_id"]?.toString(),
                title = record["title"]?.toString() ?: "",
                body = record["body"]?.toString() ?: "",
                isRead = record["is_read"] as? Boolean ?: false,
                createdAt = record["created_at"]?.toString() ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse notification: ${e.message}", e)
            null
        }
    }

    /**
     * Manually refresh unread count and notify listeners.
     * Useful for initial load or after marking notifications as read.
     */
    fun refreshUnreadCount() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val count = notificationsRepo.getUnreadCount()
                withContext(Dispatchers.Main) {
                    listeners.forEach { it.onUnreadCountChanged(count) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh unread count: ${e.message}", e)
            }
        }
    }
}
