package no.nav.helse.sykepenger.avslagsdager.bootstrap

import com.github.navikt.tbd_libs.access_token.TexasClient
import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.Config
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoIdProvider
import com.github.navikt.tbd_libs.personpseudoid.ValkeyPersonPseudoIdProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.client.PopulasjonstilgangConfig
import com.github.navikt.tbd_libs.populasjonstilgang.client.tilgangsmaskinenClient
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.infra.db.PostgresTransaksjonProvider
import no.nav.helse.sykepenger.avslagsdager.rammeverk.openapi.OpenApiConfig
import no.nav.helse.sykepenger.avslagsdager.rammeverk.openapi.configureOpenApiPlugin
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureCallId
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureCallLogging
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureStatusPages
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.RestRuting
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.configureRestRuting
import no.nav.sykepenger.libs.ktor.auth.AzureAdConfig
import no.nav.sykepenger.libs.ktor.auth.TilgangsgrupperTilTilganger
import no.nav.sykepenger.libs.ktor.auth.configureJwtAuthentication
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.navngittLogger
import no.nav.sykepenger.libs.postgres.DatabaseConfig
import no.nav.sykepenger.libs.postgres.dataSource
import no.nav.sykepenger.libs.postgres.migrerSynkront

private const val APP_NAVN = "sp-avslagsdager"

private val logger = navngittLogger("no.nav.helse.sykepenger.avslagsdager.bootstrap.App")

internal data class AppKonfigurasjon(
    val appNavn: String,
    val azureAd: AzureAdConfig,
    val database: DatabaseConfig,
    val tilganger: TilgangsgrupperTilTilganger,
    val openApi: OpenApiConfig,
) {
    companion object {
        fun fraEnv(
            appNavn: String,
            env: Map<String, String>,
        ) = AppKonfigurasjon(
            appNavn = appNavn,
            azureAd = AzureAdConfig.fraEnv(env),
            database = DatabaseConfig.fraEnv(env),
            tilganger = TilgangsgrupperTilTilganger.fraEnv(env),
            openApi = OpenApiConfig.fraEnv(appNavn, env),
        )
    }
}

fun main() {
    val env = System.getenv()
    launchApplication(
        env = env,
        kafkaConfig = AivenConfig.default,
        personPseudoIdProvider = ValkeyPersonPseudoIdProvider.fraEnv("personpseudoid", env),
        tilgangskontroll = PopulasjonstilgangConfig.fraEnv(env).tilgangsmaskinenClient(TexasClient.fromEnv()),
    )
}

internal fun launchApplication(
    env: Map<String, String>,
    kafkaConfig: Config,
    personPseudoIdProvider: PersonPseudoIdProvider,
    tilgangskontroll: PopulasjonstilgangskontrollProvider,
) {
    val konfigurasjon = AppKonfigurasjon.fraEnv(APP_NAVN, env)
    logger.info("Migrerer database")
    migrerSynkront(konfigurasjon.database)
    logger.info("Migrering ferdig")
    val dataSource = konfigurasjon.database.dataSource()
    val restAdapter =
        RestAdapter(
            personPseudoIdProvider = personPseudoIdProvider,
            populasjonstilgangskontrollProvider = tilgangskontroll,
            transaksjonProvider = PostgresTransaksjonProvider(dataSource),
        )

    RapidApplication
        .create(
            env = env,
            consumerProducerFactory = ConsumerProducerFactory(kafkaConfig),
            builder = {
                env["HTTP_PORT"]?.toInt()?.let(::withHttpPort)
                withKtorModule {
                    ktorApp(konfigurasjon, restAdapter)
                    monitor.subscribe(ApplicationStopped) {
                        loggInfo("Lukker datasource")
                        dataSource.close()
                    }
                }
            },
        ).start()
}

private fun Application.ktorApp(
    konfigurasjon: AppKonfigurasjon,
    restAdapter: RestAdapter<Transaksjonskontekst>,
) {
    configureCallId()
    configureCallLogging()
    configureContentNegotiation()
    configureStatusPages()
    configureResources()
    configureJwtAuthentication(
        azureAdConfig = konfigurasjon.azureAd,
        tilgangsgrupperTilTilganger = konfigurasjon.tilganger,
    )
    configureOpenApiPlugin(konfigurasjon.openApi)
    configureRestRuting(restAdapter, endepunkter())
    monitor.subscribe(ApplicationStarted) {
        loggInfo("Ktor-applikasjon startet", "appNavn" to konfigurasjon.appNavn)
    }
}

// Appens HTTP-endepunkter. Tom til appen får et domene.
internal fun endepunkter(): RestRuting<Transaksjonskontekst>.() -> Unit = {}
