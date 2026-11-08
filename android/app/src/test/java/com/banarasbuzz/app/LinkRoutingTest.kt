package com.banarasbuzz.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which links stay in the in-app browser and which go to the phone's own apps. */
class LinkRoutingTest {
    @Test
    fun newsAndWebPagesStayInApp() {
        listOf(
            "https://www.jagran.com/uttar-pradesh/varanasi-city-food-safety-40427666.html",
            "https://www.livehindustan.com/uttar-pradesh/varanasi/story-वाराणसी-खबर-123.html",
            "https://shrikashivishwanath.org/",
            "https://prabhat-twr.github.io/vns-news-api/privacy.html",
            "http://example.com/page?x=1#top",
            "https://www.google.com/search?q=varanasi",
        ).forEach { assertFalse("should open in app: $it", opensOutsideApp(it)) }
    }

    @Test
    fun mapsStoreAndOtherAppsGoOutside() {
        listOf(
            "https://www.google.com/maps/search/?api=1&query=Dashashwamedh+Ghat",
            "https://maps.google.com/?q=Varanasi",
            "https://maps.app.goo.gl/abc",
            "https://play.google.com/store/apps/details?id=com.banarasbuzz.app",
            "tel:+911234567890",
            "mailto:someone@example.com",
            "intent://scan/#Intent;scheme=zxing;end",
            "whatsapp://send?text=hi",
            "not a url",
        ).forEach { assertTrue("should open outside: $it", opensOutsideApp(it)) }
    }
}
