package com.samfreeze.app

import android.app.Application
import com.samfreeze.app.data.PackageRepository
import com.samfreeze.app.data.PreferencesRepository
import com.samfreeze.app.data.StatsRepository
import com.samfreeze.app.data.UadListRepository
import com.samfreeze.app.root.RootShell

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

    override fun onCreate() {
        super.onCreate()
        rootShell = RootShell.getInstance()
        packageRepository = PackageRepository(applicationContext, rootShell)
        preferencesRepository = PreferencesRepository(applicationContext)
        statsRepository = StatsRepository(rootShell)
        uadListRepository = UadListRepository(applicationContext)
    }
}
