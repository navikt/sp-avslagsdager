package no.nav.helse.sykepenger.avslagsdager.domain

import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

internal class GrunnlagForLøpendeVilkårTest {
    @Test
    fun `opphold i søknaden på en sykedag gir dag til vurdering og varsel`() {
        // Given
        val opphold = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val behov = lagBehov(lagYrkesaktivitet(a1, sykedager(1.jan, 31.jan), opphold))

        // When
        val grunnlag = utled(behov, listOf(opphold))

        // Then
        assertEquals(
            listOf(DagMedKilder(5.jan, listOf(kilde(opphold))), DagMedKilder(6.jan, listOf(kilde(opphold)))),
            grunnlag.dagerTilVurdering,
        )
        assertEquals(listOf(Varsel(a1, Varselkode.RV_SØ_8)), grunnlag.varsler)
    }

    @Test
    fun `opphold og ferie hos a1 gir dager til vurdering og varsel hos a2 og a3 som er syke`() {
        // Given
        val oppholdA1 = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val oppholdA2 = lagOppgittUtlandsopphold(a2, utlandsoppholdsperioder = emptyList())
        val oppholdA3 = lagOppgittUtlandsopphold(a3, utlandsoppholdsperioder = emptyList())
        val behov =
            lagBehov(
                lagYrkesaktivitet(a1, sykedager(1.jan, 4.jan) + dager(5.jan, 6.jan, "FERIEDAG"), oppholdA1),
                lagYrkesaktivitet(a2, sykedager(1.jan, 31.jan), oppholdA2),
                lagYrkesaktivitet(a3, sykedager(1.jan, 31.jan), oppholdA3),
            )

        // When
        val grunnlag = utled(behov, listOf(oppholdA1, oppholdA2, oppholdA3))

        // Then
        assertEquals(listOf(5.jan, 6.jan), grunnlag.dagerTilVurdering.map { it.dato })
        assertEquals(listOf(kilde(oppholdA1)), grunnlag.dagerTilVurdering.first().kilder)
        assertEquals(listOf(Varsel(a2, Varselkode.RV_SØ_8), Varsel(a3, Varselkode.RV_SØ_8)), grunnlag.varsler)
    }

    @Test
    fun `ferie hos alle gir ingen dager til vurdering, men dagene har oppgitt utlandsopphold`() {
        // Given
        val oppholdA1 = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val behov =
            lagBehov(
                lagYrkesaktivitet(a1, dager(5.jan, 6.jan, "FERIEDAG"), oppholdA1),
                lagYrkesaktivitet(a2, dager(5.jan, 6.jan, "FERIEDAG")),
            )

        // When
        val grunnlag = utled(behov, listOf(oppholdA1))

        // Then
        assertEquals(emptyList(), grunnlag.dagerTilVurdering)
        assertEquals(emptyList(), grunnlag.varsler)
        assertEquals(
            listOf(DagMedKilder(5.jan, listOf(kilde(oppholdA1))), DagMedKilder(6.jan, listOf(kilde(oppholdA1)))),
            grunnlag.dagerMedOppgittUtlandsopphold,
        )
    }

    @Test
    fun `arbeidsgiverdager og foreldede sykedager må vurderes`() {
        // Given
        val opphold = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val behov =
            lagBehov(
                lagYrkesaktivitet(a1, dager(5.jan, 5.jan, "ARBEIDSGIVERDAG"), opphold),
                lagYrkesaktivitet(a2, dager(6.jan, 6.jan, "FORELDET_SYKEDAG")),
                lagYrkesaktivitet(a3, dager(5.jan, 6.jan, "ARBEIDSDAG")),
            )

        // When
        val grunnlag = utled(behov, listOf(opphold))

        // Then
        assertEquals(listOf(5.jan, 6.jan), grunnlag.dagerTilVurdering.map { it.dato })
        assertEquals(listOf(Varsel(a1, Varselkode.RV_SØ_8), Varsel(a2, Varselkode.RV_SØ_8)), grunnlag.varsler)
    }

