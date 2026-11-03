package no.nav.helse.sykepenger.avslagsdager.application

internal interface Transaksjonskontekst {
    val oppholdskravvurderinger: OppholdskravvurderingRepository
}
