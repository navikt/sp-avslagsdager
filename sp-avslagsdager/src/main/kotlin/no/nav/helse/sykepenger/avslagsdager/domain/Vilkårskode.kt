package no.nav.helse.sykepenger.avslagsdager.domain

import no.nav.helse.sykepenger.avslagsdager.domain.Lovreferanse.Companion.`§ 8-9`

internal enum class Utfall {
    Oppfylt,
    IkkeOppfylt,
}

internal enum class Vilkårskode(
    val lovreferanse: Lovreferanse,
) {
    OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND(`§ 8-9`(avsnitt = 1, setning = 1)),
    OPPHOLD_I_NORGE_IKKE_EØS_BORGER(`§ 8-9`(avsnitt = 1, setning = 2)),
    UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8(`§ 8-9`(avsnitt = 2, bokstav = "a")),
    UNNTAK_INNLAGT_HELSEINSTITUSJON(`§ 8-9`(avsnitt = 2, bokstav = "b")),
    UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER(`§ 8-9`(avsnitt = 3)),
}
