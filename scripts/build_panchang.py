#!/usr/bin/env python3
"""
Calculate the Varanasi panchang and write the two JSON feeds the apps read.

    varanasi-panchang-daily.json     today's tithi, nakshatra, yoga, karana,
                                     sun/moon rise and set, rahu kaal, muhurat
    varanasi-panchang-calendar.json  vrat and tyohar dates for the coming weeks

Nothing is scraped. Positions come from NASA JPL's DE421 ephemeris through
Skyfield (the Drik Ganita method: real apparent Sun and Moon positions, with
the Lahiri / Chitrapaksha ayanamsa for sidereal values), computed for
Varanasi's coordinates and reported in IST.

Festival dates follow the common North Indian (purnimanta, Smarta) rules for
which part of the day a tithi must touch: sunrise for most vrats, sunset for
pradosh, moonrise for chaturthi and purnima vrats, midnight for Janmashtami and
Shivaratri. Regional custom sometimes differs by a day, which is why each
calendar entry carries date_confidence "calculated".

Usage:
    python scripts/build_panchang.py                  # today in IST
    python scripts/build_panchang.py --date 2026-08-26 --days 45
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path

import numpy as np
from skyfield import almanac
from skyfield.api import Loader, wgs84
from skyfield.framelib import ecliptic_frame
from skyfield.searchlib import find_discrete

LAT, LNG, ELEVATION_M = 25.3176, 82.9739, 80.0
LOCATION_NAME = "Varanasi, Uttar Pradesh, India"
IST = timezone(timedelta(hours=5, minutes=30))

NAKSHATRA_SPAN = 360.0 / 27.0

TITHI = [
    ("Pratipada", "प्रतिपदा"), ("Dwitiya", "द्वितीया"), ("Tritiya", "तृतीया"),
    ("Chaturthi", "चतुर्थी"), ("Panchami", "पंचमी"), ("Shashthi", "षष्ठी"),
    ("Saptami", "सप्तमी"), ("Ashtami", "अष्टमी"), ("Navami", "नवमी"),
    ("Dashami", "दशमी"), ("Ekadashi", "एकादशी"), ("Dwadashi", "द्वादशी"),
    ("Trayodashi", "त्रयोदशी"), ("Chaturdashi", "चतुर्दशी"),
]
PURNIMA = ("Purnima", "पूर्णिमा")
AMAVASYA = ("Amavasya", "अमावस्या")

NAKSHATRA = [
    ("Ashwini", "अश्विनी"), ("Bharani", "भरणी"), ("Krittika", "कृत्तिका"),
    ("Rohini", "रोहिणी"), ("Mrigashira", "मृगशिरा"), ("Ardra", "आर्द्रा"),
    ("Punarvasu", "पुनर्वसु"), ("Pushya", "पुष्य"), ("Ashlesha", "आश्लेषा"),
    ("Magha", "मघा"), ("Purva Phalguni", "पूर्वा फाल्गुनी"), ("Uttara Phalguni", "उत्तरा फाल्गुनी"),
    ("Hasta", "हस्त"), ("Chitra", "चित्रा"), ("Swati", "स्वाती"),
    ("Vishakha", "विशाखा"), ("Anuradha", "अनुराधा"), ("Jyeshtha", "ज्येष्ठा"),
    ("Mula", "मूल"), ("Purva Ashadha", "पूर्वाषाढ़ा"), ("Uttara Ashadha", "उत्तराषाढ़ा"),
    ("Shravana", "श्रवण"), ("Dhanishta", "धनिष्ठा"), ("Shatabhisha", "शतभिषा"),
    ("Purva Bhadrapada", "पूर्वा भाद्रपद"), ("Uttara Bhadrapada", "उत्तरा भाद्रपद"), ("Revati", "रेवती"),
]

YOGA = [
    ("Vishkambha", "विष्कुम्भ"), ("Priti", "प्रीति"), ("Ayushman", "आयुष्मान"),
    ("Saubhagya", "सौभाग्य"), ("Shobhana", "शोभन"), ("Atiganda", "अतिगण्ड"),
    ("Sukarma", "सुकर्मा"), ("Dhriti", "धृति"), ("Shula", "शूल"),
    ("Ganda", "गण्ड"), ("Vriddhi", "वृद्धि"), ("Dhruva", "ध्रुव"),
    ("Vyaghata", "व्याघात"), ("Harshana", "हर्षण"), ("Vajra", "वज्र"),
    ("Siddhi", "सिद्धि"), ("Vyatipata", "व्यतीपात"), ("Variyana", "वरीयान"),
    ("Parigha", "परिघ"), ("Shiva", "शिव"), ("Siddha", "सिद्ध"),
    ("Sadhya", "साध्य"), ("Shubha", "शुभ"), ("Shukla", "शुक्ल"),
    ("Brahma", "ब्रह्म"), ("Indra", "इन्द्र"), ("Vaidhriti", "वैधृति"),
]

MOVABLE_KARANA = [
    ("Bava", "बव"), ("Balava", "बालव"), ("Kaulava", "कौलव"), ("Taitila", "तैतिल"),
    ("Garaja", "गर"), ("Vanija", "वणिज"), ("Vishti", "विष्टि"),
]

MONTHS = [
    ("Chaitra", "चैत्र"), ("Vaishakha", "वैशाख"), ("Jyeshtha", "ज्येष्ठ"),
    ("Ashadha", "आषाढ़"), ("Shravana", "श्रावण"), ("Bhadrapada", "भाद्रपद"),
    ("Ashwin", "आश्विन"), ("Kartika", "कार्तिक"), ("Margashirsha", "मार्गशीर्ष"),
    ("Pausha", "पौष"), ("Magha", "माघ"), ("Phalguna", "फाल्गुन"),
]

VARA = [
    ("Monday (Somavara)", "सोमवार"), ("Tuesday (Mangalavara)", "मंगलवार"),
    ("Wednesday (Budhavara)", "बुधवार"), ("Thursday (Guruvara)", "गुरुवार"),
    ("Friday (Shukravara)", "शुक्रवार"), ("Saturday (Shanivara)", "शनिवार"),
    ("Sunday (Ravivara)", "रविवार"),
]

# Which eighth of the daytime each kaal occupies, by weekday (Monday = 0), 1-based.
RAHU_PART = [2, 7, 5, 6, 4, 3, 8]
YAMAGANDA_PART = [4, 3, 2, 1, 7, 6, 5]
GULIKA_PART = [6, 5, 4, 3, 2, 1, 7]


def tithi_name(index: int) -> tuple[str, str]:
    """index 0..29: 0..14 Shukla (14 = Purnima), 15..29 Krishna (29 = Amavasya)."""
    if index == 14:
        return PURNIMA
    if index == 29:
        return AMAVASYA
    return TITHI[index % 15]


def paksha(index: int) -> str:
    return "Shukla" if index < 15 else "Krishna"


def karana_name(index: int) -> tuple[str, str]:
    """index 0..59, one per half-tithi."""
    if index == 0:
        return ("Kimstughna", "किंस्तुघ्न")
    if index == 57:
        return ("Shakuni", "शकुनि")
    if index == 58:
        return ("Chatushpada", "चतुष्पद")
    if index == 59:
        return ("Naga", "नाग")
    return MOVABLE_KARANA[(index - 1) % 7]


class Sky:
    """Sun and Moon positions for Varanasi."""

    def __init__(self, ephemeris_dir: Path):
        load = Loader(str(ephemeris_dir), verbose=False)
        self.ts = load.timescale()
        self.eph = load("de421.bsp")
        self.earth = self.eph["earth"]
        self.sun = self.eph["sun"]
        self.moon = self.eph["moon"]
        self.place = wgs84.latlon(LAT, LNG, elevation_m=ELEVATION_M)

    # --- longitudes -------------------------------------------------------
    def _lon(self, body, t) -> np.ndarray:
        lat, lon, _ = self.earth.at(t).observe(body).apparent().frame_latlon(ecliptic_frame)
        return lon.degrees

    def ayanamsa(self, t) -> np.ndarray:
        """Lahiri (Chitrapaksha) ayanamsa in degrees: J2000 value plus IAU 2006 precession."""
        centuries = (t.tt - 2451545.0) / 36525.0
        return 23.857092 + (5028.796195 * centuries + 1.1054348 * centuries**2) / 3600.0

    def elongation(self, t) -> np.ndarray:
        return (self._lon(self.moon, t) - self._lon(self.sun, t)) % 360.0

    def sidereal_moon(self, t) -> np.ndarray:
        return (self._lon(self.moon, t) - self.ayanamsa(t)) % 360.0

    def sidereal_sun(self, t) -> np.ndarray:
        return (self._lon(self.sun, t) - self.ayanamsa(t)) % 360.0

    # --- discrete indices (for root finding) ------------------------------
    def tithi_index(self, t):
        return (self.elongation(t) // 12.0).astype(int)

    def karana_index(self, t):
        return (self.elongation(t) // 6.0).astype(int)

    def nakshatra_index(self, t):
        return (self.sidereal_moon(t) // NAKSHATRA_SPAN).astype(int)

    def yoga_index(self, t):
        total = (self.sidereal_moon(t) + self.sidereal_sun(t)) % 360.0
        return (total // NAKSHATRA_SPAN).astype(int)

    def sun_sign(self, t):
        return (self.sidereal_sun(t) // 30.0).astype(int)

    # --- time helpers -----------------------------------------------------
    def t_of(self, dt: datetime):
        return self.ts.from_datetime(dt.astimezone(timezone.utc))

    @staticmethod
    def dt_of(t) -> datetime:
        return t.utc_datetime().astimezone(IST)

    def events(self, kind: str, body, start: datetime, end: datetime) -> list[datetime]:
        t0, t1 = self.t_of(start), self.t_of(end)
        finder = almanac.find_risings if kind == "rise" else almanac.find_settings
        times, ok = finder(self.earth + self.place, body, t0, t1)
        return [self.dt_of(t) for t, good in zip(times, ok) if good]

    def transits(self, start: datetime, end: datetime) -> list[datetime]:
        f = almanac.meridian_transits(self.eph, self.sun, self.place)
        times, kinds = find_discrete(self.t_of(start), self.t_of(end), f)
        return [self.dt_of(t) for t, k in zip(times, kinds) if k == 1]

    def changes(self, func, start: datetime, end: datetime, step_days: float) -> list[tuple[datetime, int]]:
        """(time, new index) for every change of a discrete index in [start, end)."""

        def f(t):
            return func(t)

        f.step_days = step_days
        times, values = find_discrete(self.t_of(start), self.t_of(end), f)
        return [(self.dt_of(t), int(v)) for t, v in zip(times, values)]

    def value(self, func, when: datetime) -> int:
        return int(func(self.t_of(when)))


@dataclass
class Day:
    date: date
    sunrise: datetime
    sunset: datetime
    next_sunrise: datetime
    noon: datetime
    moonrise: datetime | None
    moonset: datetime | None


def iso(dt: datetime | None) -> str | None:
    if dt is None:
        return None
    return dt.astimezone(IST).replace(second=0, microsecond=0).isoformat()


def round_min(dt: datetime) -> datetime:
    """Truncate to the minute, the way printed panchangs show hh:mm."""
    return dt.replace(second=0, microsecond=0)


class Panchang:
    def __init__(self, sky: Sky):
        self.sky = sky
        self._days: dict[date, Day] = {}

    def day(self, d: date) -> Day:
        if d in self._days:
            return self._days[d]
        sky = self.sky
        midnight = datetime.combine(d, time(0), IST)
        rises = sky.events("rise", sky.sun, midnight, midnight + timedelta(days=2))
        sunrise, next_sunrise = rises[0], rises[1]
        sunset = sky.events("set", sky.sun, sunrise, next_sunrise)[0]
        noon = sky.transits(sunrise, sunset)[0]
        moonrises = sky.events("rise", sky.moon, sunrise, next_sunrise)
        moonsets = sky.events("set", sky.moon, sunrise, next_sunrise)
        result = Day(d, sunrise, sunset, next_sunrise, noon,
                     moonrises[0] if moonrises else None, moonsets[0] if moonsets else None)
        self._days[d] = result
        return result

    def angas(self, func, names, start: datetime, end: datetime, step: float, with_paksha=False):
        """Every anga in force between sunrise and next sunrise, with its end time.

        The last one runs past the next sunrise, so its ends_at is null.
        """
        sky = self.sky
        current = sky.value(func, start)
        out = []
        for when, new in sky.changes(func, start, end, step):
            out.append((current, when))
            current = new
        out.append((current, None))
        entries = []
        for idx, ends in out:
            en, hi = names(idx)
            entry = {"name": en, "name_hi": hi}
            if with_paksha:
                entry["paksha"] = paksha(idx)
            entry["ends_at"] = iso(round_min(ends)) if ends else None
            entries.append(entry)
        return entries

    def lunar_month(self, when: datetime) -> dict:
        """Amanta month in force at `when`, and its purnimanta (North Indian) name."""
        sky = self.sky

        def waxing(t):
            return sky.elongation(t) < 180.0

        waxing.step_days = 1.0
        t0 = sky.t_of(when - timedelta(days=32))
        t1 = sky.t_of(when + timedelta(days=32))
        times, values = find_discrete(t0, t1, waxing)
        new_moons = [Sky.dt_of(t) for t, v in zip(times, values) if v]  # False -> True
        before = max(m for m in new_moons if m <= when)
        after = min(m for m in new_moons if m > when)
        sign_start = sky.value(sky.sun_sign, before)
        sign_end = sky.value(sky.sun_sign, after)
        amanta = (sign_start + 1) % 12
        adhik = sign_start == sign_end
        tithi = sky.value(sky.tithi_index, when)
        purnimanta = amanta if (tithi < 15 or adhik) else (amanta + 1) % 12
        return {
            "amanta_index": amanta,
            "adhik": adhik,
            "amanta": MONTHS[amanta][0],
            "purnimanta": MONTHS[purnimanta][0],
            "purnimanta_hi": ("अधिक " if adhik else "") + MONTHS[purnimanta][1],
        }

    def daily(self, d: date, observances: list[dict]) -> dict:
        sky = self.sky
        day = self.day(d)
        start, end = day.sunrise, day.next_sunrise
        day_len = day.sunset - day.sunrise
        part = day_len / 8
        weekday = d.weekday()

        def window(n):
            s = day.sunrise + part * (n - 1)
            return {"start": iso(round_min(s)), "end": iso(round_min(s + part))}

        muhurta = day_len / 15
        abhijit_start = day.sunrise + muhurta * 7
        month = self.lunar_month(day.sunrise)
        todays = [o for o in observances if o["date"] == d.isoformat()]

        return {
            "generated_at": datetime.now(IST).replace(microsecond=0).isoformat(),
            "date": d.isoformat(),
            "location": {"name": LOCATION_NAME, "lat": LAT, "lng": LNG},
            "vara": VARA[weekday][0],
            "vara_hi": VARA[weekday][1],
            "lunar_month": {
                "purnimanta": month["purnimanta"],
                "purnimanta_hi": month["purnimanta_hi"],
                "amanta": month["amanta"],
                "adhik": month["adhik"],
            },
            "tithi": self.angas(sky.tithi_index, tithi_name, start, end, 0.05, with_paksha=True),
            "nakshatra": self.angas(sky.nakshatra_index, lambda i: NAKSHATRA[i], start, end, 0.05),
            "yoga": self.angas(sky.yoga_index, lambda i: YOGA[i], start, end, 0.05),
            "karana": self.angas(sky.karana_index, karana_name, start, end, 0.03),
            "sunrise": iso(round_min(day.sunrise)),
            "sunset": iso(round_min(day.sunset)),
            "moonrise": iso(round_min(day.moonrise)) if day.moonrise else None,
            "moonset": iso(round_min(day.moonset)) if day.moonset else None,
            "rahu_kaal": window(RAHU_PART[weekday]),
            "yamaganda_kaal": window(YAMAGANDA_PART[weekday]),
            "gulika_kaal": window(GULIKA_PART[weekday]),
            "abhijit_muhurat": {
                "start": iso(round_min(abhijit_start)),
                "end": iso(round_min(abhijit_start + muhurta)),
            },
            "festivals_and_vrats": [
                {"name": o["name"], "name_hi": o["name_hi"], "type": o["type"]} for o in todays
            ],
            "calculation_method": "Drik Ganita (JPL DE421 ephemeris, Lahiri ayanamsa)",
            "data_source": "calculated",
            "confidence": "calculated",
            "source_url": None,
        }


# --- vrat & tyohar rules ---------------------------------------------------
#
# A rule fires for each occurrence of its tithi (index 0..29) in the given amanta
# month (None = every month). The observance falls on the first day whose `kaal`
# instant lies inside the tithi; if none does (a kshaya tithi), on the day the
# tithi begins.

def T(paksha_name: str, number: int) -> int:
    """Tithi index from paksha and 1..15 (15 = Purnima / Amavasya)."""
    return number - 1 + (0 if paksha_name == "Shukla" else 15)


EKADASHI_SHUKLA = {
    0: ("Kamada Ekadashi", "कामदा एकादशी"), 1: ("Mohini Ekadashi", "मोहिनी एकादशी"),
    2: ("Nirjala Ekadashi", "निर्जला एकादशी"), 3: ("Devshayani Ekadashi", "देवशयनी एकादशी"),
    4: ("Shravana Putrada Ekadashi", "श्रावण पुत्रदा एकादशी"),
    5: ("Parsva Ekadashi (Parivartini Ekadashi)", "परिवर्तिनी एकादशी"),
    6: ("Papankusha Ekadashi", "पापांकुशा एकादशी"),
    7: ("Dev Uthani Ekadashi (Prabodhini)", "देवउठनी एकादशी"),
    8: ("Mokshada Ekadashi", "मोक्षदा एकादशी"), 9: ("Pausha Putrada Ekadashi", "पौष पुत्रदा एकादशी"),
    10: ("Jaya Ekadashi", "जया एकादशी"), 11: ("Amalaki Ekadashi", "आमलकी एकादशी"),
}
EKADASHI_KRISHNA = {  # keyed by amanta month
    0: ("Varuthini Ekadashi", "वरूथिनी एकादशी"), 1: ("Apara Ekadashi", "अपरा एकादशी"),
    2: ("Yogini Ekadashi", "योगिनी एकादशी"), 3: ("Kamika Ekadashi", "कामिका एकादशी"),
    4: ("Aja Ekadashi", "अजा एकादशी"), 5: ("Indira Ekadashi", "इंदिरा एकादशी"),
    6: ("Rama Ekadashi", "रमा एकादशी"), 7: ("Utpanna Ekadashi", "उत्पन्ना एकादशी"),
    8: ("Saphala Ekadashi", "सफला एकादशी"), 9: ("Shattila Ekadashi", "षटतिला एकादशी"),
    10: ("Vijaya Ekadashi", "विजया एकादशी"), 11: ("Papmochani Ekadashi", "पापमोचनी एकादशी"),
}

PRADOSH_DAY = [
    ("Som Pradosh Vrat", "सोम प्रदोष व्रत"), ("Bhaum Pradosh Vrat", "भौम प्रदोष व्रत"),
    ("Budh Pradosh Vrat", "बुध प्रदोष व्रत"), ("Guru Pradosh Vrat", "गुरु प्रदोष व्रत"),
    ("Shukra Pradosh Vrat", "शुक्र प्रदोष व्रत"), ("Shani Pradosh Vrat", "शनि प्रदोष व्रत"),
    ("Ravi Pradosh Vrat", "रवि प्रदोष व्रत"),
]

# (english, hindi, type, amanta month or None, tithi index, kaal, description)
FESTIVALS = [
    ("Chaitra Navratri begins", "चैत्र नवरात्रि प्रारंभ", "both", 0, T("Shukla", 1), "sunrise",
     "Nine nights of Devi worship begin; Hindu new year (Vikram Samvat)."),
    ("Ram Navami", "राम नवमी", "both", 0, T("Shukla", 9), "noon", "Birth of Lord Rama."),
    ("Hanuman Jayanti", "हनुमान जयंती", "tyohar", 0, T("Shukla", 15), "sunrise",
     "Birth of Lord Hanuman; crowds at Sankat Mochan temple."),
    ("Akshaya Tritiya", "अक्षय तृतीया", "tyohar", 1, T("Shukla", 3), "sunrise",
     "An auspicious day for new beginnings and buying gold."),
    ("Buddha Purnima", "बुद्ध पूर्णिमा", "tyohar", 1, T("Shukla", 15), "sunrise",
     "Birth, enlightenment and passing of the Buddha; celebrated at Sarnath."),
    ("Ganga Dussehra", "गंगा दशहरा", "tyohar", 2, T("Shukla", 10), "sunrise",
     "Descent of the Ganga to earth; holy dip at the ghats."),
    ("Guru Purnima", "गुरु पूर्णिमा", "tyohar", 3, T("Shukla", 15), "sunrise", "Day to honour one's teachers."),
    ("Hariyali Teej", "हरियाली तीज", "vrat", 4, T("Shukla", 3), "sunrise", "Monsoon festival of Parvati."),
    ("Nag Panchami", "नाग पंचमी", "tyohar", 4, T("Shukla", 5), "sunrise", "Worship of serpent deities."),
    ("Raksha Bandhan", "रक्षाबंधन", "tyohar", 4, T("Shukla", 15), "sunrise",
     "Sisters tie a rakhi on their brothers' wrists."),
    ("Krishna Janmashtami", "कृष्ण जन्माष्टमी", "both", 4, T("Krishna", 8), "midnight",
     "Birth of Lord Krishna, celebrated at midnight."),
    ("Hartalika Teej", "हरतालिका तीज", "vrat", 5, T("Shukla", 3), "sunrise", "Nirjala fast for Shiva and Parvati."),
    ("Ganesh Chaturthi", "गणेश चतुर्थी", "tyohar", 5, T("Shukla", 4), "noon", "Birth of Lord Ganesha."),
    ("Radha Ashtami", "राधा अष्टमी", "tyohar", 5, T("Shukla", 8), "noon", "Birth of Radha."),
    ("Anant Chaturdashi (Ganesh Visarjan)", "अनंत चतुर्दशी", "both", 5, T("Shukla", 14), "sunrise",
     "Worship of Lord Anant; Ganesh idols are immersed."),
    ("Pitru Paksha begins", "पितृ पक्ष प्रारंभ", "vrat", 5, T("Krishna", 1), "sunrise",
     "Fortnight of shraddh for ancestors."),
    ("Sarvapitri Amavasya", "सर्वपितृ अमावस्या", "vrat", 5, T("Krishna", 15), "sunrise",
     "Last day of Pitru Paksha."),
    ("Sharad Navratri begins", "शारदीय नवरात्रि प्रारंभ", "both", 6, T("Shukla", 1), "sunrise",
     "Nine nights of Durga worship begin."),
    ("Durga Ashtami", "दुर्गा अष्टमी", "both", 6, T("Shukla", 8), "sunrise", "Maha Ashtami of Navratri."),
    ("Maha Navami", "महानवमी", "both", 6, T("Shukla", 9), "sunrise", "Last day of Navratri."),
    ("Dussehra (Vijayadashami)", "दशहरा (विजयादशमी)", "tyohar", 6, T("Shukla", 10), "afternoon",
     "Victory of Rama over Ravana; Ramnagar Ramlila concludes."),
    ("Sharad Purnima", "शरद पूर्णिमा", "both", 6, T("Shukla", 15), "moonrise",
     "Full moon night of kheer under moonlight."),
    ("Karwa Chauth", "करवा चौथ", "vrat", 6, T("Krishna", 4), "moonrise",
     "Married women fast until moonrise."),
    ("Ahoi Ashtami", "अहोई अष्टमी", "vrat", 6, T("Krishna", 8), "sunset", "Mothers fast for their children."),
    ("Dhanteras", "धनतेरस", "tyohar", 6, T("Krishna", 13), "sunset", "First day of Diwali; buying metal and gold."),
    ("Narak Chaturdashi (Chhoti Diwali)", "नरक चतुर्दशी (छोटी दिवाली)", "tyohar", 6, T("Krishna", 14), "sunrise",
     "Second day of Diwali."),
    ("Diwali (Lakshmi Puja)", "दीपावली (लक्ष्मी पूजा)", "tyohar", 6, T("Krishna", 15), "sunset",
     "Festival of lights; Lakshmi Puja in the evening."),
    ("Govardhan Puja", "गोवर्धन पूजा", "tyohar", 7, T("Shukla", 1), "sunrise", "Annakut offering to Krishna."),
    ("Bhai Dooj", "भाई दूज", "tyohar", 7, T("Shukla", 2), "afternoon", "Sisters pray for their brothers."),
    ("Chhath Puja (Sandhya Arghya)", "छठ पूजा (संध्या अर्घ्य)", "both", 7, T("Shukla", 6), "sunset",
     "Evening offering to the setting Sun at the ghats."),
    ("Dev Deepawali", "देव दीपावली", "tyohar", 7, T("Shukla", 15), "sunset",
     "Kashi's ghats are lit with lakhs of diyas on Kartik Purnima."),
    ("Vivah Panchami", "विवाह पंचमी", "tyohar", 8, T("Shukla", 5), "sunrise", "Wedding of Rama and Sita."),
    ("Vasant Panchami", "वसंत पंचमी", "tyohar", 10, T("Shukla", 5), "sunrise", "Saraswati Puja; spring begins."),
    ("Maha Shivaratri", "महाशिवरात्रि", "both", 10, T("Krishna", 14), "midnight",
     "Great night of Shiva; Kashi Vishwanath's biggest festival."),
    ("Holika Dahan", "होलिका दहन", "tyohar", 11, T("Shukla", 15), "sunset", "Bonfire on the eve of Holi."),
    ("Holi", "होली", "tyohar", 11, T("Krishna", 1), "sunrise", "Festival of colours."),
]

# Recurring vrats: (english, hindi, tithi index, kaal, description)
MONTHLY = [
    ("Purnima Vrat", "पूर्णिमा व्रत", T("Shukla", 15), "moonrise", "Full-moon fast; Satyanarayan puja is common."),
    ("Amavasya", "अमावस्या", T("Krishna", 15), "afternoon", "New moon; day for tarpan and Ganga snan."),
    ("Sankashti Chaturthi", "संकष्टी चतुर्थी", T("Krishna", 4), "moonrise", "Ganesha fast broken at moonrise."),
    ("Vinayaka Chaturthi", "विनायक चतुर्थी", T("Shukla", 4), "noon", "Monthly Ganesha vrat."),
    ("Masik Shivaratri", "मासिक शिवरात्रि", T("Krishna", 14), "midnight", "Monthly night of Shiva."),
]

EKADASHI_DESC = "Ekadashi fast dedicated to Lord Vishnu."
PRADOSH_DESC = "Evening worship of Shiva during pradosh kaal."


class Calendar:
    def __init__(self, panchang: Panchang):
        self.p = panchang
        self.sky = panchang.sky

    def kaal(self, d: date, which: str) -> datetime | None:
        day = self.p.day(d)
        if which == "sunrise":
            return day.sunrise
        if which == "sunset":  # pradosh kaal: within ~1.5 h after sunset
            return day.sunset + timedelta(minutes=72)
        if which == "noon":  # madhyahna
            return day.noon
        if which == "afternoon":  # aparahna: 4th fifth of the day
            return day.sunrise + (day.sunset - day.sunrise) * 0.7
        if which == "midnight":  # nishita: solar midnight that ends this date
            return day.noon + timedelta(hours=12)
        if which == "moonrise":
            return day.moonrise
        raise ValueError(which)

    def tithi_spans(self, start: date, end: date) -> list[tuple[int, datetime, datetime]]:
        s = datetime.combine(start, time(0), IST)
        e = datetime.combine(end, time(0), IST)
        first = self.sky.value(self.sky.tithi_index, s)
        changes = self.sky.changes(self.sky.tithi_index, s, e, 0.05)
        spans = []
        cur, cur_start = first, s
        for when, new in changes:
            spans.append((cur, cur_start, when))
            cur, cur_start = new, when
        spans.append((cur, cur_start, e))
        # Drop the partial spans at both edges.
        return spans[1:-1]

    def day_for(self, span_start: datetime, span_end: datetime, kaal: str) -> date:
        d = span_start.date() - timedelta(days=1)
        while d <= span_end.date():
            k = self.kaal(d, kaal)
            if k is not None and span_start <= k < span_end:
                return d
            d += timedelta(days=1)
        # Kshaya at that kaal: the day the tithi begins (before sunrise -> previous date).
        begin = span_start.date()
        if span_start < self.p.day(begin).sunrise:
            begin -= timedelta(days=1)
        return begin

    def build(self, start: date, days: int) -> list[dict]:
        end = start + timedelta(days=days)
        out: list[dict] = []
        for idx, s, e in self.tithi_spans(start - timedelta(days=3), end + timedelta(days=3)):
            mid = s + (e - s) / 2
            month = self.p.lunar_month(mid)
            m = month["amanta_index"]
            adhik = month["adhik"]
            hits: list[tuple[str, str, str, str, str, bool]] = []

            if idx in (T("Shukla", 11), T("Krishna", 11)):
                if adhik:
                    name = ("Padmini Ekadashi", "पद्मिनी एकादशी") if idx < 15 else ("Parama Ekadashi", "परमा एकादशी")
                else:
                    name = (EKADASHI_SHUKLA if idx < 15 else EKADASHI_KRISHNA)[m]
                hits.append((name[0], name[1], "vrat", "sunrise", EKADASHI_DESC, False))
            if idx in (T("Shukla", 13), T("Krishna", 13)):
                hits.append(("PRADOSH", "", "vrat", "sunset", PRADOSH_DESC, False))
            for en, hi, t_idx, kaal, desc in MONTHLY:
                if idx == t_idx:
                    hits.append((en, hi, "vrat", kaal, desc, True))
            if not adhik:
                for en, hi, typ, fm, t_idx, kaal, desc in FESTIVALS:
                    if fm == m and idx == t_idx:
                        hits.append((en, hi, typ, kaal, desc, False))

            # Month in the purnimanta reckoning used in Varanasi.
            pm = m if idx < 15 or adhik else (m + 1) % 12
            month_en = ("Adhik " if adhik else "") + MONTHS[pm][0]
            month_hi = ("अधिक " if adhik else "") + MONTHS[pm][1]
            for en, hi, typ, kaal, desc, generic in hits:
                d = self.day_for(s, e, kaal)
                if en == "PRADOSH":
                    en, hi = PRADOSH_DAY[d.weekday()]
                elif idx in (T("Shukla", 15), T("Krishna", 15)) and generic:
                    en, hi = f"{month_en} {en}", f"{month_hi} {hi}"
                if not (start <= d < end):
                    continue
                out.append({
                    "id": f"{slug(en)}-{d.isoformat()}",
                    "name": en,
                    "name_hi": hi,
                    "type": typ,
                    "date": d.isoformat(),
                    "tithi": tithi_name(idx)[0],
                    "paksha": paksha(idx),
                    "month": month_en,
                    "description": desc,
                    "date_confidence": "calculated",
                    "source_url": None,
                    "_generic": generic,
                })

        out.extend(self.sankrantis(start, end))
        # A named festival supersedes the generic monthly vrat on the same day
        # (e.g. Diwali vs Amavasya, Dev Deepawali vs Purnima Vrat).
        festival_days = {(o["date"], o["tithi"]) for o in out if o["name"] in FESTIVAL_NAMES}
        out = [o for o in out if not (o.pop("_generic", False) and (o["date"], o["tithi"]) in festival_days)]
        out.sort(key=lambda o: (o["date"], o["type"] != "both", o["name"]))
        return out

    def sankrantis(self, start: date, end: date) -> list[dict]:
        s = datetime.combine(start, time(0), IST)
        e = datetime.combine(end, time(0), IST)
        result = []
        for when, sign in self.sky.changes(self.sky.sun_sign, s, e, 1.0):
            if sign != 9:  # Makara (Capricorn)
                continue
            d = when.date()
            if when > self.p.day(d).sunset:
                d += timedelta(days=1)
            result.append({
                "id": f"makar-sankranti-{d.isoformat()}",
                "name": "Makar Sankranti",
                "name_hi": "मकर संक्रांति",
                "type": "tyohar",
                "date": d.isoformat(),
                "tithi": "",
                "paksha": "",
                "month": "",
                "description": "The Sun enters Capricorn; khichdi daan and Ganga snan.",
                "date_confidence": "calculated",
                "source_url": None,
            })
        return result


FESTIVAL_NAMES = {f[0] for f in FESTIVALS}


def slug(text: str) -> str:
    return "-".join("".join(c.lower() if c.isalnum() else " " for c in text).split())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--date", help="YYYY-MM-DD (default: today in IST)")
    parser.add_argument("--days", type=int, default=45, help="calendar window length in days")
    parser.add_argument("--ephemeris-dir", default=".ephemeris", help="where de421.bsp is cached")
    parser.add_argument("--out-dir", default=".", help="where to write the JSON files")
    args = parser.parse_args()

    today = date.fromisoformat(args.date) if args.date else datetime.now(IST).date()
    eph_dir = Path(args.ephemeris_dir)
    eph_dir.mkdir(parents=True, exist_ok=True)

    sky = Sky(eph_dir)
    panchang = Panchang(sky)
    observances = Calendar(panchang).build(today, args.days)
    daily = panchang.daily(today, observances)

    generated = daily["generated_at"]
    calendar = {
        "generated_at": generated,
        "location": daily["location"],
        "window": {"start": today.isoformat(), "end": (today + timedelta(days=args.days - 1)).isoformat()},
        "calculation_method": daily["calculation_method"],
        "observances": observances,
    }

    out = Path(args.out_dir)
    for name, payload in (("varanasi-panchang-daily.json", daily), ("varanasi-panchang-calendar.json", calendar)):
        (out / name).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    t = daily["tithi"][0]
    print(f"{today}: {t['paksha']} {t['name']}, {daily['nakshatra'][0]['name']}; "
          f"sunrise {daily['sunrise'][11:16]}, {len(observances)} observances in calendar")


if __name__ == "__main__":
    main()
