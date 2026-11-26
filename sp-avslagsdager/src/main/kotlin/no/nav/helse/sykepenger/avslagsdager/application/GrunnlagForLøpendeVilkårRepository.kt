package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.GrunnlagForLøpendeVilkår
import no.nav.helse.sykepenger.avslagsdager.domain.Periode

internal interface GrunnlagForLøpendeVilkårRepository {
    fun lagre(grunnlag: GrunnlagForLøpendeVilkår)

    /** Det sist lagrede grunnlaget for personen og nøyaktig denne beregningsperioden. */
    fun finnSiste(
        fødselsnummer: String,
        periode: Periode,
    ): GrunnlagForLøpendeVilkår?
}
