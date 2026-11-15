package com.parcha.spoilnot.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** A group's rows take their corners from where they sit in it (DESIGN 19.5). */
class ListGroupTest {
    @Test
    fun `a single row is rounded all round`() {
        assertEquals(SegmentPosition.Only, segmentPosition(index = 0, count = 1))
        assertEquals(RoundedCornerShape(20.dp), segmentShape(SegmentPosition.Only))
    }

    @Test
    fun `two rows are a first and a last`() {
        assertEquals(SegmentPosition.First, segmentPosition(index = 0, count = 2))
        assertEquals(SegmentPosition.Last, segmentPosition(index = 1, count = 2))
    }

    @Test
    fun `rows between the first and the last are middles`() {
        val positions = (0 until 5).map { segmentPosition(it, count = 5) }
        assertEquals(
            listOf(
                SegmentPosition.First,
                SegmentPosition.Middle,
                SegmentPosition.Middle,
                SegmentPosition.Middle,
                SegmentPosition.Last,
            ),
            positions,
        )
    }

    @Test
    fun `the group's outside corners are large and the joins small`() {
        assertEquals(
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 6.dp),
            segmentShape(SegmentPosition.First),
        )
        assertEquals(RoundedCornerShape(6.dp), segmentShape(SegmentPosition.Middle))
        assertEquals(
            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            segmentShape(SegmentPosition.Last),
        )
    }

    @Test
    fun `rows inside a highlight card take its smaller outside corner`() {
        assertEquals(
            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 6.dp),
            segmentShape(SegmentPosition.First, outer = 18.dp),
        )
    }

    @Test
    fun `a row outside its group is refused`() {
        assertThrows(IllegalArgumentException::class.java) { segmentPosition(index = 0, count = 0) }
        assertThrows(IllegalArgumentException::class.java) { segmentPosition(index = 3, count = 3) }
        assertThrows(IllegalArgumentException::class.java) { segmentPosition(index = -1, count = 3) }
    }
}
