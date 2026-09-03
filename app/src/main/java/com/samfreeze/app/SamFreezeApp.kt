package com.samfreeze.app

import android.app.Application
import com.samfreeze.app.data.PackageRepository
import com.samfreeze.app.data.PreferencesRepository
import com.samfreeze.app.data.StatsRepository
import com.samfreeze.app.data.UadListRepository
import com.samfreeze.app.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SamFreezeApp : Application() {

    lateinit var rootShell: RootShell
        private set
    lateinit var packageRepository: PackageRepository
        private set
    lateinit var preferencesRepository: PreferencesRepository
        private set
    lateinit var statsRepository: StatsRepository
        private set
    lateinit var uadListRepository: UadListRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        rootShell = RootShell.getInstance()
        packageRepository = PackageRepository(applicationContext, rootShell)
        preferencesRepository = PreferencesRepository(applicationContext)
        statsRepository = StatsRepository(rootShell)
        uadListRepository = UadListRepository(applicationContext)

        // One-time, first-install-only download of the full UAD-NG debloat
        // list. Only ever runs until it succeeds once — no periodic or
        // background refreshing after that. Any further updates are
        // strictly user-initiated from the Freeze Levels screen.
        appScope.launch {
            if (!preferencesRepository.uadAutoDownloadDone.first()) {
                val result = uadListRepository.downloadLatest()
                if (result.isSuccess) {
                    preferencesRepository.setUadAutoDownloadDone(true)
                }
            }
        }
    }
}
