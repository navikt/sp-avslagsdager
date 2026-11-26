package no.nav.helse.sykepenger.avslagsdager.infra.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.avslagsdager.application.OppgittUtlandsoppholdRepository
import no.nav.helse.sykepenger.avslagsdager.domain.MeldingsreferanseId
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.SøknadId
import no.nav.helse.sykepenger.avslagsdager.domain.OppgittUtlandsopphold
import no.nav.helse.sykepenger.avslagsdager.domain.Yrkesaktivitet
import org.intellij.lang.annotations.Language
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

internal class PostgresOppgittUtlandsoppholdRepository(
    private val session: Session,
) : OppgittUtlandsoppholdRepository {
    override fun lagre(oppgittUtlandsopphold: OppgittUtlandsopphold) {
        session.run(
            queryOf(
                INSERT,
                mapOf(
                    "meldingsreferanseId" to oppgittUtlandsopphold.meldingsreferanseId.value,
                    "soknadId" to oppgittUtlandsopphold.søknadId.value,
                    "korrigerer" to oppgittUtlandsopphold.korrigerer?.value,
                    "fodselsnummer" to oppgittUtlandsopphold.fødselsnummer,
                    "yrkesaktivitetstype" to oppgittUtlandsopphold.yrkesaktivitet.yrkesaktivitetstype,
                    "organisasjonsnummer" to oppgittUtlandsopphold.yrkesaktivitet.organisasjonsnummer,
                    "utlandsoppholdsperioder" to objectMapper.writeValueAsString(oppgittUtlandsopphold.utlandsoppholdsperioder.map { PeriodeDto(it.fom, it.tom) }),
                    "hentetTidspunkt" to oppgittUtlandsopphold.hentetTidspunkt,
                ),
            ).asUpdate,
        )
    }

    override fun finnForPerson(fødselsnummer: String): List<OppgittUtlandsopphold> = session.run(queryOf(SELECT_FOR_PERSON, mapOf("fodselsnummer" to fødselsnummer)).map(::tilOppgittUtlandsopphold).asList)

    override fun finnLagrede(meldingsreferanseIder: List<MeldingsreferanseId>): Set<MeldingsreferanseId> {
        if (meldingsreferanseIder.isEmpty()) return emptySet()
        return session
            .run(
                queryOf(
                    SELECT_LAGREDE,
                    mapOf("ider" to session.createArrayOf("uuid", meldingsreferanseIder.map { it.value })),
                ).map { MeldingsreferanseId(it.uuid("meldingsreferanse_id")) }.asList,
            ).toSet()
    }

    private companion object {
        val objectMapper = jacksonObjectMapper()

        @Language("PostgreSQL")
        const val INSERT = """
            insert into oppgitt_utlandsopphold (
                meldingsreferanse_id, soknad_id, korrigerer, fødselsnummer, yrkesaktivitetstype, organisasjonsnummer,
                utlandsoppholdsperioder, hentet_tidspunkt
            )
            values (
                :meldingsreferanseId, :soknadId, :korrigerer, :fodselsnummer, :yrkesaktivitetstype, :organisasjonsnummer,
                cast(:utlandsoppholdsperioder as jsonb), :hentetTidspunkt
            )
            on conflict (meldingsreferanse_id) do nothing
        """

        @Language("PostgreSQL")
        const val SELECT_FOR_PERSON = """
            select meldingsreferanse_id, soknad_id, korrigerer, fødselsnummer, yrkesaktivitetstype, organisasjonsnummer,
                   utlandsoppholdsperioder, hentet_tidspunkt
            from oppgitt_utlandsopphold
            where fødselsnummer = :fodselsnummer
        """

        @Language("PostgreSQL")
        const val SELECT_LAGREDE = """
            select meldingsreferanse_id from oppgitt_utlandsopphold where meldingsreferanse_id = any(:ider)
        """

        fun tilOppgittUtlandsopphold(row: Row) =
            OppgittUtlandsopphold(
                meldingsreferanseId = MeldingsreferanseId(row.uuid("meldingsreferanse_id")),
                søknadId = SøknadId(row.uuid("soknad_id")),
                korrigerer = row.uuidOrNull("korrigerer")?.let(::SøknadId),
                fødselsnummer = row.string("fødselsnummer"),
                yrkesaktivitet = Yrkesaktivitet(row.string("yrkesaktivitetstype"), row.stringOrNull("organisasjonsnummer")),
                utlandsoppholdsperioder = objectMapper.readValue<List<PeriodeDto>>(row.string("utlandsoppholdsperioder")).map { Periode(it.fom, it.tom) },
                hentetTidspunkt = row.instant("hentet_tidspunkt"),
            )
    }
}

private data class PeriodeDto(
    val fom: LocalDate,
    val tom: LocalDate,
)
