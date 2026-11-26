package no.nav.helse.sykepenger.avslagsdager.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.avslagsdager.application.SøknadenKunneIkkeHentes
import no.nav.helse.sykepenger.avslagsdager.application.Søknadsoppslag
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.application.grunnlagForLøpendeVilkår
import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdag
import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdagerbehov
import no.nav.helse.sykepenger.avslagsdager.domain.Dagtype
import no.nav.helse.sykepenger.avslagsdager.domain.MeldingsreferanseId
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.Sykdomsdag
import no.nav.helse.sykepenger.avslagsdager.domain.Varsel
import no.nav.helse.sykepenger.avslagsdager.domain.VurdertDag
import no.nav.helse.sykepenger.avslagsdager.domain.Yrkesaktivitet
import no.nav.helse.sykepenger.avslagsdager.domain.YrkesaktivitetIBehov
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.TransaksjonProvider
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.medMdc
import tools.jackson.databind.JsonNode
import java.util.UUID

/**
 * Svarer Spleis med grunnlaget for løpende vilkår i beregningsperioden (ADR 0023), og med utfallet av den gjeldende
 * vurderingen for hver vurderte dag (ADR 0005). Appen svarer for alle personer, også dem uten vurderinger. Kan en søknad
 * ikke hentes fra Spedisjon, får behovet ikke svar, og Spleis spør på nytt ved neste påminnelse. Andre feil, som at
 * databasen er nede, kastes videre, så meldingen leses på nytt.
 */
internal class AvslagsdagerRiver(
    rapidsConnection: RapidsConnection,
    private val transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
    private val søknadsoppslag: Søknadsoppslag,
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
                    message.interestedIn(YRKESAKTIVITETER) { it.yrkesaktiviteter() }
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

            val behov = Avslagsdagerbehov(fødselsnummer, periode, packet[YRKESAKTIVITETER].yrkesaktiviteter())
            val grunnlag =
                try {
                    transaksjonProvider.grunnlagForLøpendeVilkår(behov, søknadsoppslag)
                } catch (feil: SøknadenKunneIkkeHentes) {
                    loggError(
                        "Kunne ikke hente søknaden fra Spedisjon, svarer ikke på behov for $BEHOV",
                        feil,
                        "meldingsreferanseId" to feil.meldingsreferanseId.toString(),
                    )
                    return@medMdc
                }
            val innhold = grunnlag.innhold

            packet["@løsning"] =
                mapOf(
                    BEHOV to
                        mapOf(
                            "grunnlagForLøpendeVilkårId" to grunnlag.id.value,
                            "avslagsdager" to innhold.avslagsdager.map { it.tilLøsning() },
                            "varsler" to innhold.varsler.map { it.tilLøsning() },
                            "vurderteDager" to innhold.vurderteDager.map { it.tilLøsning() },
                        ),
                )
            loggInfo(
                "Svarer på behov for $BEHOV",
                "grunnlagForLøpendeVilkårId" to grunnlag.id.toString(),
                "antallDagerTilVurdering" to innhold.dagerTilVurdering.size.toString(),
                "antallAvslagsdager" to innhold.avslagsdager.size.toString(),
                "antallVurderteDager" to innhold.vurderteDager.size.toString(),
            )
            context.publish(packet.toJson())
        }
    }

    private fun JsonMessage.periode() = Periode(this[FOM].asLocalDate(), this[TOM].asLocalDate())

    private companion object {
        const val BEHOV = "Avslagsdager"
        const val FOM = "$BEHOV.fom"
        const val TOM = "$BEHOV.tom"
        const val YRKESAKTIVITETER = "$BEHOV.yrkesaktiviteter"
    }
}

// Et behov uten yrkesaktiviteter har ingen dager som må vurderes
private fun JsonNode.yrkesaktiviteter() =
    values().map { yrkesaktivitet ->
        YrkesaktivitetIBehov(
            yrkesaktivitet =
                Yrkesaktivitet(
                    yrkesaktivitetstype = yrkesaktivitet.path("yrkesaktivitetstype").asString(),
                    organisasjonsnummer = yrkesaktivitet.path("organisasjonsnummer").takeIf { it.isString }?.asString(),
                ),
            sykdomstidslinje =
                yrkesaktivitet.path("sykdomstidslinje").values().map { dag ->
                    Sykdomsdag(dag.path("dato").asLocalDate(), Dagtype(dag.path("dagtype").asString()))
                },
            søknadMeldingsreferanseIder =
                yrkesaktivitet.path("søknadMeldingsreferanseIder").values().map { MeldingsreferanseId(UUID.fromString(it.asString())) },
        )
    }

private fun Avslagsdag.tilLøsning() =
    mapOf(
        "dato" to dato,
        "vilkår" to vilkår.name,
        "vilkårskoder" to vilkårskoder.map { it.name },
    )

private fun Varsel.tilLøsning() =
    mapOf(
        "yrkesaktivitetstype" to yrkesaktivitet.yrkesaktivitetstype,
        "organisasjonsnummer" to yrkesaktivitet.organisasjonsnummer,
        "varselkode" to varselkode.name,
    )

private fun VurdertDag.tilLøsning() =
    mapOf(
        "vurderingId" to vurderingId.value,
        "vilkår" to vilkår.name,
        "dato" to dato,
        "utfall" to utfall.name,
        "vilkårskoder" to vilkårskoder.map { it.name },
    )
