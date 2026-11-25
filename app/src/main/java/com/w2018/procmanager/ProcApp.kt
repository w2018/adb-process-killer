package com.w2018.procmanager

import android.app.Application
import com.w2018.procmanager.data.SettingsStore
import com.w2018.procmanager.data.AppRepository
import com.w2018.procmanager.data.ChannelManager
import com.w2018.procmanager.core.StatsStore
import com.w2018.procmanager.core.GuardEngine

class ProcApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repo: AppRepository
        private set
    lateinit var channels: ChannelManager
        private set
    lateinit var stats: StatsStore
        private set
    lateinit var engine: GuardEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        stats = StatsStore(this)
        channels = ChannelManager(this, settings)
        repo = AppRepository(this, channels)
        engine = GuardEngine(this)
        engine.start()
    }

    companion object {
        lateinit var instance: ProcApp
            private set
    }
}
