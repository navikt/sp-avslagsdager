package no.nav.helse.sykepenger.avslagsdager.infra.db

import no.nav.helse.sykepenger.avslagsdager.domain.MeldingsreferanseId
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.SøknadId
import no.nav.helse.sykepenger.avslagsdager.domain.OppgittUtlandsopphold
import no.nav.helse.sykepenger.avslagsdager.domain.Yrkesaktivitet
import no.nav.sykepenger.libs.testing.testdata.lagIdentitetsnummer
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

internal class PostgresOppgittUtlandsoppholdRepositoryTest : DatabaseTest() {
    @Test
    fun `lagrede oppgitte utlandsopphold kan hentes for personen med alle felter`() {
        // Given
        val arbeidstaker =
            lagOppgittUtlandsopphold(
                yrkesaktivitet = Yrkesaktivitet("ARBEIDSTAKER", "987654321"),
                utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan), Periode(10.jan, 10.jan)),
                korrigerer = SøknadId(UUID.randomUUID()),
            )
        val selvstendig = lagOppgittUtlandsopphold(yrkesaktivitet = Yrkesaktivitet("SELVSTENDIG", null), utlandsoppholdsperioder = emptyList())
        val annenPerson = lagOppgittUtlandsopphold(fødselsnummer = lagIdentitetsnummer())

        // When
        transaksjon { kontekst -> listOf(arbeidstaker, selvstendig, annenPerson).forEach { kontekst.oppgittUtlandsopphold.lagre(it) } }

        // Then
        assertEquals(
            setOf(arbeidstaker, selvstendig),
            transaksjon { it.oppgittUtlandsopphold.finnForPerson(FØDSELSNUMMER) }.toSet(),
        )
    }

    @Test
    fun `et oppgitt utlandsopphold som allerede er lagret, lagres ikke på nytt`() {
        // Given
        val opphold = lagOppgittUtlandsopphold(utlandsoppholdsperioder = listOf(Periode(5.jan, 6.jan)))
        transaksjon { it.oppgittUtlandsopphold.lagre(opphold) }

        // When
        transaksjon { it.oppgittUtlandsopphold.lagre(opphold.copy(utlandsoppholdsperioder = emptyList())) }

        // Then
        assertEquals(listOf(opphold), transaksjon { it.oppgittUtlandsopphold.finnForPerson(FØDSELSNUMMER) })
    }

    @Test
    fun `finner hvilke søknader som allerede er lagret`() {
        // Given
        val lagret = lagOppgittUtlandsopphold()
        transaksjon { it.oppgittUtlandsopphold.lagre(lagret) }
        val ukjent = MeldingsreferanseId(UUID.randomUUID())

        // When
        val kjente = transaksjon { it.oppgittUtlandsopphold.finnLagrede(listOf(lagret.meldingsreferanseId, ukjent)) }

        // Then
        assertEquals(setOf(lagret.meldingsreferanseId), kjente)
    }

    private companion object {
        val FØDSELSNUMMER = lagIdentitetsnummer()

        val Int.jan: LocalDate get() = LocalDate.of(2026, 1, this)

        fun lagOppgittUtlandsopphold(
            fødselsnummer: String = FØDSELSNUMMER,
            yrkesaktivitet: Yrkesaktivitet = Yrkesaktivitet("ARBEIDSTAKER", "987654321"),
            utlandsoppholdsperioder: List<Periode> = listOf(Periode(5.jan, 6.jan)),
            korrigerer: SøknadId? = null,
        ) = OppgittUtlandsopphold(
            meldingsreferanseId = MeldingsreferanseId(UUID.randomUUID()),
            søknadId = SøknadId(UUID.randomUUID()),
            korrigerer = korrigerer,
            fødselsnummer = fødselsnummer,
            yrkesaktivitet = yrkesaktivitet,
            utlandsoppholdsperioder = utlandsoppholdsperioder,
            hentetTidspunkt = Instant.parse("2026-02-01T12:00:00Z"),
        )
    }
}
