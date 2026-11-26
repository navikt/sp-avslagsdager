package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.GrunnlagForLøpendeVilkår
import no.nav.helse.sykepenger.avslagsdager.domain.Periode

internal class InMemoryGrunnlagForLøpendeVilkårRepository : GrunnlagForLøpendeVilkårRepository {
    private val grunnlag = mutableListOf<GrunnlagForLøpendeVilkår>()

    override fun lagre(grunnlag: GrunnlagForLøpendeVilkår) {
        check(this.grunnlag.none { it.id == grunnlag.id }) { "Grunnlaget ${grunnlag.id} er allerede lagret" }
        this.grunnlag.add(grunnlag)
    }

    override fun finnSiste(
        fødselsnummer: String,
        periode: Periode,
    ) = grunnlag.lastOrNull { it.innhold.behov.fødselsnummer == fødselsnummer && it.innhold.behov.periode == periode }
}
