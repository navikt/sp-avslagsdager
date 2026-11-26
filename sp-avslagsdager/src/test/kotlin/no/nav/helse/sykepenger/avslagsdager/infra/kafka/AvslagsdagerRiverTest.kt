package no.nav.helse.sykepenger.avslagsdager.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingResultat
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.SvarPåOppholdskrav
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd
import no.nav.helse.sykepenger.avslagsdager.infra.db.Database
import no.nav.helse.sykepenger.avslagsdager.infra.db.DatabaseTest
import no.nav.helse.sykepenger.avslagsdager.infra.spedisjon.FakeSpedisjon
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

internal class AvslagsdagerRiverTest : DatabaseTest() {
    private val spedisjon = FakeSpedisjon()
    private val rapid = TestRapid().apply { AvslagsdagerRiver(this, Database.transaksjonProvider, spedisjon.søknadsoppslag) }

    @Test
    fun `en person uten vurderinger i perioden får tomme lister`() {
        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan), FØDSELSNUMMER)

        // Then
        assertEquals(1, rapid.inspektør.size)
        assertEquals(FØDSELSNUMMER, rapid.inspektør.key(0))
        assertEquals(Løsning(emptyList()), rapid.inspektør.message(0).løsning())
    }

    @Test
    fun `svarer med hver dag i perioden i en vurdering som ikke er oppfylt`() {
        // Given
        val vurdering = lagre(ikkeOppfylt, dager = listOf(5.jan, 6.jan))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(
            Løsning(
                vurderteDager =
                    listOf(
                        VurdertDagJson(vurdering.id.value, "2026-01-05", IKKE_OPPFYLT, VILKÅRSKODER_IKKE_OPPFYLT),
                        VurdertDagJson(vurdering.id.value, "2026-01-06", IKKE_OPPFYLT, VILKÅRSKODER_IKKE_OPPFYLT),
                    ),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `løsningen har grunnlagForLøpendeVilkårId, avslagsdager, varsler og vurderteDager`() {
        // Given
        lagre(ikkeOppfylt, dager = listOf(5.jan))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(
            listOf("grunnlagForLøpendeVilkårId", "avslagsdager", "varsler", "vurderteDager"),
            rapid.inspektør
                .message(0)
                .path("@løsning")
                .path("Avslagsdager")
                .propertyNames()
                .toList(),
        )
    }

    @Test
    fun `dager utenfor perioden er ikke med`() {
        // Given
        val vurdering = lagre(ikkeOppfylt, dager = listOf(4.jan, 5.jan, 6.jan, 7.jan))
        lagre(ikkeOppfylt, dager = listOf(1.feb))

        // When
        rapid.sendTestMessage(behov(fom = 5.jan, tom = 6.jan))

        // Then
        assertEquals(
            Løsning(
                vurderteDager =
                    listOf(
                        VurdertDagJson(vurdering.id.value, "2026-01-05", IKKE_OPPFYLT, VILKÅRSKODER_IKKE_OPPFYLT),
                        VurdertDagJson(vurdering.id.value, "2026-01-06", IKKE_OPPFYLT, VILKÅRSKODER_IKKE_OPPFYLT),
                    ),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `flere vurderinger gir dagene sortert på dato`() {
        // Given
        val senere = lagre(ikkeOppfylt, dager = listOf(10.jan))
        val tidligere = lagre(ikkeOppfyltIkkeEøsBorger, dager = listOf(2.jan, 1.jan))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(
            Løsning(
                vurderteDager =
                    listOf(
                        VurdertDagJson(tidligere.id.value, "2026-01-01", IKKE_OPPFYLT, VILKÅRSKODER_IKKE_OPPFYLT_IKKE_EØS_BORGER),
                        VurdertDagJson(tidligere.id.value, "2026-01-02", IKKE_OPPFYLT, VILKÅRSKODER_IKKE_OPPFYLT_IKKE_EØS_BORGER),
                        VurdertDagJson(senere.id.value, "2026-01-10", IKKE_OPPFYLT, VILKÅRSKODER_IKKE_OPPFYLT),
                    ),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `en oppfylt vurdering gir dagene med utfallet oppfylt`() {
        // Given
        val vurdering = lagre(oppfylt, dager = listOf(5.jan))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(
            Løsning(
                vurderteDager = listOf(VurdertDagJson(vurdering.id.value, "2026-01-05", OPPFYLT, VILKÅRSKODER_OPPFYLT)),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `en oppfylt omgjøring gjelder for dagene den omfatter`() {
        // Given
        val avslag = lagre(ikkeOppfylt, dager = listOf(5.jan, 6.jan), vurdertTidspunkt = kl(12))
        val omgjøring = lagre(oppfylt, dager = listOf(6.jan), vurdertTidspunkt = kl(13))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(
            Løsning(
                vurderteDager =
                    listOf(
                        VurdertDagJson(avslag.id.value, "2026-01-05", IKKE_OPPFYLT, VILKÅRSKODER_IKKE_OPPFYLT),
                        VurdertDagJson(omgjøring.id.value, "2026-01-06", OPPFYLT, VILKÅRSKODER_OPPFYLT),
                    ),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `en vurdering som er omgjort for alle dagene sine, gjelder ikke lenger`() {
        // Given
        lagre(ikkeOppfylt, dager = listOf(5.jan), vurdertTidspunkt = kl(12))
        val omgjøring = lagre(ikkeOppfyltIkkeEøsBorger, dager = listOf(5.jan), vurdertTidspunkt = kl(13))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(
            Løsning(
                vurderteDager =
                    listOf(VurdertDagJson(omgjøring.id.value, "2026-01-05", IKKE_OPPFYLT, VILKÅRSKODER_IKKE_OPPFYLT_IKKE_EØS_BORGER)),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `den nyeste vurderingen gjelder uansett rekkefølgen de er lagret i`() {
        // Given
        val nyest = lagre(oppfylt, dager = listOf(5.jan), vurdertTidspunkt = kl(13))
        lagre(ikkeOppfylt, dager = listOf(5.jan), vurdertTidspunkt = kl(12))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(
            Løsning(
                vurderteDager = listOf(VurdertDagJson(nyest.id.value, "2026-01-05", OPPFYLT, VILKÅRSKODER_OPPFYLT)),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `vurderinger for en annen person er ikke med`() {
        // Given
        lagre(ikkeOppfylt, dager = listOf(5.jan), fødselsnummer = lagIdentitetsnummer())

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(Løsning(emptyList()), rapid.inspektør.message(0).løsning())
    }

    @Test
    fun `opphold og ferie hos a1 gir varsel hos a2 og a3 som er syke, og grunnlaget lagres`() {
        // Given
        val søknadA1 = spedisjon.sendtSøknad(FØDSELSNUMMER, utlandsopphold = listOf(Periode(5.jan, 6.jan)), ferie = listOf(Periode(5.jan, 6.jan)))
        val søknadA2 = spedisjon.sendtSøknad(FØDSELSNUMMER)
        val søknadA3 = spedisjon.sendtSøknad(FØDSELSNUMMER)

        // When
        rapid.sendTestMessage(
            behov(
                yrkesaktivitet(A1, sykedager(1.jan, 4.jan) + dager(5.jan, 6.jan, "FERIEDAG"), søknadA1),
                yrkesaktivitet(A2, sykedager(1.jan, 31.jan), søknadA2),
                yrkesaktivitet(A3, sykedager(1.jan, 31.jan), søknadA3),
            ),
        )

        // Then
        val løsning =
            rapid.inspektør
                .message(0)
                .path("@løsning")
                .path("Avslagsdager")
        assertEquals(listOf(A2, A3), løsning.varsler())
        assertEquals(emptyList(), løsning.avslagsdager())
        val grunnlag = checkNotNull(transaksjon { it.grunnlagForLøpendeVilkår.finnSiste(FØDSELSNUMMER, Periode(1.jan, 31.jan)) })
        assertEquals(UUID.fromString(løsning.path("grunnlagForLøpendeVilkårId").asString()), grunnlag.id.value)
        assertEquals(listOf(5.jan, 6.jan), grunnlag.innhold.dagerMedOppgittUtlandsopphold.map { it.dato })
        assertEquals(listOf(5.jan, 6.jan), grunnlag.innhold.dagerTilVurdering.map { it.dato })
    }

    @Test
    fun `en dag som allerede er vurdert, gir ikke varsel, men avslagsdag`() {
        // Given
        lagre(ikkeOppfylt, dager = listOf(5.jan, 6.jan))
        val søknad = spedisjon.sendtSøknad(FØDSELSNUMMER, utlandsopphold = listOf(Periode(5.jan, 6.jan)))

        // When
        rapid.sendTestMessage(behov(yrkesaktivitet(A1, sykedager(1.jan, 31.jan), søknad)))

        // Then
        val løsning =
            rapid.inspektør
                .message(0)
                .path("@løsning")
                .path("Avslagsdager")
        assertEquals(emptyList(), løsning.varsler())
        assertEquals(
            listOf(AvslagsdagJson("2026-01-05", VILKÅRSKODER_IKKE_OPPFYLT), AvslagsdagJson("2026-01-06", VILKÅRSKODER_IKKE_OPPFYLT)),
            løsning.avslagsdager(),
        )
    }

    @Test
    fun `en søknad som allerede er slått opp, hentes ikke på nytt`() {
        // Given
        val søknad = spedisjon.sendtSøknad(FØDSELSNUMMER, utlandsopphold = listOf(Periode(5.jan, 6.jan)))
        rapid.sendTestMessage(behov(yrkesaktivitet(A1, sykedager(1.jan, 31.jan), søknad)))

        // When
        rapid.sendTestMessage(behov(yrkesaktivitet(A1, sykedager(1.jan, 31.jan), søknad)))

        // Then
        assertEquals(listOf(søknad.meldingsreferanseId), spedisjon.oppslag)
        assertEquals(
            listOf(A1),
            rapid.inspektør
                .message(1)
                .path("@løsning")
                .path("Avslagsdager")
                .varsler(),
        )
    }

    @Test
    fun `når Spedisjon ikke svarer, får behovet ikke svar`() {
        // Given
        val søknad = spedisjon.sendtSøknad(FØDSELSNUMMER, utlandsopphold = listOf(Periode(5.jan, 6.jan)))
        spedisjon.feiler()

        // When
        rapid.sendTestMessage(behov(yrkesaktivitet(A1, sykedager(1.jan, 31.jan), søknad)))

        // Then
        assertEquals(0, rapid.inspektør.size)
        assertNull(transaksjon { it.grunnlagForLøpendeVilkår.finnSiste(FØDSELSNUMMER, Periode(1.jan, 31.jan)) })
    }

    @Test
    fun `en korrigerende søknad uten opphold fjerner varselet`() {
        // Given
        val original = spedisjon.sendtSøknad(FØDSELSNUMMER, utlandsopphold = listOf(Periode(5.jan, 6.jan)))
        val korrigering = spedisjon.sendtSøknad(FØDSELSNUMMER, korrigerer = original)

        // When
        rapid.sendTestMessage(behov(yrkesaktivitet(A1, sykedager(1.jan, 31.jan), original, korrigering)))

        // Then
        assertEquals(
            emptyList(),
            rapid.inspektør
                .message(0)
                .path("@løsning")
                .path("Avslagsdager")
                .varsler(),
        )
    }

    @Test
    fun `samme behov to ganger gir samme grunnlagForLøpendeVilkårId`() {
        // Given
        val søknad = spedisjon.sendtSøknad(FØDSELSNUMMER, utlandsopphold = listOf(Periode(5.jan, 6.jan)))
        val behov = { behov(yrkesaktivitet(A1, sykedager(1.jan, 31.jan), søknad)) }

        // When
        rapid.sendTestMessage(behov())
        rapid.sendTestMessage(behov())

        // Then
        assertEquals(grunnlagForLøpendeVilkårId(0), grunnlagForLøpendeVilkårId(1))
    }

    @Test
    fun `en ny vurdering mellom to behov gir ny grunnlagForLøpendeVilkårId`() {
        // Given
        val søknad = spedisjon.sendtSøknad(FØDSELSNUMMER, utlandsopphold = listOf(Periode(5.jan, 6.jan)))
        val behov = { behov(yrkesaktivitet(A1, sykedager(1.jan, 31.jan), søknad)) }
        rapid.sendTestMessage(behov())

        // When
        lagre(ikkeOppfylt, dager = listOf(5.jan))
        rapid.sendTestMessage(behov())

        // Then
        assertNotEquals(grunnlagForLøpendeVilkårId(0), grunnlagForLøpendeVilkårId(1))
    }

    @Test
    fun `behov som allerede har en løsning, ignoreres`() {
        // Given
        val medLøsning =
            behov(fom = 1.jan, tom = 31.jan).replace(
                "\"@behov\"",
                "\"@løsning\": { \"Avslagsdager\": { \"vurderteDager\": [] } }, \"@behov\"",
            )

        // When
        rapid.sendTestMessage(medLøsning)

        // Then
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `andre behov ignoreres`() {
        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan).replace("[\"Avslagsdager\"]", "[\"EtHeltAnnetBehov\"]"))

        // Then
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `behov uten gyldig periode ignoreres`() {
        listOf(
            """{ "tom": "2026-01-31" }""",
            """{ "fom": "2026-01-01" }""",
            """{ "fom": "ikke en dato", "tom": "2026-01-31" }""",
            """{ "fom": "2026-01-31", "tom": "2026-01-01" }""",
            """{ "vurderingIder": ["${UUID.randomUUID()}"] }""",
        ).forEach { periode ->
            // When
            rapid.sendTestMessage(behov(periode))

            // Then
            assertEquals(0, rapid.inspektør.size, periode)
        }
    }

    private fun lagre(
        svar: SvarPåOppholdskrav,
        dager: List<LocalDate>,
        fødselsnummer: String = FØDSELSNUMMER,
        vurdertTidspunkt: Instant = kl(12),
    ): Oppholdskravvurdering {
        val resultat =
            Oppholdskravvurdering.vurder(
                fødselsnummer = fødselsnummer,
                behandlingId = UUID.randomUUID(),
                dager = dager,
                svar = svar,
                fritekstbegrunnelse = "Begrunnelse",
                journalpostId = emptyList(),
                saksbehandlerIdent = "A123456",
                vurdertTidspunkt = vurdertTidspunkt,
            )
        val vurdering = (resultat as OppholdskravvurderingResultat.Vurdert).vurdering
        transaksjon { it.oppholdskravvurderinger.lagre(vurdering) }
        return vurdering
    }

    private fun grunnlagForLøpendeVilkårId(indeks: Int) =
        rapid.inspektør
            .message(indeks)
            .path("@løsning")
            .path("Avslagsdager")
            .path("grunnlagForLøpendeVilkårId")
            .asString()

    private fun JsonNode.varsler() =
        path("varsler").values().map { varsel ->
            assertEquals("RV_SØ_8", varsel.path("varselkode").asString())
            assertEquals("ARBEIDSTAKER", varsel.path("yrkesaktivitetstype").asString())
            varsel.path("organisasjonsnummer").asString()
        }

    private data class AvslagsdagJson(
        val dato: String,
        val vilkårskoder: List<String>,
        val vilkår: String = "OPPHOLDSKRAV",
    )

    private fun JsonNode.avslagsdager() =
        path("avslagsdager").values().map { dag ->
            AvslagsdagJson(
                dato = dag.path("dato").asString(),
                vilkårskoder = dag.path("vilkårskoder").values().map { it.asString() },
                vilkår = dag.path("vilkår").asString(),
            )
        }

    private data class Løsning(
        val vurderteDager: List<VurdertDagJson>,
    )

    private data class VurdertDagJson(
        val vurderingId: UUID,
        val dato: String,
        val utfall: String,
        val vilkårskoder: List<String>,
        val vilkår: String = "OPPHOLDSKRAV",
    )

    private fun JsonNode.løsning(): Løsning {
        val løsning = path("@løsning").path("Avslagsdager")
        return Løsning(
            vurderteDager =
                løsning.path("vurderteDager").values().map { dag ->
                    VurdertDagJson(
                        vurderingId = UUID.fromString(dag.path("vurderingId").asString()),
                        dato = dag.path("dato").asString(),
                        utfall = dag.path("utfall").asString(),
                        vilkårskoder = dag.path("vilkårskoder").values().map { it.asString() },
                        vilkår = dag.path("vilkår").asString(),
                    )
                },
        )
    }

    private companion object {
        val FØDSELSNUMMER = lagIdentitetsnummer()
        const val OPPFYLT = "Oppfylt"
        const val IKKE_OPPFYLT = "IkkeOppfylt"

        val ikkeOppfylt =
            SvarPåOppholdskrav(
                eøsBorger = true,
                oppholdIGodkjentOmråde = false,
                unntakAndreLedd = UnntakAndreLedd.INGEN,
                godkjentOppholdInntilFireUker = false,
            )

        val oppfylt =
            SvarPåOppholdskrav(
                eøsBorger = true,
                oppholdIGodkjentOmråde = true,
                unntakAndreLedd = null,
                godkjentOppholdInntilFireUker = null,
            )

        val ikkeOppfyltIkkeEøsBorger = ikkeOppfylt.copy(eøsBorger = false)

        val VILKÅRSKODER_OPPFYLT = listOf("OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND")

        val VILKÅRSKODER_IKKE_OPPFYLT =
            listOf(
                "OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND",
                "UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8",
                "UNNTAK_INNLAGT_HELSEINSTITUSJON",
                "UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER",
            )

        val VILKÅRSKODER_IKKE_OPPFYLT_IKKE_EØS_BORGER =
            listOf(
                "OPPHOLD_I_NORGE_IKKE_EØS_BORGER",
                "UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8",
                "UNNTAK_INNLAGT_HELSEINSTITUSJON",
                "UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER",
            )

        val Int.jan: LocalDate get() = LocalDate.of(2026, 1, this)
        val Int.feb: LocalDate get() = LocalDate.of(2026, 2, this)

        const val A1 = "111111111"
        const val A2 = "222222222"
        const val A3 = "333333333"

        val objectMapper = jacksonObjectMapper()

        fun dager(
            fom: LocalDate,
            tom: LocalDate,
            dagtype: String,
        ) = Periode(fom, tom).dager().map { mapOf("dato" to it.toString(), "dagtype" to dagtype) }

        fun sykedager(
            fom: LocalDate,
            tom: LocalDate,
        ) = dager(fom, tom, "SYKEDAG")

        fun yrkesaktivitet(
            organisasjonsnummer: String,
            sykdomstidslinje: List<Map<String, String>>,
            vararg søknader: FakeSpedisjon.SendtSøknad,
        ) = mapOf(
            "yrkesaktivitetstype" to "ARBEIDSTAKER",
            "organisasjonsnummer" to organisasjonsnummer,
            "sykdomstidslinje" to sykdomstidslinje,
            "søknadMeldingsreferanseIder" to søknader.map { it.meldingsreferanseId.toString() },
        )

        fun behov(vararg yrkesaktiviteter: Map<String, Any>) =
            behov(
                objectMapper.writeValueAsString(
                    mapOf("fom" to 1.jan.toString(), "tom" to 31.jan.toString(), "yrkesaktiviteter" to yrkesaktiviteter.toList()),
                ),
            )

        fun kl(time: Int): Instant = Instant.parse("2026-01-10T%02d:00:00Z".format(time))

        fun behov(
            fom: LocalDate,
            tom: LocalDate,
        ) = behov("""{ "fom": "$fom", "tom": "$tom" }""")

        fun behov(behovsinnhold: String): String {
            @Language("JSON")
            val behov = """
            {
              "@event_name": "behov",
              "@id": "${UUID.randomUUID()}",
              "@behov": ["Avslagsdager"],
              "fødselsnummer": "$FØDSELSNUMMER",
              "Avslagsdager": $behovsinnhold
            }
            """
            return behov
        }
    }
}
