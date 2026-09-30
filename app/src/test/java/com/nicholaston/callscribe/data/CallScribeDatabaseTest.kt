package com.nicholaston.callscribe.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CallScribeDatabaseTest {
    private lateinit var database: CallScribeDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CallScribeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `FTS search returns the owning call and snippet`() = runBlocking {
        val callId = database.callDao().insert(
            CallRecord(
                source = CallSource.IMPORT,
                startedAt = 123,
                direction = CallDirection.INCOMING,
            ),
        )
        database.transcriptDao().insert(
            TranscriptSegment(
                callId = callId,
                index = 0,
                startMs = 0,
                endMs = 1_000,
                text = "The project update is ready",
            ),
        )

        val results = database.callDao().searchTranscript("\"project\"*").first()

        assertEquals(listOf(callId), results.map { it.call.id })
        assertEquals("The [project] update is ready", results.single().snippet)
    }
}
