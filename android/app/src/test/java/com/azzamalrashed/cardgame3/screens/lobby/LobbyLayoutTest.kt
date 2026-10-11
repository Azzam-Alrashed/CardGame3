package com.azzamalrashed.cardgame3.screens.lobby

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyLayoutTest {
    @Test
    fun columnsFollowThePlayerCountUpToFive() {
        // A wide screen fits 8 columns: as many as players, never more than 5.
        assertEquals(1, lobbyColumns(1, 1000f))
        assertEquals(3, lobbyColumns(3, 1000f))
        assertEquals(5, lobbyColumns(5, 1000f))
        assertEquals(5, lobbyColumns(13, 1000f))
    }

    @Test
    fun columnsFitTheWidth() {
        // (width - 48 + 12) / 112, as on iOS: a 393 pt iPhone fits 3.
        assertEquals(3, lobbyColumns(6, 393f))
        assertEquals(3, lobbyColumns(13, 372f))
        assertEquals(2, lobbyColumns(13, 371f))
        // Never fewer than one, even when nothing fits or nobody's in.
        assertEquals(1, lobbyColumns(6, 100f))
        assertEquals(1, lobbyColumns(6, 0f))
        assertEquals(1, lobbyColumns(0, 1000f))
    }

    @Test
    fun startNeedsFourPlayers() {
        assertEquals(3, missingPlayers(1))
        assertEquals(1, missingPlayers(3))
        assertTrue(missingPlayers(4) <= 0)
        assertTrue(missingPlayers(13) <= 0)
    }

    @Test
    fun tags() {
        assertNull(badgeTag(isHost = false, isYou = false, isAI = false))
        assertEquals(BadgeTag.AI, badgeTag(isHost = false, isYou = false, isAI = true))
        assertEquals(BadgeTag.Host, badgeTag(isHost = true, isYou = false, isAI = false))
        assertEquals(BadgeTag.HostYou, badgeTag(isHost = true, isYou = true, isAI = false))
        assertEquals(BadgeTag.You, badgeTag(isHost = false, isYou = true, isAI = false))
    }

    @Test
    fun leavingBadgesStayWhereTheyWere() {
        assertEquals(listOf("a", "b", "c"), mergeShown(listOf("a", "b"), listOf("a", "b", "c")))
        assertEquals(listOf("a", "b", "c"), mergeShown(listOf("a", "b", "c"), listOf("a", "c")))
        assertEquals(listOf("a", "b", "c"), mergeShown(listOf("a", "b", "c"), listOf("b", "c")))
        assertEquals(listOf("a", "b", "c", "d"), mergeShown(listOf("a", "b", "c"), listOf("a", "d")))
        assertEquals(listOf("a", "b"), mergeShown(listOf("a", "b"), emptyList()))
        assertEquals(listOf("a"), mergeShown(emptyList(), listOf("a")))
    }

    private fun plan(sizes: List<IntSize>, columns: Int, rtl: Boolean = false, minHeight: Int = 0) =
        planBadgeGrid(sizes, columns, cell = 100, spacing = 12, rowSpacing = 18, padding = 24, maxWidth = 400, minHeight = minHeight, rtl = rtl)

    @Test
    fun gridIsCenteredWithEachBadgeCenteredInItsCell() {
        val sizes = listOf(IntSize(80, 120), IntSize(100, 100), IntSize(60, 120), IntSize(80, 100))
        val p = plan(sizes, columns = 3, minHeight = 600)
        // 3 columns: 3 * 100 + 2 * 12 = 324 wide, so the grid starts at (400 - 324) / 2 = 38.
        // Rows 120 and 100 tall, 18 apart: 238 tall, starting at (600 - 238) / 2 = 181.
        assertEquals(400, p.width)
        assertEquals(600, p.height)
        assertEquals(IntOffset(38 + 10, 181), p.positions[0])
        assertEquals(IntOffset(38 + 112, 181 + 10), p.positions[1])
        assertEquals(IntOffset(38 + 224 + 20, 181), p.positions[2])
        // The last row starts at the first column.
        assertEquals(IntOffset(38 + 10, 181 + 138), p.positions[3])
    }

    @Test
    fun tallGridGrowsWithPadding() {
        val sizes = List(6) { IntSize(100, 100) }
        val p = plan(sizes, columns = 2, minHeight = 100)
        // Three rows: 300 + 2 * 18 = 336, plus 24 above and below.
        assertEquals(384, p.height)
        assertEquals(24, p.positions[0].y)
    }

    @Test
    fun rightToLeftStartsOnTheRight() {
        val sizes = listOf(IntSize(80, 100), IntSize(80, 100))
        val ltr = plan(sizes, columns = 2)
        val rtl = plan(sizes, columns = 2, rtl = true)
        assertTrue(rtl.positions[0].x > rtl.positions[1].x)
        assertEquals(400 - ltr.positions[0].x - 80, rtl.positions[0].x)
        assertEquals(ltr.positions[0].y, rtl.positions[0].y)
    }

    @Test
    fun emptyGrid() {
        val p = plan(emptyList(), columns = 1, minHeight = 300)
        assertEquals(300, p.height)
        assertTrue(p.positions.isEmpty())
    }

    @Test
    fun waitingDotsLightUpOneAtATime() {
        // Each dot is fully lit a third of a cycle after the one before it.
        assertEquals(1f, ellipsisDotAlpha(0.5f / 3, 0), 0.001f)
        assertEquals(1f, ellipsisDotAlpha(1.5f / 3, 1), 0.001f)
        assertEquals(1f, ellipsisDotAlpha(2.5f / 3, 2), 0.001f)
        // While one is lit, the others are dim.
        assertEquals(0.3f, ellipsisDotAlpha(0.5f / 3, 1), 0.001f)
        assertEquals(0.3f, ellipsisDotAlpha(0.5f / 3, 2), 0.001f)
        // It loops.
        for (index in 0 until 3) assertEquals(ellipsisDotAlpha(0f, index), ellipsisDotAlpha(1f, index), 0.001f)
    }
}
