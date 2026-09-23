package com.miladtak.japo.tracking

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersonTrackerTest {
    @Test fun keepsTrackIdWhenDetectionMoves() {
        val tracker = PersonTracker(iouThreshold = 0.1f)
        val first = tracker.update(listOf(PersonDetection(RectF(0f, 0f, 100f, 100f))))
        val second = tracker.update(listOf(PersonDetection(RectF(5f, 5f, 105f, 105f))))
        assertEquals(1, first.single().id)
        assertEquals(first.single().id, second.single().id)
    }

    @Test fun createsSeparateTracksForSeparatePeople() {
        val tracker = PersonTracker(iouThreshold = 0.1f)
        val result = tracker.update(
            listOf(
                PersonDetection(RectF(0f, 0f, 50f, 100f)),
                PersonDetection(RectF(200f, 0f, 250f, 100f))
            )
        )
        assertEquals(2, result.size)
        assertTrue(result[0].id != result[1].id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyDetectionBounds() {
        PersonDetection(RectF(0f, 0f, 0f, 10f))
    }
}
