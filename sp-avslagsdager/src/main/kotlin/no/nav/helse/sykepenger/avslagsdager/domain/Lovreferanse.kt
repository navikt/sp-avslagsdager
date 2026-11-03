package no.nav.helse.sykepenger.avslagsdager.domain

import java.time.LocalDate

internal data class Lovreferanse(
    val lov: String,
    val paragraf: String,
    val avsnitt: Int?,
    val setning: Int?,
    val bokstav: String?,
    val iKraftFra: LocalDate,
) {
    companion object {
        // Samme versjon som Spleis bruker når den logger subsumsjon for § 8-9
        private val `§ 8-9 i kraft fra` = LocalDate.of(2021, 6, 1)

        fun `§ 8-9`(
            avsnitt: Int,
            setning: Int? = null,
            bokstav: String? = null,
        ) = Lovreferanse(
            lov = "folketrygdloven",
            paragraf = "8-9",
            avsnitt = avsnitt,
            setning = setning,
            bokstav = bokstav,
            iKraftFra = `§ 8-9 i kraft fra`,
        )
    }
}
