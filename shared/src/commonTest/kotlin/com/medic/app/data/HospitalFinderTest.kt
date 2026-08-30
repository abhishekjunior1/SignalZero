package com.medic.app.data

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class HospitalFinderTest {

    @Test
    fun nearest_ranks_by_distance_from_downtown_sf() {
        val hospitals = listOf(
            Hospital("Zuckerberg SF General", 37.7554, -122.4045),
            Hospital("UCSF Parnassus", 37.7631, -122.4586),
            Hospital("Far Away", 37.9000, -122.5000)
        )
        val nearest = HospitalFinder.nearest(hospitals, 37.7749, -122.4194, count = 2)
        assertEquals(2, nearest.size)
        assertTrue(nearest[0].distanceKm < nearest[1].distanceKm)
        assertEquals("Zuckerberg SF General", nearest[0].hospital.name)
    }

    @Test
    fun bearing_is_cardinal_east_for_point_east() {
        val bearing = GeoMath.bearingDegrees(37.7749, -122.4194, 37.7749, -122.4094)
        assertTrue(bearing in 85.0..95.0)
    }
}

class HospitalCoverageTest {

    private val sf = listOf(
        Hospital("UCSF Parnassus", 37.7631, -122.4586),
        Hospital("Zuckerberg SF General", 37.7554, -122.4049),
    )

    @kotlin.test.Test
    fun reportsNoCoverageForAPositionFarFromTheDataset() {
        // Ranchi, Jharkhand. Before this guard the app offered the San Francisco
        // VA Medical Center, 12,622 km away, as a 151,471-minute walk.
        val results = HospitalFinder.nearest(sf, 23.3441, 85.3096)
        kotlin.test.assertTrue(
            HospitalFinder.outOfCoverage(results),
            "a position 12,000 km from every entry must not be reported as covered",
        )
    }

    @kotlin.test.Test
    fun reportsCoverageWhenInsideTheDatasetRegion() {
        val results = HospitalFinder.nearest(sf, 37.7749, -122.4194)
        kotlin.test.assertTrue(results.first().distanceKm < 10.0)
        kotlin.test.assertTrue(!HospitalFinder.outOfCoverage(results))
    }

    @kotlin.test.Test
    fun emptyDatasetIsOutOfCoverage() {
        kotlin.test.assertTrue(HospitalFinder.outOfCoverage(emptyList()))
    }
}
