package no.nav.helse.sykepenger.avslagsdager.domain

import java.time.LocalDate

internal data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
) {
    init {
        require(!tom.isBefore(fom)) { "tom ($tom) kan ikke være før fom ($fom)" }
    }

    operator fun contains(dato: LocalDate) = !dato.isBefore(fom) && !dato.isAfter(tom)
}

/** Grupperer dagene til sammenhengende kalenderperioder. Helg og helligdager bryter ikke en periode. */
internal fun Collection<LocalDate>.tilPerioder(): List<Periode> =
    distinct().sorted().fold(emptyList()) { perioder, dag ->
        val forrige = perioder.lastOrNull()
        if (forrige != null && forrige.tom.plusDays(1) == dag) {
            perioder.dropLast(1) + forrige.copy(tom = dag)
        } else {
            perioder + Periode(dag, dag)
        }
    }
