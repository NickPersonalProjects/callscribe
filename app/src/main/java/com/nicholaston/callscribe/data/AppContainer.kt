package com.nicholaston.callscribe.data

import android.content.Context

class AppContainer(context: Context) {
    val database: CallScribeDatabase = CallScribeDatabase.get(context)
    val settings = CallScribeSettings(context)
    val callRepository = CallRepository(database.callDao(), database.transcriptDao())
}
