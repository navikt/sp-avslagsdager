package no.nav.helse.sykepenger.avslagsdager.infra.db

import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.avslagsdager.application.GrunnlagForLøpendeVilkårRepository
import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdag
import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdagerbehov
import no.nav.helse.sykepenger.avslagsdager.domain.DagMedKilder
import no.nav.helse.sykepenger.avslagsdager.domain.Dagtype
import no.nav.helse.sykepenger.avslagsdager.domain.GrunnlagForLøpendeVilkår
import no.nav.helse.sykepenger.avslagsdager.domain.GrunnlagForLøpendeVilkårId
import no.nav.helse.sykepenger.avslagsdager.domain.Grunnlagsinnhold
import no.nav.helse.sykepenger.avslagsdager.domain.Kilde
import no.nav.helse.sykepenger.avslagsdager.domain.LøpendeVilkår
import no.nav.helse.sykepenger.avslagsdager.domain.MeldingsreferanseId
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.Sykdomsdag
import no.nav.helse.sykepenger.avslagsdager.domain.Utfall
import no.nav.helse.sykepenger.avslagsdager.domain.Varsel
import no.nav.helse.sykepenger.avslagsdager.domain.Varselkode
import no.nav.helse.sykepenger.avslagsdager.domain.Vilkårskode
import no.nav.helse.sykepenger.avslagsdager.domain.VurdertDag
import no.nav.helse.sykepenger.avslagsdager.domain.Yrkesaktivitet
import no.nav.helse.sykepenger.avslagsdager.domain.YrkesaktivitetIBehov
import org.intellij.lang.annotations.Language
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.util.UUID

internal class PostgresGrunnlagForLøpendeVilkårRepository(
    private val session: Session,
) : GrunnlagForLøpendeVilkårRepository {
    override fun lagre(grunnlag: GrunnlagForLøpendeVilkår) {
        val behov = grunnlag.innhold.behov
        session.run(
            queryOf(
                INSERT,
                mapOf(
                    "id" to grunnlag.id.value,
                    "fodselsnummer" to behov.fødselsnummer,
                    "fom" to behov.periode.fom,
                    "tom" to behov.periode.tom,
                    "grunnlag" to objectMapper.writeValueAsString(grunnlag.innhold.tilDto()),
                ),
            ).asUpdate,
        )
    }

    override fun finnSiste(
        fødselsnummer: String,
        periode: Periode,
    ): GrunnlagForLøpendeVilkår? =
        session.run(
            queryOf(SELECT_SISTE, mapOf("fodselsnummer" to fødselsnummer, "fom" to periode.fom, "tom" to periode.tom))
                .map { row ->
                    GrunnlagForLøpendeVilkår.fraLagring(
                        id = GrunnlagForLøpendeVilkårId(row.uuid("id")),
                        innhold = objectMapper.readValue<GrunnlagsinnholdDto>(row.string("grunnlag")).tilDomene(),
                    )
                }.asSingle,
        )

    private companion object {
        val objectMapper = jacksonObjectMapper()

        @Language("PostgreSQL")
        const val INSERT = """
            insert into grunnlag_for_lopende_vilkar (id, fødselsnummer, fom, tom, grunnlag)
            values (:id, :fodselsnummer, :fom, :tom, cast(:grunnlag as jsonb))
        """

        @Language("PostgreSQL")
        const val SELECT_SISTE = """
            select id, grunnlag
            from grunnlag_for_lopende_vilkar
            where fødselsnummer = :fodselsnummer and fom = :fom and tom = :tom
            order by opprettet desc
            limit 1
        """
    }
}

private data class GrunnlagsinnholdDto(
    val behov: BehovDto,
    val dagerMedOppgittUtlandsopphold: List<DagMedKilderDto>,
    val dagerTilVurdering: List<DagMedKilderDto>,
    val varsler: List<VarselDto>,
    val vurderteDager: List<VurdertDagDto>,
    val avslagsdager: List<AvslagsdagDto>,
)

private data class BehovDto(
    val fødselsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val yrkesaktiviteter: List<YrkesaktivitetIBehovDto>,
)

private data class YrkesaktivitetIBehovDto(
    val yrkesaktivitetstype: String,
    val organisasjonsnummer: String?,
    val sykdomstidslinje: List<SykdomsdagDto>,
    val søknadMeldingsreferanseIder: List<UUID>,
)

private data class SykdomsdagDto(
    val dato: LocalDate,
    val dagtype: String,
)

private data class DagMedKilderDto(
    val dato: LocalDate,
    val kilder: List<KildeDto>,
)

private data class KildeDto(
    val søknadMeldingsreferanseId: UUID,
    val yrkesaktivitetstype: String,
    val organisasjonsnummer: String?,
)

