package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.TransaksjonProvider

internal class InMemoryTransaksjonProvider(
    override val oppholdskravvurderinger: InMemoryOppholdskravvurderingRepository = InMemoryOppholdskravvurderingRepository(),
) : TransaksjonProvider<Transaksjonskontekst>,
    Transaksjonskontekst {
    override fun <T> transaksjon(block: (Transaksjonskontekst) -> T): T = block(this)
}