    @Test
    fun `opphold utenfor beregningsperioden er ikke med`() {
        // Given
        val opphold = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(30.jan, 2.feb)))
        val behov = lagBehov(lagYrkesaktivitet(a1, sykedager(1.jan, 31.jan), opphold))

        // When
        val grunnlag = utled(behov, listOf(opphold))

        // Then
        assertEquals(listOf(30.jan, 31.jan), grunnlag.dagerMedOppgittUtlandsopphold.map { it.dato })
    }

    @Test
    fun `en dag med en gjeldende vurdering er ikke til vurdering, også når en ny søknad oppgir opphold`() {
        // Given
        val vurdering = lagVurdering(listOf(5.jan), oppfylt = true)
        val første = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val ny = lagOppgittUtlandsopphold(a2, utlandsoppholdsperioder = listOf(Periode(5.jan, 5.jan)))
        val behov =
            lagBehov(
                lagYrkesaktivitet(a1, sykedager(1.jan, 31.jan), første),
                lagYrkesaktivitet(a2, sykedager(1.jan, 31.jan), ny),
            )

        // When
        val grunnlag = utled(behov, listOf(første, ny), listOf(vurdering))

        // Then
        assertEquals(listOf(6.jan), grunnlag.dagerTilVurdering.map { it.dato })
        assertEquals(listOf(Varsel(a1, Varselkode.RV_SØ_8), Varsel(a2, Varselkode.RV_SØ_8)), grunnlag.varsler)
        assertEquals(
            setOf(kilde(første), kilde(ny)),
            grunnlag.dagerMedOppgittUtlandsopphold
                .first()
                .kilder
                .toSet(),
        )
    }

    @Test
    fun `en korrigerende søknad uten opphold fjerner oppholdene i søknaden den korrigerer`() {
        // Given
        val original = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val korrigering = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = emptyList(), korrigerer = original.søknadId)
        val behov = lagBehov(lagYrkesaktivitet(a1, sykedager(1.jan, 31.jan), original, korrigering))

        // When
        val grunnlag = utled(behov, listOf(original, korrigering))

        // Then
        assertEquals(emptyList(), grunnlag.dagerMedOppgittUtlandsopphold)
        assertEquals(emptyList(), grunnlag.dagerTilVurdering)
        assertEquals(emptyList(), grunnlag.varsler)
    }

    @Test
    fun `oppholdene i den korrigerende søknaden gjelder`() {
        // Given
        val original = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val korrigering = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(10.jan, 10.jan)), korrigerer = original.søknadId)
        val behov = lagBehov(lagYrkesaktivitet(a1, sykedager(1.jan, 31.jan), original, korrigering))

        // When
        val grunnlag = utled(behov, listOf(original, korrigering))

        // Then
        assertEquals(listOf(DagMedKilder(10.jan, listOf(kilde(korrigering)))), grunnlag.dagerTilVurdering)
    }

    @Test
    fun `dager der den gjeldende vurderingen ikke er oppfylt, er avslagsdager`() {
        // Given
        val avslag = lagVurdering(listOf(5.jan, 6.jan), oppfylt = false, vurdertTidspunkt = Instant.parse("2026-02-01T12:00:00Z"))
        val omgjøring = lagVurdering(listOf(6.jan), oppfylt = true, vurdertTidspunkt = Instant.parse("2026-02-01T13:00:00Z"))
        val utenforPerioden = lagVurdering(listOf(1.feb), oppfylt = false)

        // When
        val grunnlag = utled(lagBehov(), emptyList(), listOf(avslag, omgjøring, utenforPerioden))

        // Then
        assertEquals(
            listOf(
                Avslagsdag(
                    dato = 5.jan,
                    vilkår = LøpendeVilkår.OPPHOLDSKRAV,
                    vilkårskoder =
                        listOf(
                            Vilkårskode.OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND,
                            Vilkårskode.UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8,
                            Vilkårskode.UNNTAK_INNLAGT_HELSEINSTITUSJON,
                            Vilkårskode.UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER,
                        ),
                ),
            ),
            grunnlag.avslagsdager,
        )
        assertEquals(listOf(avslag.vurdertDag(5.jan), omgjøring.vurdertDag(6.jan)), grunnlag.vurderteDager)
    }

    @Test
    fun `samme behov og samme gjeldende vurderinger som det siste grunnlaget gir samme grunnlag`() {
        // Given
        val opphold = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val behov = lagBehov(lagYrkesaktivitet(a1, sykedager(1.jan, 31.jan), opphold))
        val vurdering = lagVurdering(listOf(5.jan), oppfylt = false)
        val siste = GrunnlagForLøpendeVilkår.forBehov(behov, listOf(opphold), listOf(vurdering), siste = null)

        // When
        val grunnlag = GrunnlagForLøpendeVilkår.forBehov(behov, listOf(opphold), listOf(vurdering), siste)

        // Then
        assertSame(siste, grunnlag)
    }

    @Test
    fun `søknadene i en annen rekkefølge gir samme grunnlag`() {
        // Given
        val oppholdA1 = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val oppholdA2 = lagOppgittUtlandsopphold(a2, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val behov = lagBehov(lagYrkesaktivitet(a1, sykedager(1.jan, 31.jan), oppholdA1), lagYrkesaktivitet(a2, sykedager(1.jan, 31.jan), oppholdA2))
        val siste = GrunnlagForLøpendeVilkår.forBehov(behov, listOf(oppholdA1, oppholdA2), emptyList(), siste = null)

        // When
        val grunnlag = GrunnlagForLøpendeVilkår.forBehov(behov, listOf(oppholdA2, oppholdA1), emptyList(), siste)

        // Then
        assertSame(siste, grunnlag)
    }

    @Test
    fun `en ny vurdering gir et nytt grunnlag`() {
        // Given
        val opphold = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val behov = lagBehov(lagYrkesaktivitet(a1, sykedager(1.jan, 31.jan), opphold))
        val siste = GrunnlagForLøpendeVilkår.forBehov(behov, listOf(opphold), emptyList(), siste = null)

        // When
        val grunnlag = GrunnlagForLøpendeVilkår.forBehov(behov, listOf(opphold), listOf(lagVurdering(listOf(5.jan), oppfylt = false)), siste)

        // Then
        assertNotEquals(siste.id, grunnlag.id)
        assertEquals(listOf(6.jan), grunnlag.innhold.dagerTilVurdering.map { it.dato })
    }

    @Test
    fun `et endret behov gir et nytt grunnlag`() {
        // Given
        val opphold = lagOppgittUtlandsopphold(a1, utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        val siste =
            GrunnlagForLøpendeVilkår.forBehov(
                lagBehov(lagYrkesaktivitet(a1, sykedager(1.jan, 31.jan), opphold)),
                listOf(opphold),
                emptyList(),
                siste = null,
            )
        val endretBehov = lagBehov(lagYrkesaktivitet(a1, sykedager(1.jan, 4.jan) + dager(5.jan, 31.jan, "FERIEDAG"), opphold))

        // When
        val grunnlag = GrunnlagForLøpendeVilkår.forBehov(endretBehov, listOf(opphold), emptyList(), siste)

        // Then
        assertNotEquals(siste.id, grunnlag.id)
        assertEquals(endretBehov, grunnlag.innhold.behov)
    }

    private fun utled(
        behov: Avslagsdagerbehov,
        oppgittUtlandsopphold: List<OppgittUtlandsopphold>,
        vurderinger: List<Oppholdskravvurdering> = emptyList(),
    ) = GrunnlagForLøpendeVilkår.forBehov(behov, oppgittUtlandsopphold, vurderinger, siste = null).innhold

    private companion object {
        val FØDSELSNUMMER = lagIdentitetsnummer()
        val a1 = Yrkesaktivitet("ARBEIDSTAKER", "a1")
        val a2 = Yrkesaktivitet("ARBEIDSTAKER", "a2")
        val a3 = Yrkesaktivitet("ARBEIDSTAKER", "a3")

        val Int.jan: LocalDate get() = LocalDate.of(2026, 1, this)
        val Int.feb: LocalDate get() = LocalDate.of(2026, 2, this)

        fun dager(
            fom: LocalDate,
            tom: LocalDate,
            dagtype: String,
        ) = fom.datesUntil(tom.plusDays(1)).toList().map { Sykdomsdag(it, Dagtype(dagtype)) }

        fun sykedager(
            fom: LocalDate,
            tom: LocalDate,
        ) = dager(fom, tom, "SYKEDAG")

        fun kilde(opphold: OppgittUtlandsopphold) = Kilde(opphold.meldingsreferanseId, opphold.yrkesaktivitet)

        fun lagVurdering(
            dager: List<LocalDate>,
            oppfylt: Boolean,
            vurdertTidspunkt: Instant = Instant.parse("2026-02-01T12:00:00Z"),
        ): Oppholdskravvurdering {
            val resultat =
                Oppholdskravvurdering.vurder(
                    fødselsnummer = FØDSELSNUMMER,
                    behandlingId = UUID.randomUUID(),
                    dager = dager,
                    svar =
                        SvarPåOppholdskrav(
                            eøsBorger = true,
                            oppholdIGodkjentOmråde = oppfylt,
                            unntakAndreLedd = UnntakAndreLedd.INGEN.takeUnless { oppfylt },
                            godkjentOppholdInntilFireUker = false.takeUnless { oppfylt },
                        ),
                    fritekstbegrunnelse = "Begrunnelse",
                    journalpostId = emptyList(),
                    saksbehandlerIdent = "A123456",
                    vurdertTidspunkt = vurdertTidspunkt,
                )
            return (resultat as OppholdskravvurderingResultat.Vurdert).vurdering
        }

        fun lagOppgittUtlandsopphold(
            yrkesaktivitet: Yrkesaktivitet,
            utlandsoppholdsperioder: List<Periode>,
            søknadId: SøknadId = SøknadId(UUID.randomUUID()),
            korrigerer: SøknadId? = null,
        ) = OppgittUtlandsopphold(
            meldingsreferanseId = MeldingsreferanseId(UUID.randomUUID()),
            søknadId = søknadId,
            korrigerer = korrigerer,
            fødselsnummer = FØDSELSNUMMER,
            yrkesaktivitet = yrkesaktivitet,
            utlandsoppholdsperioder = utlandsoppholdsperioder,
            hentetTidspunkt = Instant.parse("2026-02-01T12:00:00Z"),
        )

        fun lagYrkesaktivitet(
            yrkesaktivitet: Yrkesaktivitet,
            sykdomstidslinje: List<Sykdomsdag>,
            vararg oppgittUtlandsopphold: OppgittUtlandsopphold,
        ) = YrkesaktivitetIBehov(yrkesaktivitet, sykdomstidslinje, oppgittUtlandsopphold.map { it.meldingsreferanseId })

        fun lagBehov(
            vararg yrkesaktiviteter: YrkesaktivitetIBehov,
            periode: Periode = Periode(1.jan, 31.jan),
        ) = Avslagsdagerbehov(FØDSELSNUMMER, periode, yrkesaktiviteter.toList())
    }
}
