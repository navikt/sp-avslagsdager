package no.nav.helse.sykepenger.avslagsdager.rammeverk.rest

interface ApiErrorCode {
    val httpStatus: Int
    val tittel: String
}
