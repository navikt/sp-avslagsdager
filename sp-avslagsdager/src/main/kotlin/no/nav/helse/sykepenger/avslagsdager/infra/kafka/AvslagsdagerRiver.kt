package no.nav.helse.sykepenger.avslagsdager.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.application.avslagsdager
import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdag
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.VurdertDag
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.TransaksjonProvider
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.medMdc
import tools.jackson.databind.JsonNode

/**
 * Svarer Spleis med de gjeldende vurderingene og de avslåtte dagene i beregningsperioden (ADR 0005). Appen svarer for
 * alle personer, også dem uten vurderinger. Feil, som at databasen er nede, kastes videre, så meldingen leses på nytt.
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
                validate { message ->
                    message.requireKey("fødselsnummer")
                    message.require(FOM, JsonNode::asLocalDate)
                    message.require(TOM) { tom -> Periode(message[FOM].asLocalDate(), tom.asLocalDate()) }
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
        val periode = packet.periode()

        medMdc(MdcKey.IDENTITETSNUMMER to fødselsnummer) {
            loggInfo("Mottatt behov for $BEHOV", "fom" to periode.fom.toString(), "tom" to periode.tom.toString())

            val løsning = transaksjonProvider.transaksjon { it.oppholdskravvurderinger.avslagsdager(fødselsnummer, periode) }

            packet["@løsning"] =
                mapOf(
                    BEHOV to
                        mapOf(
                            "vurderingIder" to løsning.vurderingIder.map { it.value },
                            "avslagsdager" to løsning.avslagsdager.map { it.tilLøsning() },
                            "vurderteDager" to løsning.vurderteDager.map { it.tilLøsning() },
                        ),
                )
            loggInfo(
                "Svarer på behov for $BEHOV",
                "antallVurderinger" to løsning.vurderingIder.size.toString(),
                "antallAvslagsdager" to løsning.avslagsdager.size.toString(),
                "antallVurderteDager" to løsning.vurderteDager.size.toString(),
            )
            context.publish(packet.toJson())
        }
    }

    private fun JsonMessage.periode() = Periode(this[FOM].asLocalDate(), this[TOM].asLocalDate())

    private companion object {
        const val BEHOV = "Avslagsdager"
        const val FOM = "$BEHOV.fom"
        const val TOM = "$BEHOV.tom"
    }
}

private fun Avslagsdag.tilLøsning() =
    mapOf(
        "vurderingId" to vurderingId.value,
        "dato" to dato,
        "vilkårskoder" to vilkårskoder.map { it.name },
    )

private fun VurdertDag.tilLøsning() =
    mapOf(
        "vurderingId" to vurderingId.value,
        "vilkår" to vilkår.name,
        "dato" to dato,
        "utfall" to utfall.name,
        "vilkårskoder" to vilkårskoder.map { it.name },
    )
