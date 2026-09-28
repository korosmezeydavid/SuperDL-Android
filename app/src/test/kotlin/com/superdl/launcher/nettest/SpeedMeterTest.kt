package com.superdl.launcher.nettest

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A mérés számítási része — hamis órával, hálózat nélkül.
 *
 * A bemelegítés-levonás a sebességmérés lelke: ha elromlik, a telefon a TCP
 * lassú indulását is „sebességnek" mérné, és rendszeresen kevesebbet mondana
 * a valóságnál.
 */
class SpeedMeterTest {

    private class FakeClock(var now: Double = 100.0) {
        fun read(): Double = now
    }

    @Test
    fun bemelegitesUtanCsakAzEgyenletesSzakasz() {
        val c = FakeClock()
        val counter = SpeedCounter(warmup = 1.0, clock = c::read)
        counter.add(500_000)          // t=100.0: az első bájt indítja az órát
        c.now = 100.5
        counter.add(500_000)          // még bemelegítés
        c.now = 101.0
        counter.add(1_000_000)        // itt ér véget a bemelegítés: ez a kezdőpont
        c.now = 102.0
        counter.add(1_000_000)        // 1 MB 1 s alatt = 8 Mbit/s
        val (mbps, bytes) = counter.result()
        assertEquals(8.0, mbps, 0.0)
        assertEquals(3_000_000L, bytes)
    }

    @Test
    fun rovidMeresnelATeljesSzakasz() {
        val c = FakeClock()
        val counter = SpeedCounter(warmup = 1.0, clock = c::read)
        counter.add(100_000)
        c.now = 100.5
        counter.add(400_000)
        // a bemelegítés le sem telt: becsületesen a teljes szakaszból számolunk
        val (mbps, bytes) = counter.result()
        assertEquals(NetTestText.mbps(500_000, 0.5), mbps, 0.0)
        assertEquals(500_000L, bytes)
    }

    @Test
    fun egyetlenAdagNemSebesseg() {
        val c = FakeClock()
        val counter = SpeedCounter(warmup = 1.0, clock = c::read)
        assertEquals(0.0 to 0L, counter.result())
        counter.add(1234)
        assertEquals(0.0 to 1234L, counter.result())
    }

    @Test
    fun nullaBemelegites() {
        val c = FakeClock()
        val counter = SpeedCounter(warmup = 0.0, clock = c::read)
        counter.add(1000)             // t0 azonnal: az első adag NEM számít bele
        c.now = 101.0
        counter.add(125_000)          // 125 kB / 1 s = 1 Mbit/s
        assertEquals(1.0, counter.result().first, 0.0)
    }

    @Test
    fun mintakMediannal() {
        val r = SpeedMeter.summarize(listOf(2.0, 10.0, 45.0, 9.5, 11.0), 12.0, 10_000L)
        assertEquals(10.0, r.mbps, 0.0)
        assertEquals(45.0, r.peakMbps, 0.0)
        assertEquals(listOf(2.0, 10.0, 45.0, 9.5, 11.0), r.samples)
        assertTrue(r.fluctuating)
    }

    @Test
    fun mintakNelkulAzAtlag() {
        val r = SpeedMeter.summarize(emptyList(), 7.25, 5L)
        assertEquals(7.25, r.mbps, 0.0)
        assertEquals(7.25, r.peakMbps, 0.0)
        assertFalse(r.fluctuating)
    }

    @Test
    fun nullaMedianEseteAtlag() {
        // Python: `mbps=kozep or atlag_mbps` — a 0 medián helyett az átlag
        val r = SpeedMeter.summarize(listOf(0.0, 0.0, 3.0), 1.5, 5L)
        assertEquals(1.5, r.mbps, 0.0)
        assertEquals(3.0, r.peakMbps, 0.0)
    }

    @Test
    fun parosMedian() {
        assertEquals(2.5, SpeedMeter.median(listOf(4.0, 1.0, 2.0, 3.0)), 0.0)
        assertEquals(0.0, SpeedMeter.median(emptyList()), 0.0)
    }

    @Test
    fun kesleltetesStatisztika() {
        val s = NetProbe.latencyStats(listOf(20.0, 24.0, 22.0), tried = 4)
        // legkisebb, átlag, jitter (|24-20| + |22-24|) / 2, sikeres 3/4
        assertArrayEquals(doubleArrayOf(20.0, 22.0, 3.0, 75.0), s, 0.0)
        assertArrayEquals(doubleArrayOf(0.0, 0.0, 0.0, 0.0), NetProbe.latencyStats(emptyList(), 3), 0.0)
    }

    @Test
    fun traceEsOrg() {
        val d = NetProbe.parseTrace("fl=1\nip=84.2.33.144\nloc=HU\ncolo=BUD\nhttp=http/2\n")
        assertEquals("84.2.33.144", d["ip"])
        assertEquals("BUD", d["colo"])
        assertEquals("5483" to "Magyar Telekom plc.", NetProbe.splitOrg("AS5483 Magyar Telekom plc."))
        assertEquals("5483" to "", NetProbe.splitOrg("AS5483"))
        assertEquals("" to "Valami Kft.", NetProbe.splitOrg("Valami Kft."))
    }

    @Test
    fun megszakitasLezarjaANyitottKapcsolatot() {
        val stop = StopSignal()
        var closed = 0
        stop.register { closed++ }
        stop.set()
        assertEquals(1, closed)
        // a megszakítás UTÁN nyitott kapcsolat is azonnal záródik
        stop.register { closed++ }
        assertEquals(2, closed)
        assertTrue(stop.isSet)
    }

    @Test
    fun megszakitasUtanNincsMentes() {
        val stop = StopSignal()
        var saved = 0
        assertEquals(1, stop.unlessStopped { ++saved })
        var closed = 0
        stop.register { closed++ }
        stop.set(closeNow = false)        // fő szál: csak a jelző, bontás nélkül
        assertEquals(0, closed)
        assertNull(stop.unlessStopped { ++saved })
        assertEquals(1, saved)
        stop.closeAll()                   // háttérszál: bontás
        assertEquals(1, closed)
    }
}
