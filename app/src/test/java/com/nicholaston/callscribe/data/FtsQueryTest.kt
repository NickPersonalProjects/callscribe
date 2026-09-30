package com.nicholaston.callscribe.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FtsQueryTest {
    @Test
    fun `terms are quoted and prefix matched`() {
        assertEquals("\"project\"* AND \"update\"*", FtsQuery.fromUserInput(" project  update "))
    }

    @Test
    fun `quotes cannot escape an FTS term`() {
        assertEquals("\"say\"* AND \"\"\"hello\"\"\"*", FtsQuery.fromUserInput("say \"hello\""))
    }
}
