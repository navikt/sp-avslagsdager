package no.nav.helse.sykepenger.avslagsdager.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.helse.sykepenger.avslagsdager.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.avslagsdager.bootstrap.FakeTilgangskontroll
import no.nav.helse.sykepenger.avslagsdager.bootstrap.endepunkter
import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdagerbehov
import no.nav.helse.sykepenger.avslagsdager.domain.Dagtype
import no.nav.helse.sykepenger.avslagsdager.domain.GrunnlagForLøpendeVilkår
import no.nav.helse.sykepenger.avslagsdager.domain.MeldingsreferanseId
import no.nav.helse.sykepenger.avslagsdager.domain.OppgittUtlandsopphold
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingResultat
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.SvarPåOppholdskrav
import no.nav.helse.sykepenger.avslagsdager.domain.Sykdomsdag
import no.nav.helse.sykepenger.avslagsdager.domain.SøknadId
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd
import no.nav.helse.sykepenger.avslagsdager.domain.Yrkesaktivitet
import no.nav.helse.sykepenger.avslagsdager.domain.YrkesaktivitetIBehov
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureStatusPages
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.configureRestRuting
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.get
import no.nav.helse.sykepenger.avslagsdager.rammeverk.testfixtures.InMemoryPersonPseudoIdProvider
import no.nav.helse.sykepenger.avslagsdager.rammeverk.testfixtures.TokenUtsteder
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.sykepenger.libs.ktor.auth.AccessToken
import no.nav.sykepenger.libs.ktor.auth.AzureAdConfig
import no.nav.sykepenger.libs.ktor.auth.NavIdent
import no.nav.sykepenger.libs.ktor.auth.Saksbehandler
import no.nav.sykepenger.libs.ktor.auth.SaksbehandlerOid
import no.nav.sykepenger.libs.ktor.auth.SaksbehandlerPrincipal
import no.nav.sykepenger.libs.ktor.auth.Tilgang
import no.nav.sykepenger.libs.ktor.auth.TilgangsgrupperTilTilganger
import no.nav.sykepenger.libs.ktor.auth.configureJwtAuthentication
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class GetGrunnlagForLøpendeVilkårBehandlerTest {
    private val saksbehandler = Saksbehandler(NavIdent("Z999999"), SaksbehandlerOid("oid"), "Test Testesen")
    private val identitetsnummer = Identitetsnummer(lagIdentitetsnummer())
    private val annenPerson = Identitetsnummer(lagIdentitetsnummer())
    private val transaksjonProvider = InMemoryTransaksjonProvider()
    private val mockOAuth2Server = MockOAuth2Server()

    @BeforeEach
    fun start() = mockOAuth2Server.start()

    @AfterEach
    fun stop() = mockOAuth2Server.shutdown()

    @Test
    fun `grunnlaget viser perioden, dagene med oppgitt opphold, dagene til vurdering og de vurderte dagene`() =
        testApplication {
            // Given
            val søknadA1 = MeldingsreferanseId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            val søknadSelvstendig = MeldingsreferanseId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
            val a1 = Yrkesaktivitet("ARBEIDSTAKER", "987654321")
            val selvstendig = Yrkesaktivitet("SELVSTENDIG", null)
            val avslag = lagVurdering(dager = listOf(3.jan))
            val grunnlag =
                lagreGrunnlag(
                    yrkesaktiviteter =
                        listOf(
                            lagYrkesaktivitetIBehov(a1, "SYKEDAG", listOf(søknadA1)),
                            lagYrkesaktivitetIBehov(selvstendig, "SYKEDAG", listOf(søknadSelvstendig)),
                        ),
                    opphold =
                        listOf(
                            lagOpphold(søknadA1, a1, Periode(3.jan, 5.jan)),
                            lagOpphold(søknadSelvstendig, selvstendig, Periode(5.jan, 6.jan)),
                        ),
                    vurderinger = listOf(avslag),
                )
            val pseudoId = settOpp()

            // When
            val response = client.get(url(pseudoId, grunnlag.id.toString()))

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val a1Kilde = """{ "søknadMeldingsreferanseId": "$søknadA1", "yrkesaktivitetstype": "ARBEIDSTAKER", "organisasjonsnummer": "987654321" }"""
            val selvstendigKilde = """{ "søknadMeldingsreferanseId": "$søknadSelvstendig", "yrkesaktivitetstype": "SELVSTENDIG", "organisasjonsnummer": null }"""
            assertJson(
                """
                {
                  "grunnlagForLøpendeVilkårId": "${grunnlag.id}",
                  "periode": { "fom": "2026-01-01", "tom": "2026-01-10" },
                  "dagerMedOppgittUtlandsopphold": [
                    { "dato": "2026-01-03", "kilder": [$a1Kilde] },
                    { "dato": "2026-01-04", "kilder": [$a1Kilde] },
                    { "dato": "2026-01-05", "kilder": [$a1Kilde, $selvstendigKilde] },
                    { "dato": "2026-01-06", "kilder": [$selvstendigKilde] }
                  ],
                  "dagerTilVurdering": [
                    { "dato": "2026-01-04", "kilder": [$a1Kilde] },
                    { "dato": "2026-01-05", "kilder": [$a1Kilde, $selvstendigKilde] },
                    { "dato": "2026-01-06", "kilder": [$selvstendigKilde] }
                  ],
                  "vurderteDager": [
                    { "dato": "2026-01-03", "vilkår": "OPPHOLDSKRAV", "vurderingId": "${avslag.id}", "utfall": "IkkeOppfylt" }
                  ]
                }
                """,
                response.bodyAsText(),
            )
        }

    // Spesialist (on-behalf-of) og Speil kaller med saksbehandlertoken med appen som audience. Hvilke apper som får
    // token, styres av inbound i Nais-manifestet.
    @Test
    fun `godtar saksbehandlertoken med appen som audience og Les-tilgang`() =
        testApplication {
            // Given
            val grunnlag = lagreGrunnlag()
            val pseudoId = settOppMedAzureAd()
            val token =
                TokenUtsteder(mockOAuth2Server, audience = "sp-avslagsdager")
                    .utstedSaksbehandlerToken(entraGrupper = setOf(LES_GRUPPE))

            // When
            val response = client.get(url(pseudoId, grunnlag.id.toString())) { bearerAuth(token) }

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `kall uten token gir 401`() =
        testApplication {
            // Given
            val grunnlag = lagreGrunnlag()
            val pseudoId = settOppMedAzureAd()

            // When
            val response = client.get(url(pseudoId, grunnlag.id.toString()))

            // Then
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `kall når vurdering av oppholdskravet ikke er skrudd på gir 500`() =
        testApplication {
            // Given
            val grunnlag = lagreGrunnlag()
            val pseudoId = settOpp(aktivert = false)

            // When
            val response = client.get(url(pseudoId, grunnlag.id.toString()))

            // Then
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }

    @Test
    fun `ukjent grunnlagForLøpendeVilkårId gir 404`() =
        testApplication {
            // Given
            lagreGrunnlag()
            val pseudoId = settOpp()

            // When
            val response = client.get(url(pseudoId, UUID.randomUUID().toString()))

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `grunnlagForLøpendeVilkårId som ikke er en UUID gir 404`() =
        testApplication {
            // Given
            val pseudoId = settOpp()

            // When
            val response = client.get(url(pseudoId, "ikke-en-uuid"))

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `grunnlag som gjelder en annen person gir 404`() =
        testApplication {
            // Given
            val grunnlag = lagreGrunnlag(fødselsnummer = annenPerson)
            val pseudoId = settOpp()

            // When
            val response = client.get(url(pseudoId, grunnlag.id.toString()))

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `ukjent person gir 404`() =
        testApplication {
            // Given
            val grunnlag = lagreGrunnlag()
            settOpp()

            // When
            val response = client.get(url(UUID.randomUUID().toString(), grunnlag.id.toString()))

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `manglende tilgang til personen gir 403`() =
        testApplication {
            // Given
            val grunnlag = lagreGrunnlag()
            val pseudoId =
                settOpp(tilgangskontroll = FakeTilgangskontroll(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet)))

            // When
            val response = client.get(url(pseudoId, grunnlag.id.toString()))

            // Then
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `kall uten Les-tilgang gir 403`() =
        testApplication {
            // Given
            val grunnlag = lagreGrunnlag()
            val pseudoId = settOpp(tilganger = emptySet())

            // When
            val response = client.get(url(pseudoId, grunnlag.id.toString()))

            // Then
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    private fun lagreGrunnlag(
        fødselsnummer: Identitetsnummer = identitetsnummer,
        yrkesaktiviteter: List<YrkesaktivitetIBehov> = listOf(lagYrkesaktivitetIBehov()),
        opphold: List<OppgittUtlandsopphold> = emptyList(),
        vurderinger: List<Oppholdskravvurdering> = emptyList(),
    ): GrunnlagForLøpendeVilkår {
        val behov = Avslagsdagerbehov(fødselsnummer.value, PERIODE, yrkesaktiviteter)
        val grunnlag = GrunnlagForLøpendeVilkår.forBehov(behov, opphold, vurderinger, siste = null)
        transaksjonProvider.grunnlagForLøpendeVilkår.lagre(grunnlag)
        return grunnlag
    }

    private fun lagYrkesaktivitetIBehov(
        yrkesaktivitet: Yrkesaktivitet = Yrkesaktivitet("ARBEIDSTAKER", "987654321"),
        dagtype: String = "SYKEDAG",
        søknader: List<MeldingsreferanseId> = emptyList(),
    ) = YrkesaktivitetIBehov(yrkesaktivitet, PERIODE.dager().map { Sykdomsdag(it, Dagtype(dagtype)) }, søknader)

    private fun lagOpphold(
        søknad: MeldingsreferanseId,
        yrkesaktivitet: Yrkesaktivitet,
        periode: Periode,
    ) = OppgittUtlandsopphold(
        meldingsreferanseId = søknad,
        søknadId = SøknadId(UUID.randomUUID()),
        korrigerer = null,
        fødselsnummer = identitetsnummer.value,
        yrkesaktivitet = yrkesaktivitet,
        utlandsoppholdsperioder = listOf(periode),
        hentetTidspunkt = Instant.parse("2026-02-01T12:00:00Z"),
    )

    private fun lagVurdering(dager: List<LocalDate>): Oppholdskravvurdering {
        val resultat =
            Oppholdskravvurdering.vurder(
                fødselsnummer = identitetsnummer.value,
                behandlingId = UUID.randomUUID(),
                dager = dager,
                svar = SvarPåOppholdskrav(eøsBorger = true, oppholdIGodkjentOmråde = false, unntakAndreLedd = UnntakAndreLedd.INGEN, godkjentOppholdInntilFireUker = false),
                fritekstbegrunnelse = "Oppholdt seg i Thailand",
                journalpostId = emptyList(),
                saksbehandlerIdent = "Z999999",
            )
        return (resultat as OppholdskravvurderingResultat.Vurdert).vurdering
    }

    private fun ApplicationTestBuilder.settOpp(
        tilganger: Set<Tilgang> = setOf(Tilgang.Les),
        tilgangskontroll: PopulasjonstilgangskontrollProvider = FakeTilgangskontroll(),
        aktivert: Boolean = true,
    ): String {
        val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
        val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)
        pseudoIdProvider.nyPersonPseudoId(annenPerson)
        application {
            configureContentNegotiation()
            configureResources()
            configureStatusPages()
            val principal = SaksbehandlerPrincipal(saksbehandler, tilganger, AccessToken("token"))
            intercept(ApplicationCallPipeline.Plugins) {
                call.authentication.principal(principal)
            }
            val restAdapter =
                RestAdapter(
                    personPseudoIdProvider = pseudoIdProvider,
                    populasjonstilgangskontrollProvider = tilgangskontroll,
                    transaksjonProvider = transaksjonProvider,
                )
            routing {
                get(GetGrunnlagForLøpendeVilkårBehandler(aktivert), restAdapter)
            }
        }
        return pseudoId.toString()
    }

    // Kobler opp endepunktene slik appen gjør, med ekte validering av Azure AD-token
    private fun ApplicationTestBuilder.settOppMedAzureAd(): String {
        val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
        val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)
        application {
            configureContentNegotiation()
            configureResources()
            configureStatusPages()
            configureJwtAuthentication(
                azureAdConfig =
                    AzureAdConfig(
                        clientId = "sp-avslagsdager",
                        issuerUrl = mockOAuth2Server.issuerUrl("azuread").toString(),
                        jwkProviderUri = mockOAuth2Server.jwksUrl("azuread").toString(),
                    ),
                tilgangsgrupperTilTilganger = TilgangsgrupperTilTilganger(tilgangLesGruppeIder = setOf(LES_GRUPPE), tilgangSkrivGruppeIder = emptySet()),
            )
            configureRestRuting(
                RestAdapter(
                    personPseudoIdProvider = pseudoIdProvider,
                    populasjonstilgangskontrollProvider = FakeTilgangskontroll(),
                    transaksjonProvider = transaksjonProvider,
                ),
                endepunkter(oppholdskravvurderingAktivert = true),
            )
        }
        return pseudoId.toString()
    }

    private companion object {
        val Int.jan: LocalDate get() = LocalDate.of(2026, 1, this)
        val PERIODE = Periode(1.jan, 10.jan)
        const val LES_GRUPPE = "les-gruppe"
    }
}

private fun url(
    personId: String,
    grunnlagForLøpendeVilkårId: String,
) = "/api/personer/$personId/avslagsdager/$grunnlagForLøpendeVilkårId"

private fun assertJson(
    @Language("JSON") forventet: String,
    faktisk: String,
) {
    val mapper = jacksonObjectMapper()
    assertEquals(mapper.readTree(forventet), mapper.readTree(faktisk))
}
