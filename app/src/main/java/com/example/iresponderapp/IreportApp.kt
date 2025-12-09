package com.example.iresponderapp

import android.app.Application
import com.example.iresponderapp.supabase.AuthRepository
import com.example.iresponderapp.supabase.IncidentsRepository
import com.example.iresponderapp.supabase.NotificationsRepository
import com.example.iresponderapp.supabase.NotificationsRealtimeManager
import com.example.iresponderapp.supabase.ResponderProfileRepository
import com.example.iresponderapp.supabase.SupabaseAuthRepository
import com.example.iresponderapp.supabase.SupabaseIncidentsRepository
import com.example.iresponderapp.supabase.SupabaseNotificationsRepository
import com.example.iresponderapp.supabase.SupabaseResponderProfileRepository
import com.example.iresponderapp.supabase.SupabaseUnitReportsRepository
import com.example.iresponderapp.supabase.SupabaseFinalReportDraftsRepository
import com.example.iresponderapp.supabase.SupabaseStorageRepository
import com.example.iresponderapp.supabase.UnitReportsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

class IreportApp : Application() {

    lateinit var supabaseClient: SupabaseClient
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var responderProfileRepository: ResponderProfileRepository
        private set

    lateinit var incidentsRepository: IncidentsRepository
        private set

    lateinit var unitReportsRepository: UnitReportsRepository
        private set

    lateinit var notificationsRepository: SupabaseNotificationsRepository
        private set

    lateinit var notificationsRealtimeManager: NotificationsRealtimeManager
        private set

    lateinit var finalReportDraftsRepository: SupabaseFinalReportDraftsRepository
        private set

    lateinit var storageRepository: SupabaseStorageRepository
        private set

    override fun onCreate() {
        super.onCreate()
        supabaseClient = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }

        authRepository = SupabaseAuthRepository(supabaseClient)
        responderProfileRepository = SupabaseResponderProfileRepository(supabaseClient)
        incidentsRepository = SupabaseIncidentsRepository(supabaseClient)
        unitReportsRepository = SupabaseUnitReportsRepository(supabaseClient)
        notificationsRepository = SupabaseNotificationsRepository(supabaseClient)
        notificationsRealtimeManager = NotificationsRealtimeManager(supabaseClient, notificationsRepository)
        finalReportDraftsRepository = SupabaseFinalReportDraftsRepository(supabaseClient)
        storageRepository = SupabaseStorageRepository(supabaseClient)
    }

    /**
     * Start realtime notifications subscription.
     * Call this after user successfully logs in.
     */
    fun startRealtimeNotifications() {
        notificationsRealtimeManager.start()
    }

    /**
     * Stop realtime notifications subscription.
     * Call this when user logs out.
     */
    fun stopRealtimeNotifications() {
        notificationsRealtimeManager.stop()
    }
}
