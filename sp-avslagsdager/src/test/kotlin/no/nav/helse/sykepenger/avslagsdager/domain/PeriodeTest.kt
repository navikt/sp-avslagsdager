package no.nav.helse.sykepenger.avslagsdager.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class PeriodeTest {
    @Test
    fun `sammenhengende dager blir én periode`() {
        // Given
        val dager = listOf(1.jan, 2.jan, 3.jan)

        // When
        val perioder = dager.tilPerioder()

        // Then
        assertEquals(listOf(Periode(1.jan, 3.jan)), perioder)
    }

    @Test
    fun `dager som ikke henger sammen gir flere perioder`() {
        // Given
        val dager = listOf(1.jan, 2.jan, 5.jan, 7.jan, 8.jan)

        // When
        val perioder = dager.tilPerioder()

        // Then
        assertEquals(listOf(Periode(1.jan, 2.jan), Periode(5.jan, 5.jan), Periode(7.jan, 8.jan)), perioder)
    }

    @Test
    fun `helg bryter ikke perioden`() {
        // Given: fredag til mandag
        val dager = listOf(2.jan, 3.jan, 4.jan, 5.jan)

        // When
        val perioder = dager.tilPerioder()

        // Then
        assertEquals(listOf(Periode(2.jan, 5.jan)), perioder)
    }

    @Test
    fun `usorterte og dupliserte dager gir samme perioder`() {
        // Given
        val dager = listOf(3.jan, 1.jan, 2.jan, 2.jan, 10.jan)

        // When
        val perioder = dager.tilPerioder()

        // Then
        assertEquals(listOf(Periode(1.jan, 3.jan), Periode(10.jan, 10.jan)), perioder)
    }

    @Test
    fun `ingen dager gir ingen perioder`() {
        // When
        val perioder = emptyList<LocalDate>().tilPerioder()

        // Then
        assertEquals(emptyList<Periode>(), perioder)
    }
}

private val Int.jan get() = LocalDate.of(2026, 1, this)
