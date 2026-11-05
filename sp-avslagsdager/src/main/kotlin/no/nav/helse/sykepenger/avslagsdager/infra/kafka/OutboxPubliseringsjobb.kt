package no.nav.helse.sykepenger.avslagsdager.infra.kafka

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.OutgoingMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykepenger.avslagsdager.application.HandlingType
import no.nav.helse.sykepenger.avslagsdager.application.OutboxMelding
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.leaderelection.LeaderElection
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.TransaksjonProvider
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Publiserer outbox-meldingene på rapiden, én om gangen i rekkefølgen de ble lagt til. En melding markeres som
 * publisert i samme transaksjon som den ble hentet med `for update`, så den sendes ikke igjen etter at Kafka har
 * bekreftet den. Feiler publiseringen, prøver jobben igjen ved neste poll, og senere meldinger venter.
 */
internal class OutboxPubliseringsjobb(
    private val rapidsConnection: RapidsConnection,
    private val transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
    private val leaderElection: LeaderElection,
    private val pollIntervall: Duration = 0.5.seconds,
) : RapidsConnection.StatusListener {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    override fun onStartup(rapidsConnection: RapidsConnection) {
        loggInfo("Starter outbox-publiseringsjobb")
        job =
            scope.launch {
                while (isActive) {
                    kjørEnRundeHvisLeader()
                    delay(pollIntervall)
                }
            }
    }

    override fun onShutdownSignal(rapidsConnection: RapidsConnection) {
        loggInfo("Stopper outbox-publiseringsjobb")
        runBlocking {
            job?.cancelAndJoin()
        }
    }

    internal fun kjørEnRundeHvisLeader() {
        if (leaderElection.isLeader()) kjørEnRunde()
    }

    internal fun kjørEnRunde() {
        try {
            var harMelding: Boolean
            do {
                harMelding =
                    transaksjonProvider.transaksjon { kontekst ->
                        val konvolutt = kontekst.outbox.hentUpubliserte(maksAntall = 1).firstOrNull() ?: return@transaksjon false
                        val json = konvolutt.melding.tilJsonMessage(konvolutt.identitetsnummer)
                        val (_, feiledeMeldinger) =
                            rapidsConnection.publish(
                                listOf(OutgoingMessage(body = json.toJson(), key = konvolutt.identitetsnummer.value)),
                            )
                        feiledeMeldinger.firstOrNull()?.apply { throw error }
                        kontekst.outbox.markerSomSendt(konvolutt.id)
                        true
                    }
            } while (harMelding)
        } catch (e: Exception) {
            loggError("Feil under publisering av outbox-meldinger. Prøver igjen ved neste poll", e)
        }
    }
}

private fun OutboxMelding.tilJsonMessage(identitetsnummer: Identitetsnummer) =
    when (this) {
        is OutboxMelding.Saksbehandlerhandling ->
            JsonMessage.newMessage(
                eventName = "saksbehandlerhandling",
                map =
                    mapOf(
                        "fødselsnummer" to identitetsnummer.value,
                        "saksbehandlerIdent" to saksbehandlerIdent,
                        "handlingId" to handlingId,
                        "handlingType" to handlingType.tilMelding(),
                        "kreverTotrinnsvurdering" to kreverTotrinnsvurdering,
                        "tidspunkt" to tidspunkt,
                        "behandlingId" to behandlingId,
                    ),
            )

        is OutboxMelding.AvslagsdagerEndret ->
            JsonMessage.newMessage(
                eventName = "avslagsdager_endret",
                map =
                    mapOf(
                        "fødselsnummer" to identitetsnummer.value,
                        "vurderingId" to vurderingId,
                        "perioder" to perioder.map { mapOf("fom" to it.fom, "tom" to it.tom) },
                        "manuellVurdering" to manuellVurdering,
                    ),
            )
    }

private fun HandlingType.tilMelding() =
    when (this) {
        HandlingType.AVSLAGSDAGER_ENDRET -> "AVSLAGSDAGER_ENDRET"
    }
