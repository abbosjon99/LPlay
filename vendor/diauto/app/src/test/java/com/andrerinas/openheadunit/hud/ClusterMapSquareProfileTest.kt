package com.andrerinas.openheadunit.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// LPlay: the Leapmotor cluster window's 720x720 square.
class ClusterMapSquareProfileTest {
    @Test fun marginModeAsksForA720SquareWith280ColumnsOfMarginAtBothSides() {
        val p = ClusterMapConfig.squareProfile(0, ClusterMapConfig.Detail.NORMAL)
        assertEquals(ClusterMapConfig.RES_1280x720, p.codecResolution)
        assertEquals(1280, p.codecWidth)
        assertEquals(720, p.codecHeight)
        assertEquals(560, p.widthMargin)
        assertEquals(0, p.heightMargin)
        assertEquals(720, p.contentWidth)
        assertEquals(720, p.contentHeight)
        assertEquals(280, p.margins.left)
        assertEquals(280, p.margins.right)
        assertEquals(0, p.margins.top)
        // The content and the margins fill the codec frame exactly.
        assertEquals(p.codecWidth, p.contentWidth + p.widthMargin)
        // The car marker's box lies inside the square.
        assertTrue(p.contentInsets.left + p.contentInsets.right < p.contentWidth)
        assertTrue(p.contentInsets.top + p.contentInsets.bottom < p.contentHeight)
    }

    @Test fun cropModeAsksForTheWhole16x9FrameAndKeepsTheMarkerInTheMiddleSquare() {
        val p = ClusterMapConfig.squareProfile(1, ClusterMapConfig.Detail.NORMAL)
        assertEquals(0, p.widthMargin)
        assertEquals(1280, p.contentWidth)
        // The safe area starts at least 280 columns in from both sides, i.e. inside the window's middle 720.
        assertTrue(p.contentInsets.left >= ClusterMapConfig.SQUARE_VIEW_LEFT)
        assertTrue(p.contentInsets.right >= ClusterMapConfig.SQUARE_VIEW_LEFT)
        assertTrue(p.contentInsets.left + p.contentInsets.right < p.contentWidth)
    }

    @Test fun theWindowShowsTheColumnsTheMarginsLeftFree() {
        assertEquals(280, ClusterMapConfig.SQUARE_VIEW_LEFT)
        assertEquals(720, ClusterMapConfig.SQUARE_SIDE)
        assertEquals(1280, ClusterMapConfig.SQUARE_VIEW_LEFT * 2 + ClusterMapConfig.SQUARE_SIDE)
    }
}
