package no.nav.helse.sykepenger.avslagsdager.infra.spedisjon

import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.spedisjon.SpedisjonClient
import no.nav.helse.sykepenger.avslagsdager.application.SøknadenKunneIkkeHentes
import no.nav.helse.sykepenger.avslagsdager.application.Søknadsoppslag
import no.nav.helse.sykepenger.avslagsdager.domain.MeldingsreferanseId
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.SøknadId
import no.nav.helse.sykepenger.avslagsdager.domain.OppgittUtlandsopphold
import no.nav.helse.sykepenger.avslagsdager.domain.Yrkesaktivitet
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Henter søknaden fra Spedisjon og leser oppholdene utenfor EØS: `fravar` med type `UTLANDSOPPHOLD`, slik
 * `SøknadBuilder.fravær` i Spleis gjør. Søknaden kan være sendt med et annet identitetsnummer enn behovet har, så
 * `fnr` i meldingen sjekkes ikke.
 */
internal class SpedisjonSøknadsoppslag(
    private val spedisjonClient: SpedisjonClient,
) : Søknadsoppslag {
    override fun hent(
        meldingsreferanseId: MeldingsreferanseId,
        yrkesaktivitet: Yrkesaktivitet,
        fødselsnummer: String,
    ): OppgittUtlandsopphold {
        val melding =
            try {
                spedisjonClient.hentMelding(meldingsreferanseId.value).getOrThrow()
            } catch (feil: Exception) {
                throw SøknadenKunneIkkeHentes(meldingsreferanseId, feil)
            }
        val søknad = objectMapper.readTree(melding.jsonBody)
        return OppgittUtlandsopphold(
            meldingsreferanseId = meldingsreferanseId,
            søknadId = SøknadId(melding.eksternDokumentId),
            korrigerer = søknad.path("korrigerer").takeIf { it.isString }?.let { SøknadId(UUID.fromString(it.asString())) },
            fødselsnummer = fødselsnummer,
            yrkesaktivitet = yrkesaktivitet,
            utlandsoppholdsperioder =
                søknad
                    .path("fravar")
                    .values()
                    .filter { it.path("type").asString() == "UTLANDSOPPHOLD" }
                    .map { Periode(it.dato("fom"), it.dato("tom")) },
            hentetTidspunkt = Instant.now(),
        )
    }

    private fun JsonNode.dato(felt: String) = LocalDate.parse(path(felt).asString())

    private companion object {
        val objectMapper = jacksonObjectMapper()
    }
}
