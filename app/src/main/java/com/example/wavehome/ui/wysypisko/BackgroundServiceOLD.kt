package com.example.wavehome.ui.wysypisko


//    OLD
//    override fun onCreate() {
//        super.onCreate()
//        db = Room.databaseBuilder(
//            applicationContext,
//            AppDatabase::class.java,
//            "time_log.db"
//        ).build()
//
//        createNotificationChannel()
//    }

//OLD
//    private fun scheduleNextTask() {
//        serviceScope.launch {
//            // Zapisz do bazy
//            db.tieeLogDao().insert(TimeLog(timestamp = System.currentTimeMillis()))
//
//            // Stary wariant cyklu tła został zastąpiony przez SmartHomeSyncWorker.
//        }
//    }
