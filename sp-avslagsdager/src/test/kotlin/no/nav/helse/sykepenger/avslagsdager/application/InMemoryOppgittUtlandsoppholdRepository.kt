package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.MeldingsreferanseId
import no.nav.helse.sykepenger.avslagsdager.domain.OppgittUtlandsopphold

internal class InMemoryOppgittUtlandsoppholdRepository : OppgittUtlandsoppholdRepository {
    private val lagrede = mutableMapOf<MeldingsreferanseId, OppgittUtlandsopphold>()

    override fun lagre(oppgittUtlandsopphold: OppgittUtlandsopphold) {
        lagrede.putIfAbsent(oppgittUtlandsopphold.meldingsreferanseId, oppgittUtlandsopphold)
    }

    override fun finnForPerson(fødselsnummer: String) = lagrede.values.filter { it.fødselsnummer == fødselsnummer }

    override fun finnLagrede(meldingsreferanseIder: List<MeldingsreferanseId>) = meldingsreferanseIder.filter { it in lagrede }.toSet()
}
