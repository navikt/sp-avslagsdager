package no.nav.helse.sykepenger.avslagsdager.domain

import java.time.LocalDate

/**
 * Den gjeldende vurderingen for hver dag i [periode] som minst én av vurderingene omfatter, sortert på dato. Den
 * gjeldende vurderingen for en dag er den nyeste som omfatter dagen. En omgjøring er en ny vurdering, så en eldre
 * vurdering gjelder videre for dagene omgjøringen ikke omfatter.
 */
internal fun List<Oppholdskravvurdering>.gjeldendePerDag(periode: Periode): Map<LocalDate, Oppholdskravvurdering> =
    flatMap { vurdering -> vurdering.dager.filter { it in periode }.map { dag -> dag to vurdering } }
        .groupBy({ (dag, _) -> dag }, { (_, vurdering) -> vurdering })
        .mapValues { (_, vurderinger) -> vurderinger.maxWith(nyesteSist) }
        .toSortedMap()

// ID-en skiller vurderinger med samme tidspunkt, så svaret blir det samme hver gang
private val nyesteSist = compareBy<Oppholdskravvurdering>({ it.vurdertTidspunkt }, { it.id.value })