private data class VarselDto(
    val yrkesaktivitetstype: String,
    val organisasjonsnummer: String?,
    val varselkode: String,
)

private data class VurdertDagDto(
    val vurderingId: UUID,
    val vilkår: String,
    val dato: LocalDate,
    val utfall: String,
    val vilkårskoder: List<String>,
)

private data class AvslagsdagDto(
    val dato: LocalDate,
    val vilkår: String,
    val vilkårskoder: List<String>,
)

private fun Grunnlagsinnhold.tilDto() =
    GrunnlagsinnholdDto(
        behov =
            BehovDto(
                fødselsnummer = behov.fødselsnummer,
                fom = behov.periode.fom,
                tom = behov.periode.tom,
                yrkesaktiviteter =
                    behov.yrkesaktiviteter.map { yrkesaktivitet ->
                        YrkesaktivitetIBehovDto(
                            yrkesaktivitetstype = yrkesaktivitet.yrkesaktivitet.yrkesaktivitetstype,
                            organisasjonsnummer = yrkesaktivitet.yrkesaktivitet.organisasjonsnummer,
                            sykdomstidslinje = yrkesaktivitet.sykdomstidslinje.map { SykdomsdagDto(it.dato, it.dagtype.navn) },
                            søknadMeldingsreferanseIder = yrkesaktivitet.søknadMeldingsreferanseIder.map { it.value },
                        )
                    },
            ),
        dagerMedOppgittUtlandsopphold = dagerMedOppgittUtlandsopphold.map { it.tilDto() },
        dagerTilVurdering = dagerTilVurdering.map { it.tilDto() },
        varsler =
            varsler.map {
                VarselDto(it.yrkesaktivitet.yrkesaktivitetstype, it.yrkesaktivitet.organisasjonsnummer, it.varselkode.name)
            },
        vurderteDager =
            vurderteDager.map {
                VurdertDagDto(it.vurderingId.value, it.vilkår.name, it.dato, it.utfall.name, it.vilkårskoder.map { kode -> kode.name })
            },
        avslagsdager = avslagsdager.map { AvslagsdagDto(it.dato, it.vilkår.name, it.vilkårskoder.map { kode -> kode.name }) },
    )

private fun DagMedKilder.tilDto() =
    DagMedKilderDto(
        dato = dato,
        kilder =
            kilder.map {
                KildeDto(it.søknadMeldingsreferanseId.value, it.yrkesaktivitet.yrkesaktivitetstype, it.yrkesaktivitet.organisasjonsnummer)
            },
    )

private fun GrunnlagsinnholdDto.tilDomene() =
    Grunnlagsinnhold(
        behov =
            Avslagsdagerbehov(
                fødselsnummer = behov.fødselsnummer,
                periode = Periode(behov.fom, behov.tom),
                yrkesaktiviteter =
                    behov.yrkesaktiviteter.map { yrkesaktivitet ->
                        YrkesaktivitetIBehov(
                            yrkesaktivitet = Yrkesaktivitet(yrkesaktivitet.yrkesaktivitetstype, yrkesaktivitet.organisasjonsnummer),
                            sykdomstidslinje = yrkesaktivitet.sykdomstidslinje.map { Sykdomsdag(it.dato, Dagtype(it.dagtype)) },
                            søknadMeldingsreferanseIder = yrkesaktivitet.søknadMeldingsreferanseIder.map(::MeldingsreferanseId),
                        )
                    },
            ),
        dagerMedOppgittUtlandsopphold = dagerMedOppgittUtlandsopphold.map { it.tilDomene() },
        dagerTilVurdering = dagerTilVurdering.map { it.tilDomene() },
        varsler =
            varsler.map {
                Varsel(Yrkesaktivitet(it.yrkesaktivitetstype, it.organisasjonsnummer), Varselkode.valueOf(it.varselkode))
            },
        vurderteDager =
            vurderteDager.map {
                VurdertDag(
                    vurderingId = OppholdskravvurderingId(it.vurderingId),
                    vilkår = LøpendeVilkår.valueOf(it.vilkår),
                    dato = it.dato,
                    utfall = Utfall.valueOf(it.utfall),
                    vilkårskoder = it.vilkårskoder.map(Vilkårskode::valueOf),
                )
            },
        avslagsdager =
            avslagsdager.map {
                Avslagsdag(it.dato, LøpendeVilkår.valueOf(it.vilkår), it.vilkårskoder.map(Vilkårskode::valueOf))
            },
    )

private fun DagMedKilderDto.tilDomene() =
    DagMedKilder(
        dato = dato,
        kilder =
            kilder.map {
                Kilde(MeldingsreferanseId(it.søknadMeldingsreferanseId), Yrkesaktivitet(it.yrkesaktivitetstype, it.organisasjonsnummer))
            },
    )
