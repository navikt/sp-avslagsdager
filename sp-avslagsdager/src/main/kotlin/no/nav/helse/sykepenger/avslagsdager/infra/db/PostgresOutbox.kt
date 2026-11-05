package no.nav.helse.sykepenger.avslagsdager.infra.db

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.avslagsdager.application.HandlingType
import no.nav.helse.sykepenger.avslagsdager.application.Outbox
import no.nav.helse.sykepenger.avslagsdager.application.OutboxKonvolutt
import no.nav.helse.sykepenger.avslagsdager.application.OutboxKonvoluttId
import no.nav.helse.sykepenger.avslagsdager.application.OutboxMelding
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import org.intellij.lang.annotations.Language
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class PostgresOutbox(
    private val session: Session,
) : Outbox {
    override fun leggTil(konvolutt: OutboxKonvolutt) {
        @Language("PostgreSQL")
        val sql = """
            insert into outbox (id, melding, fodselsnummer)
            values (:id, cast(:melding as jsonb), :fodselsnummer)
        """
        session.run(
            queryOf(
                sql,
                mapOf(
                    "id" to konvolutt.id.value,
                    "melding" to objectMapper.writeValueAsString(konvolutt.melding.tilDto()),
                    "fodselsnummer" to konvolutt.identitetsnummer.value,
                ),
            ).asUpdate,
        )
    }

    override fun hentUpubliserte(maksAntall: Int): List<OutboxKonvolutt> {
        @Language("PostgreSQL")
        val sql = """
            select id, melding, fodselsnummer
            from outbox
            where publisert_tidspunkt is null
            order by sekvensnummer
            limit :maksAntall
            for update
        """
        return session.run(queryOf(sql, mapOf("maksAntall" to maksAntall)).map(::tilKonvolutt).asList)
    }

    override fun markerSomSendt(id: OutboxKonvoluttId) {
        @Language("PostgreSQL")
        val sql = """
            update outbox set publisert_tidspunkt = :now where id = :id
        """
        session.run(queryOf(sql, mapOf("id" to id.value, "now" to Instant.now())).asUpdate)
    }
}

private val objectMapper = jacksonObjectMapper()

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = OutboxMeldingDto.Saksbehandlerhandling::class, name = "SAKSBEHANDLERHANDLING"),
    JsonSubTypes.Type(value = OutboxMeldingDto.AvslagsdagerEndret::class, name = "AVSLAGSDAGER_ENDRET"),
)
private sealed interface OutboxMeldingDto {
    data class Saksbehandlerhandling(
        val saksbehandlerIdent: String,
        val handlingId: UUID,
        val handlingType: HandlingTypeDto,
        val kreverTotrinnsvurdering: Boolean,
        val tidspunkt: Instant,
        val behandlingId: UUID,
    ) : OutboxMeldingDto

    data class AvslagsdagerEndret(
        val vurderingId: UUID,
        val perioder: List<PeriodeDto>,
        val manuellVurdering: Boolean,
    ) : OutboxMeldingDto
}

private enum class HandlingTypeDto {
    MANUELT_VURDERT_OPPHOLDSKRAV,
}

private data class PeriodeDto(
    val fom: LocalDate,
    val tom: LocalDate,
)

private fun tilKonvolutt(row: Row) =
    OutboxKonvolutt(
        id = OutboxKonvoluttId(row.uuid("id")),
        melding = objectMapper.readValue<OutboxMeldingDto>(row.string("melding")).tilOutboxMelding(),
        identitetsnummer = Identitetsnummer(row.string("fodselsnummer")),
    )

private fun OutboxMelding.tilDto(): OutboxMeldingDto =
    when (this) {
        is OutboxMelding.Saksbehandlerhandling ->
            OutboxMeldingDto.Saksbehandlerhandling(
                saksbehandlerIdent = saksbehandlerIdent,
                handlingId = handlingId,
                handlingType = handlingType.tilDto(),
                kreverTotrinnsvurdering = kreverTotrinnsvurdering,
                tidspunkt = tidspunkt,
                behandlingId = behandlingId,
            )

        is OutboxMelding.AvslagsdagerEndret ->
            OutboxMeldingDto.AvslagsdagerEndret(
                vurderingId = vurderingId,
                perioder = perioder.map { PeriodeDto(it.fom, it.tom) },
                manuellVurdering = manuellVurdering,
            )
    }

private fun OutboxMeldingDto.tilOutboxMelding(): OutboxMelding =
    when (this) {
        is OutboxMeldingDto.Saksbehandlerhandling ->
            OutboxMelding.Saksbehandlerhandling(
                saksbehandlerIdent = saksbehandlerIdent,
                handlingId = handlingId,
                handlingType = handlingType.tilDomene(),
                kreverTotrinnsvurdering = kreverTotrinnsvurdering,
                tidspunkt = tidspunkt,
                behandlingId = behandlingId,
            )

        is OutboxMeldingDto.AvslagsdagerEndret ->
            OutboxMelding.AvslagsdagerEndret(
                vurderingId = vurderingId,
                perioder = perioder.map { Periode(it.fom, it.tom) },
                manuellVurdering = manuellVurdering,
            )
    }

private fun HandlingType.tilDto() =
    when (this) {
        HandlingType.MANUELT_VURDERT_OPPHOLDSKRAV -> HandlingTypeDto.MANUELT_VURDERT_OPPHOLDSKRAV
    }

private fun HandlingTypeDto.tilDomene() =
    when (this) {
        HandlingTypeDto.MANUELT_VURDERT_OPPHOLDSKRAV -> HandlingType.MANUELT_VURDERT_OPPHOLDSKRAV
    }
