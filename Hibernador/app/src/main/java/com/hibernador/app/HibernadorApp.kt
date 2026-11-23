package com.hibernador.app

import android.app.Application
import com.hibernador.app.data.LastResultStore
import com.hibernador.app.data.RulesRepository
import com.hibernador.app.shizuku.ShizukuManager

class HibernadorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RulesRepository.init(this)
        LastResultStore.init(this)
        ShizukuManager.init(this)
    }
}
