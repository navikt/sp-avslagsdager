package no.nav.helse.sykepenger.avslagsdager.application

internal interface Transaksjonskontekst {
    val oppholdskravvurderinger: OppholdskravvurderingRepository
    val outbox: Outbox
    val oppgittUtlandsopphold: OppgittUtlandsoppholdRepository
    val grunnlagForLøpendeVilkår: GrunnlagForLøpendeVilkårRepository
}
