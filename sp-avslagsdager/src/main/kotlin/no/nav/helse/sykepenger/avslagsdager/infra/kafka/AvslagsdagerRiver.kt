package no.nav.helse.sykepenger.avslagsdager.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.avslagsdager.application.AvslagsdagerResultat
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.application.avslagsdager
import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdag
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.TransaksjonProvider
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.medMdc
import java.util.UUID

/**
 * Svarer Spleis med dagene som er avslått i vurderingene Spleis spør om (ADR 0005). Er en vurdering ukjent for
 * personen, svarer riveren ikke. Spleis venter da på løsningen, og feilen må rettes før behovet sendes på nytt.
 * Andre feil, som at databasen er nede, kastes videre, så meldingen leses på nytt.
 */
internal class AvslagsdagerRiver(
    rapidsConnection: RapidsConnection,
    private val transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "behov")
                    it.requireAllOrAny("@behov", listOf(BEHOV))
                    it.forbid("@løsning")
                }
                validate {
                    it.requireKey("fødselsnummer")
                    it.require(VURDERING_IDER) { node ->
                        require(node.isArray && !node.isEmpty) { "$VURDERING_IDER må være en liste med minst én ID" }
                        node.values().forEach { id -> UUID.fromString(id.asString()) }
                    }
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val fødselsnummer = packet["fødselsnummer"].asString()
        val vurderingIder = packet[VURDERING_IDER].values().map { OppholdskravvurderingId(UUID.fromString(it.asString())) }

        medMdc(MdcKey.IDENTITETSNUMMER to fødselsnummer) {
            loggInfo("Mottatt behov for $BEHOV", "vurderingIder" to vurderingIder.joinToString())

            val resultat = transaksjonProvider.transaksjon { it.oppholdskravvurderinger.avslagsdager(fødselsnummer, vurderingIder) }

            when (resultat) {
                is AvslagsdagerResultat.UkjenteVurderinger ->
                    loggError(
                        "Kan ikke svare på behovet $BEHOV. Noen av vurderingene finnes ikke for personen",
                        "ukjenteVurderingIder" to resultat.vurderingIder.joinToString(),
                    )

                is AvslagsdagerResultat.Funnet -> {
                    packet["@løsning"] = mapOf(BEHOV to mapOf("avslagsdager" to resultat.avslagsdager.map { it.tilLøsning() }))
                    loggInfo("Svarer på behov for $BEHOV", "antallAvslagsdager" to resultat.avslagsdager.size.toString())
                    context.publish(packet.toJson())
                }
            }
        }
    }

    private companion object {
        const val BEHOV = "Avslagsdager"
        const val VURDERING_IDER = "$BEHOV.vurderingIder"
    }
}

private fun Avslagsdag.tilLøsning() =
    mapOf(
        "vurderingId" to vurderingId.value,
        "dato" to dato,
        "vilkårskoder" to vilkårskoder.map { it.name },
    )
