package com.carlink.core.settings

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class CarBrandTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences(CarBrandStore.PREFS, Context.MODE_PRIVATE)
    private var detected = VehicleProfile.GENERIC
    private fun store() = CarBrandStore(prefs) { detected }

    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun onlyBydImpliesTheBydProfile() {
        assertEquals(listOf(CarBrand.BYD), CarBrand.entries.filter { it.profile == VehicleProfile.BYD_DILINK })
        // LPlay: Leapmotor has a profile of its own; every other make is generic.
        CarBrand.entries.filter { it != CarBrand.BYD && it != CarBrand.LEAPMOTOR }
            .forEach { assertEquals(it.name, VehicleProfile.GENERIC, it.profile) }
    }

    @Test fun onlyLeapmotorImpliesTheLeapmotorProfile() {
        assertEquals(listOf(CarBrand.LEAPMOTOR), CarBrand.entries.filter { it.profile == VehicleProfile.LEAPMOTOR_C01 })
        assertEquals(CarBrand.LEAPMOTOR, CarBrand.detected(VehicleProfile.LEAPMOTOR_C01))
        assertEquals(CarBrand.LEAPMOTOR, CarBrand.fromProfile(VehicleProfile.LEAPMOTOR_C01))
        assertEquals(CarBrand.OTHER, CarBrand.fromProfile(VehicleProfile.GENERIC))
    }

    @Test fun theLeapmotorProfileIsDetectedFromTheCarsMultimediaAppOrTheFingerprint() {
        assertEquals(VehicleProfile.LEAPMOTOR_C01, VehicleProfile.detect(multimediaInstalled = true, fingerprint = "qti/msmnile_gvmq/x"))
        assertEquals(VehicleProfile.LEAPMOTOR_C01, VehicleProfile.detect(multimediaInstalled = false, fingerprint = "Leapmotor/C01/x"))
        assertEquals(VehicleProfile.GENERIC, VehicleProfile.detect(multimediaInstalled = false, fingerprint = "qti/msmnile_gvmq/x"))
        // LPlay does not look for BYD.
        assertEquals(VehicleProfile.GENERIC, VehicleProfile.detect(multimediaInstalled = false, fingerprint = "BYD-AUTO/DiLink/x"))
    }

    @Test fun choosingLeapmotorOnADetectedLeapmotorIsNoChange() {
        detected = VehicleProfile.LEAPMOTOR_C01
        assertNull(store().chosen())
        assertEquals(VehicleProfile.LEAPMOTOR_C01, store().profile())
        assertFalse(store().set(CarBrand.LEAPMOTOR))
        assertTrue("Other turns the Leapmotor profile off", store().set(CarBrand.OTHER))
        assertEquals(VehicleProfile.GENERIC, store().profile())
    }

    @Test fun popularBrandsComeFirstThenLuxuryThenOther() {
        val all = CarBrand.entries
        assertEquals(CarBrand.MARUTI_SUZUKI, all.first())
        assertEquals(CarBrand.OTHER, all.last())
        val firstLuxury = all.indexOfFirst { it.luxury }
        val firstMotorcycle = all.indexOfFirst { it.motorcycle }
        assertTrue(all.subList(firstLuxury, firstMotorcycle).all { it.luxury })
        assertTrue(all.drop(firstMotorcycle).dropLast(1).all { it.motorcycle })
        assertTrue(all.indexOf(CarBrand.BYD) < firstLuxury)
    }

    @Test fun nothingChosenFallsBackToDetection() {
        assertNull(store().chosen())
        assertEquals(VehicleProfile.GENERIC, store().profile())
        detected = VehicleProfile.BYD_DILINK
        assertNull(store().chosen())
        assertEquals(VehicleProfile.BYD_DILINK, store().profile())
        assertEquals(CarBrand.BYD, CarBrand.detected(VehicleProfile.BYD_DILINK))
        assertNull(CarBrand.detected(VehicleProfile.GENERIC))
    }

    @Test fun legacyBydProfileMigratesToBydBrand() {
        prefs.edit().putString(CarBrandStore.KEY_LEGACY_PROFILE, "BYD_DILINK").commit()
        assertEquals(CarBrand.BYD, store().chosen())
        assertEquals(VehicleProfile.BYD_DILINK, store().profile())
        assertEquals("BYD", prefs.getString(CarBrandStore.KEY_BRAND, null))
        assertFalse(prefs.contains(CarBrandStore.KEY_LEGACY_PROFILE))
    }

    @Test fun legacyGenericProfileMigratesToOtherEvenOnADetectedByd() {
        detected = VehicleProfile.BYD_DILINK
        prefs.edit().putString(CarBrandStore.KEY_LEGACY_PROFILE, "GENERIC").commit()
        assertEquals(CarBrand.OTHER, store().chosen())
        assertEquals(VehicleProfile.GENERIC, store().profile())
    }

    @Test fun unreadableValuesCountAsNotChosen() {
        prefs.edit().putString(CarBrandStore.KEY_LEGACY_PROFILE, "FYT").commit()
        assertNull(store().chosen())
        prefs.edit().putString(CarBrandStore.KEY_BRAND, "DELOREAN").commit()
        assertNull(store().chosen())
    }

    @Test fun aStoredBrandWinsOverALegacyProfile() {
        prefs.edit().putString(CarBrandStore.KEY_BRAND, "HYUNDAI").putString(CarBrandStore.KEY_LEGACY_PROFILE, "BYD_DILINK").commit()
        assertEquals(CarBrand.HYUNDAI, store().chosen())
    }

    @Test fun setReportsAChangeOnlyWhenTheProfileChanges() {
        assertFalse("generic to Hyundai keeps the generic profile", store().set(CarBrand.HYUNDAI))
        assertEquals(CarBrand.HYUNDAI, store().chosen())
        assertFalse(store().set(CarBrand.TATA))
        assertTrue("Tata to BYD turns on the BYD profile", store().set(CarBrand.BYD))
        assertFalse(store().set(CarBrand.BYD))
        assertTrue(store().set(CarBrand.OTHER))
        assertEquals(VehicleProfile.GENERIC, store().profile())
    }

    @Test fun choosingBydOnADetectedBydIsNoChange() {
        detected = VehicleProfile.BYD_DILINK
        assertFalse(store().set(CarBrand.BYD))
        assertTrue(store().set(CarBrand.KIA))
    }
}
