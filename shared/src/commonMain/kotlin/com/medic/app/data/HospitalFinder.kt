package com.medic.app.data

/**
 * Offline nearest-hospital ranking from an approximate fix — no APIs, no routing.
 *
 * The dataset is a bundled list for a fixed set of regions, so a user outside
 * those regions has no usable data. That case has to be stated, not computed
 * around: ranking by distance always returns *something*, and for a user in
 * eastern India the something was San Francisco, 12,622 km away, presented as a
 * 105-day walk. On a life-safety screen, confidently wrong guidance is worse
 * than an admission of no coverage.
 */
object HospitalFinder {

    /**
     * Beyond this, the nearest entry is not plausibly reachable and the dataset
     * should be treated as having no coverage for this position.
     *
     * 150 km is well past any sensible "nearest hospital" — roughly two hours by
     * road — while still tolerating sparse rural coverage where the nearest
     * facility genuinely is distant.
     */
    const val COVERAGE_RADIUS_KM = 150.0

    fun nearest(
        hospitals: List<Hospital>,
        approxLat: Double,
        approxLon: Double,
        count: Int = 3
    ): List<HospitalWithBearing> =
        hospitals
            .map { h ->
                HospitalWithBearing(
                    hospital = h,
                    distanceKm = GeoMath.distanceKm(approxLat, approxLon, h.latitude, h.longitude),
                    bearingDegrees = GeoMath.bearingDegrees(approxLat, approxLon, h.latitude, h.longitude)
                )
            }
            .sortedBy { it.distanceKm }
            .take(count)

    /**
     * True when the bundled data has nothing near this position, so the caller
     * should say it has no coverage instead of showing a route.
     */
    fun outOfCoverage(results: List<HospitalWithBearing>): Boolean =
        results.isEmpty() || results.first().distanceKm > COVERAGE_RADIUS_KM
}
