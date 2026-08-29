"""Generate the solar-heading evaluation set with independent ground truth.

Ground truth comes from Skyfield against the JPL DE421 ephemeris — deliberately
NOT from the Meeus/NOAA algorithm the app implements, because measuring an
algorithm against itself proves only that it is self-consistent.

The baseline is a magnetic compass, which is what someone without GPS actually
reaches for. Its error against true north is the magnetic declination at that
place and time, from the WMM model.

Output (both committed, so the evaluation replays with no network):
  cases/solar_cases.tsv   id, iso8601, lat, lon        -> fed to the Kotlin harness
  cases/solar_truth.tsv   id, true_az, sun_elev, decl  -> the answer key
"""

from __future__ import annotations

import random
from pathlib import Path

from pygeomag import GeoMag
from skyfield.api import load, wgs84

HERE = Path(__file__).resolve().parent
SEED = 20260830          # fixed so the corpus is identical for anyone who rebuilds
TARGET_CASES = 120
MIN_SUN_ELEVATION = 5.0  # below this the app refuses a solar fix (isSunUsable)

# Spread over latitude bands, longitudes, seasons and times of day. Arctic and
# equatorial sites behave very differently, and a set clustered in one band
# would flatter any implementation.
SITES = [
    ("san_jose",    37.3,  -121.9),
    ("reykjavik",   64.1,   -21.9),
    ("nairobi",     -1.3,    36.8),
    ("sydney",     -33.9,   151.2),
    ("erlangen",    49.6,    11.0),
    ("lapu_lapu",   10.3,   123.9),
    ("death_valley",36.5,  -117.1),
    ("ushuaia",    -54.8,   -68.3),
    ("svalbard",    78.2,    15.6),
    ("quito",       -0.2,   -78.5),
]


def main() -> None:
    rng = random.Random(SEED)
    ts = load.timescale()
    eph = load("de421.bsp")
    sun, earth = eph["sun"], eph["earth"]
    geomag = GeoMag()

    cases, truth = [], []
    attempts = 0
    while len(cases) < TARGET_CASES and attempts < TARGET_CASES * 40:
        attempts += 1
        name, lat, lon = SITES[len(cases) % len(SITES)]
        month = rng.randint(1, 12)
        day = rng.randint(1, 28)
        hour = rng.randint(0, 23)
        minute = rng.choice([0, 15, 30, 45])

        t = ts.utc(2026, month, day, hour, minute, 0)
        observer = earth + wgs84.latlon(lat, lon)
        alt, az, _ = observer.at(t).observe(sun).apparent().altaz()

        # Only sample where the app would actually offer a solar fix.
        if alt.degrees < MIN_SUN_ELEVATION:
            continue

        cid = f"{name}_{len(cases):03d}"
        iso = f"2026-{month:02d}-{day:02d}T{hour:02d}:{minute:02d}:00Z"
        decl = geomag.calculate(glat=lat, glon=lon, alt=0, time=2026.5).d

        cases.append((cid, iso, lat, lon))
        truth.append((cid, az.degrees, alt.degrees, decl))

    (HERE / "cases").mkdir(exist_ok=True)
    with (HERE / "cases/solar_cases.tsv").open("w") as f:
        f.write("# id\tutc_iso8601\tlat\tlon\n")
        for cid, iso, lat, lon in cases:
            f.write(f"{cid}\t{iso}\t{lat}\t{lon}\n")

    with (HERE / "cases/solar_truth.tsv").open("w") as f:
        f.write("# id\ttrue_azimuth_deg\tsun_elevation_deg\tmagnetic_declination_deg\n")
        f.write("# truth: Skyfield + JPL DE421.  declination: WMM via pygeomag.\n")
        for cid, az, alt, decl in truth:
            f.write(f"{cid}\t{az:.6f}\t{alt:.6f}\t{decl:.6f}\n")

    lats = sorted({round(c[2]) for c in cases})
    print(f"wrote {len(cases)} cases across {len(SITES)} sites")
    print(f"  latitude span: {min(lats)} to {max(lats)}")
    print(f"  sun elevation: all >= {MIN_SUN_ELEVATION} deg (app refuses a fix below this)")


if __name__ == "__main__":
    main()
