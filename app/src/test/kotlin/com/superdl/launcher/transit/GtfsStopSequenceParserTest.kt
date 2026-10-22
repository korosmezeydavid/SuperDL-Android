package com.superdl.launcher.transit

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsStopSequenceParserTest {

    private val routes = """
        route_id,route_short_name,route_long_name,route_type
        R1,4,"4-es villamos, körjárat",0
        R2,7,7-es busz,3
    """.trimIndent()

    private val trips = """
        route_id,service_id,trip_id,trip_headsign,direction_id
        R1,WEEKDAY,T2,"Széll Kálmán tér, irány",1
        R1,WEEKDAY,T1,"Újbuda-központ",0
        R1,WEEKEND,T3,"Újbuda-központ",0
        R2,WEEKDAY,B1,"Bosnyák tér",0
    """.trimIndent()

    private val stops = "\uFEFFstop_id,stop_name,stop_lat,stop_lon\r\n" +
        "S1,Induló,47.47,19.04\r\n" +
        "S2,\"Átkelés, \"\"A\"\" oldal\",47.48,19.05\r\n" +
        "S3,\"Kettős\nnevű megálló\",47.49,19.06\r\n" +
        "S4,Induló,47.50,19.07\r\n"

    private val stopTimes = """
        trip_id,arrival_time,departure_time,stop_id,stop_sequence
        T1,25:04:00,25:04:30,S2,20
        T2,10:00:00,10:00:00,S4,1
        T1,25:00:00,25:00:30,S1,10
        T3,11:00:00,11:00:00,S1,1
        T1,25:08:00,25:08:30,S3,30
        B1,12:00:00,12:00:00,S4,1
        T1,25:12:00,25:12:30,S1,40
        T2,10:05:00,10:05:00,S3,2
        T3,11:05:00,11:05:00,S4,2
    """.trimIndent()

    @Test
    fun chosenTripHasNumericStopOrderAndPreservesQuotedCsv() {
        val result = trip("T1")!!

        assertEquals("4", result.route.shortName)
        assertEquals("4-es villamos, körjárat", result.route.longName)
        assertEquals("Újbuda-központ", result.trip.headsign)
        assertEquals("WEEKDAY", result.trip.serviceId)
        assertEquals(listOf(10, 20, 30, 40), result.stops.map { it.stopSequence })
        assertEquals(listOf("S1", "S2", "S3", "S1"), result.stops.map { it.stop.id })
        assertEquals("Átkelés, \"A\" oldal", result.stops[1].stop.name)
        assertEquals("Kettős\nnevű megálló", result.stops[2].stop.name)
        assertEquals("25:04:30", result.stops[1].departureTime)
        assertEquals(47.48, result.stops[1].stop.latitude, 0.00001)
    }

    @Test
    fun routeSelectionReturnsAllMatchingTripsInStableOrder() {
        val all = route("R1")
        assertEquals(listOf("T1", "T2", "T3"), all.map { it.trip.id })
        assertEquals(listOf("S4", "S3"), all[1].stops.map { it.stop.id })

        val oneDirection = route("R1", directionId = 0)
        assertEquals(listOf("T1", "T3"), oneDirection.map { it.trip.id })
        assertEquals(listOf("S1", "S4"), oneDirection[1].stops.map { it.stop.id })
        assertEquals(2, oneDirection[0].stops.count { it.stop.id == "S1" })
    }

    @Test
    fun routePatternsKeepOneTripPerDirectionAndStopOrder() {
        val patterns = GtfsStopSequenceParser.forRoutePatterns(
            StringReader(stops), StringReader(routes), StringReader(trips),
            StringReader(stopTimes), "R1"
        )
        assertEquals(listOf("T1", "T2", "T3"), patterns.map { it.trip.id })
        val repeated = trips + "\nR1,WEEKDAY,T4,Újbuda-központ,0"
        val repeatedTimes = stopTimes + "\nT4,12:00:00,12:00:00,S1,1" +
            "\nT4,12:05:00,12:05:00,S4,2"
        val deduplicated = GtfsStopSequenceParser.forRoutePatterns(
            StringReader(stops), StringReader(routes), StringReader(repeated),
            StringReader(repeatedTimes), "R1"
        )
        assertEquals(listOf("T1", "T2", "T3"), deduplicated.map { it.trip.id })
        assertEquals(listOf("4", "7"),
            GtfsStopSequenceParser.listRoutes(StringReader(routes)).map { it.shortName })
    }

    @Test
    fun unknownSelectionIsEmpty() {
        assertNull(trip("missing"))
        assertTrue(route("missing").isEmpty())
    }

    @Test
    fun missingSelectedStopFailsInsteadOfSilentlyShorteningRoute() {
        val invalid = stopTimes.replace("S3,30", "UNKNOWN,30")
        val error = assertThrows(GtfsFormatException::class.java) {
            trip("T1", times = invalid)
        }
        assertTrue(error.message!!.contains("missing stop UNKNOWN"))
    }

    @Test
    fun duplicateSequenceFailsInsteadOfChoosingArbitraryStop() {
        val invalid = stopTimes.replace("S3,30", "S3,20")
        val error = assertThrows(GtfsFormatException::class.java) {
            trip("T1", times = invalid)
        }
        assertTrue(error.message!!.contains("duplicate stop_sequence"))
    }

    @Test
    fun malformedQuotedCsvIsRejected() {
        val invalidStops = "stop_id,stop_name,stop_lat,stop_lon\nS1,\"Unclosed,47.47,19.04"
        val error = assertThrows(GtfsFormatException::class.java) {
            trip("T1", stopData = invalidStops)
        }
        assertTrue(error.message!!.contains("unterminated quoted field"))
    }

    private fun trip(id: String, stopData: String = stops, times: String = stopTimes) =
        GtfsStopSequenceParser.forTrip(
            StringReader(stopData), StringReader(routes), StringReader(trips), StringReader(times), id
        )

    private fun route(id: String, directionId: Int? = null) =
        GtfsStopSequenceParser.forRoute(
            StringReader(stops), StringReader(routes), StringReader(trips), StringReader(stopTimes),
            id, directionId
        )
}
