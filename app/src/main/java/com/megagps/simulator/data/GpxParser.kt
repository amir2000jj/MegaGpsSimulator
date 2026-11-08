package com.megagps.simulator.data

import android.util.Xml
import com.megagps.simulator.domain.GeoPoint
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/** Minimal GPX reader: prefers track points, then route points, then waypoints. */
object GpxParser {
    fun parse(input: InputStream): List<GeoPoint> {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        val trk = ArrayList<GeoPoint>()
        val rte = ArrayList<GeoPoint>()
        val wpt = ArrayList<GeoPoint>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val target = when (parser.name) {
                    "trkpt" -> trk
                    "rtept" -> rte
                    "wpt" -> wpt
                    else -> null
                }
                if (target != null) {
                    val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                    val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        val p = GeoPoint(lat, lon)
                        if (p.isValid) target.add(p)
                    }
                }
            }
            event = parser.next()
        }
        return when {
            trk.size >= 2 -> trk
            rte.size >= 2 -> rte
            else -> wpt
        }
    }
}
