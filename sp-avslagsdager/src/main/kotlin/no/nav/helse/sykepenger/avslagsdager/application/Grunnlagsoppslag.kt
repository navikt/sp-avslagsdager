package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdagerbehov
import no.nav.helse.sykepenger.avslagsdager.domain.GrunnlagForLøpendeVilkår
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.TransaksjonProvider

/**
 * Finner grunnlaget for løpende vilkår for [behov] (ADR 0023). Søknader appen ikke har sett før, hentes fra
 * [søknadsoppslag] utenfor transaksjonen. Feiler oppslaget, kastes feilen, og ingenting lagres.
 */
internal fun TransaksjonProvider<Transaksjonskontekst>.grunnlagForLøpendeVilkår(
    behov: Avslagsdagerbehov,
    søknadsoppslag: Søknadsoppslag,
): GrunnlagForLøpendeVilkår {
    val søknader =
        behov.yrkesaktiviteter
            .flatMap { yrkesaktivitet -> yrkesaktivitet.søknadMeldingsreferanseIder.map { it to yrkesaktivitet.yrkesaktivitet } }
            .distinctBy { (meldingsreferanseId, _) -> meldingsreferanseId }
    val lagrede = transaksjon { it.oppgittUtlandsopphold.finnLagrede(søknader.map { (meldingsreferanseId, _) -> meldingsreferanseId }) }
    val nye =
        søknader
            .filterNot { (meldingsreferanseId, _) -> meldingsreferanseId in lagrede }
            .map { (meldingsreferanseId, yrkesaktivitet) -> søknadsoppslag.hent(meldingsreferanseId, yrkesaktivitet, behov.fødselsnummer) }

    return transaksjon { kontekst ->
        nye.forEach { kontekst.oppgittUtlandsopphold.lagre(it) }
        val siste = kontekst.grunnlagForLøpendeVilkår.finnSiste(behov.fødselsnummer, behov.periode)
        val grunnlag =
            GrunnlagForLøpendeVilkår.forBehov(
                behov = behov,
                oppgittUtlandsopphold = kontekst.oppgittUtlandsopphold.finnForPerson(behov.fødselsnummer),
                vurderinger = kontekst.oppholdskravvurderinger.finnForPeriode(behov.fødselsnummer, behov.periode),
                siste = siste,
            )
        if (grunnlag.id != siste?.id) kontekst.grunnlagForLøpendeVilkår.lagre(grunnlag)
        grunnlag
    }
}
