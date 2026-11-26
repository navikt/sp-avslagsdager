package no.nav.helse.sykepenger.avslagsdager.infra.db

import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdagerbehov
import no.nav.helse.sykepenger.avslagsdager.domain.Dagtype
import no.nav.helse.sykepenger.avslagsdager.domain.GrunnlagForLøpendeVilkår
import no.nav.helse.sykepenger.avslagsdager.domain.MeldingsreferanseId
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingResultat
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.SvarPåOppholdskrav
import no.nav.helse.sykepenger.avslagsdager.domain.Sykdomsdag
import no.nav.helse.sykepenger.avslagsdager.domain.SøknadId
import no.nav.helse.sykepenger.avslagsdager.domain.OppgittUtlandsopphold
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd
import no.nav.helse.sykepenger.avslagsdager.domain.Yrkesaktivitet
import no.nav.helse.sykepenger.avslagsdager.domain.YrkesaktivitetIBehov
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class PostgresGrunnlagForLøpendeVilkårRepositoryTest : DatabaseTest() {
    @Test
    fun `et lagret grunnlag kan hentes igjen med hele innholdet`() {
        // Given
        val grunnlag = lagGrunnlag()

        // When
        transaksjon { it.grunnlagForLøpendeVilkår.lagre(grunnlag) }

        // Then
        val hentet = transaksjon { it.grunnlagForLøpendeVilkår.finnSiste(FØDSELSNUMMER, PERIODE) }
        assertEquals(grunnlag.id, hentet?.id)
        assertEquals(grunnlag.innhold, hentet?.innhold)
    }

    @Test
    fun `finner det siste grunnlaget for personen og beregningsperioden`() {
        // Given
        val første = lagGrunnlag()
        val siste = lagGrunnlag()
        val annenPeriode = lagGrunnlag(periode = Periode(1.jan, 30.jan))
        val annenPerson = lagGrunnlag(fødselsnummer = lagIdentitetsnummer())
        listOf(første, siste, annenPeriode, annenPerson).forEach { grunnlag ->
            transaksjon { it.grunnlagForLøpendeVilkår.lagre(grunnlag) }
        }

        // When
        val hentet = transaksjon { it.grunnlagForLøpendeVilkår.finnSiste(FØDSELSNUMMER, PERIODE) }

        // Then
        assertEquals(siste.id, hentet?.id)
    }

    @Test
    fun `uten grunnlag for beregningsperioden finnes ikke noe siste grunnlag`() {
        assertNull(transaksjon { it.grunnlagForLøpendeVilkår.finnSiste(FØDSELSNUMMER, PERIODE) })
    }

    private companion object {
        val FØDSELSNUMMER = lagIdentitetsnummer()

        val Int.jan: LocalDate get() = LocalDate.of(2026, 1, this)

        val PERIODE = Periode(1.jan, 31.jan)

        fun lagGrunnlag(
            fødselsnummer: String = FØDSELSNUMMER,
            periode: Periode = PERIODE,
        ): GrunnlagForLøpendeVilkår {
            val a1 = Yrkesaktivitet("ARBEIDSTAKER", "987654321")
            val selvstendig = Yrkesaktivitet("SELVSTENDIG", null)
            val opphold =
                OppgittUtlandsopphold(
                    meldingsreferanseId = MeldingsreferanseId(UUID.randomUUID()),
                    søknadId = SøknadId(UUID.randomUUID()),
                    korrigerer = null,
                    fødselsnummer = fødselsnummer,
                    yrkesaktivitet = a1,
                    utlandsoppholdsperioder = listOf(Periode(5.jan, 7.jan)),
                    hentetTidspunkt = Instant.parse("2026-02-01T12:00:00Z"),
                )
            val behov =
                Avslagsdagerbehov(
                    fødselsnummer = fødselsnummer,
                    periode = periode,
                    yrkesaktiviteter =
                        listOf(
                            YrkesaktivitetIBehov(
                                a1,
                                periode.dager().map { Sykdomsdag(it, Dagtype("SYKEDAG")) },
                                listOf(opphold.meldingsreferanseId),
                            ),
                            YrkesaktivitetIBehov(selvstendig, periode.dager().map { Sykdomsdag(it, Dagtype("FERIEDAG")) }, emptyList()),
                        ),
                )
            val avslag =
                Oppholdskravvurdering.vurder(
                    fødselsnummer = fødselsnummer,
                    behandlingId = UUID.randomUUID(),
                    dager = listOf(5.jan),
                    svar =
                        SvarPåOppholdskrav(
                            eøsBorger = true,
                            oppholdIGodkjentOmråde = false,
                            unntakAndreLedd = UnntakAndreLedd.INGEN,
                            godkjentOppholdInntilFireUker = false,
                        ),
                    fritekstbegrunnelse = "Begrunnelse",
                    journalpostId = emptyList(),
                    saksbehandlerIdent = "A123456",
                ) as OppholdskravvurderingResultat.Vurdert
            return GrunnlagForLøpendeVilkår.forBehov(behov, listOf(opphold), listOf(avslag.vurdering), siste = null)
        }
    }
}
