package no.nav.helse.sykepenger.avslagsdager.application

internal class InMemoryOutbox : Outbox {
    private val _meldinger = mutableListOf<Melding>()
    internal val meldinger: List<Melding> get() = _meldinger.toList()

    override fun leggTil(
        fødselsnummer: String,
        melding: String,
    ) {
        _meldinger.add(Melding(fødselsnummer, melding))
    }

    internal data class Melding(
        val fødselsnummer: String,
        val melding: String,
    )
}
