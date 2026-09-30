package com.nicholaston.callscribe.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRegistryTest {
    @Test
    fun `model files have pinned revisions sizes and hashes`() {
        ModelRegistry.models.forEach { model ->
            assertEquals(40, model.revision.length)
            assertTrue(model.downloadBytes > 0)
            model.files.forEach { file ->
                assertTrue(file.bytes > 0)
                assertTrue(file.sha256.matches(Regex("[0-9a-f]{64}")))
                assertTrue(model.downloadUrl(file).contains(model.revision))
            }
        }
    }

    @Test
    fun `default model is Parakeet`() {
        assertEquals(
            ModelArchitecture.PARAKEET_TRANSDUCER,
            ModelRegistry.require(ModelRegistry.DEFAULT_MODEL_ID).architecture,
        )
    }
}
