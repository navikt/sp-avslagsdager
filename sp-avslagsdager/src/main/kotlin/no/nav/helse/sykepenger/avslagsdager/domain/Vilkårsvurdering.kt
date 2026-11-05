package no.nav.helse.sykepenger.avslagsdager.domain

/**
 * Lovreferansen lagres sammen med vurderingen, slik at hjemmelen slik den var da vurderingen ble gjort, er bevart, også
 * om [Vilkårskode] senere får en ny lovreferanse.
 */
internal data class Vilkårsvurdering(
    val vilkårskode: Vilkårskode,
    val utfall: Utfall,
    val lovreferanse: Lovreferanse,
)
