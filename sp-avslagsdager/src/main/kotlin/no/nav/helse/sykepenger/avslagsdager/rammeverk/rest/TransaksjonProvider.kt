package no.nav.helse.sykepenger.avslagsdager.rammeverk.rest

interface TransaksjonProvider<TRANSAKSJON> {
    fun <T> transaksjon(block: (TRANSAKSJON) -> T): T
}
