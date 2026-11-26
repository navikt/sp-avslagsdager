package no.nav.helse.sykepenger.avslagsdager.domain

import java.time.LocalDate
import java.util.UUID

@JvmInline
internal value class GrunnlagForLøpendeVilkårId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun ny() = GrunnlagForLøpendeVilkårId(UUID.randomUUID())
    }
}

internal data class Kilde(
    val søknadMeldingsreferanseId: MeldingsreferanseId,
    val yrkesaktivitet: Yrkesaktivitet,
)

internal data class DagMedKilder(
    val dato: LocalDate,
    val kilder: List<Kilde>,
)

/** En dag som er avslått etter et løpende vilkår. Listen er felles for alle løpende vilkår (ADR 0023). */
internal data class Avslagsdag(
    val dato: LocalDate,
    val vilkår: LøpendeVilkår,
    val vilkårskoder: List<Vilkårskode>,
)

internal enum class Varselkode {
    RV_SØ_8,
}

internal data class Varsel(
    val yrkesaktivitet: Yrkesaktivitet,
    val varselkode: Varselkode,
)

/** Alt i grunnlaget unntatt ID-en. To grunnlag med samme innhold bygger på det samme. */
internal data class Grunnlagsinnhold(
    val behov: Avslagsdagerbehov,
    val dagerMedOppgittUtlandsopphold: List<DagMedKilder>,
    val dagerTilVurdering: List<DagMedKilder>,
    val varsler: List<Varsel>,
    val vurderteDager: List<VurdertDag>,
    val avslagsdager: List<Avslagsdag>,
)

/**
 * Svaret på behovet `Avslagsdager`, sammen med behovet det bygde på (ADR 0023). Et grunnlag endres aldri. Gir et nytt
 * behov samme innhold som det siste grunnlaget for samme person og beregningsperiode, gjelder det siste grunnlaget.
 */
internal class GrunnlagForLøpendeVilkår private constructor(
    val id: GrunnlagForLøpendeVilkårId,
    val innhold: Grunnlagsinnhold,
) {
    companion object {
        fun forBehov(
            behov: Avslagsdagerbehov,
            oppgittUtlandsopphold: List<OppgittUtlandsopphold>,
            vurderinger: List<Oppholdskravvurdering>,
            siste: GrunnlagForLøpendeVilkår?,
        ): GrunnlagForLøpendeVilkår {
            val innhold = utled(behov, oppgittUtlandsopphold, vurderinger)
            if (siste != null && siste.innhold == innhold) return siste
            return GrunnlagForLøpendeVilkår(GrunnlagForLøpendeVilkårId.ny(), innhold)
        }

        fun fraLagring(
            id: GrunnlagForLøpendeVilkårId,
            innhold: Grunnlagsinnhold,
        ) = GrunnlagForLøpendeVilkår(id, innhold)

        private fun utled(
            behov: Avslagsdagerbehov,
            oppgittUtlandsopphold: List<OppgittUtlandsopphold>,
            vurderinger: List<Oppholdskravvurdering>,
        ): Grunnlagsinnhold {
            // Lagringsrekkefølgen er tilfeldig, så kildene sorteres for at samme innhold skal gi samme grunnlag
            val gjeldendeOpphold = oppgittUtlandsopphold.gjeldende().sortedBy { it.meldingsreferanseId.value }
            val dagerMedOpphold =
                behov.periode.dager().mapNotNull { dato ->
                    val kilder = gjeldendeOpphold.filter { opphold -> opphold.utlandsoppholdsperioder.any { dato in it } }
                    if (kilder.isEmpty()) null else DagMedKilder(dato, kilder.map { Kilde(it.meldingsreferanseId, it.yrkesaktivitet) })
                }
            val gjeldendeVurderinger = vurderinger.gjeldendePerDag(behov.periode)
            val dagerTilVurdering =
                dagerMedOpphold.filter { dag ->
                    dag.dato !in gjeldendeVurderinger && behov.harDagMedRettTilSykepenger(dag.dato)
                }
            val varsler =
                behov.yrkesaktiviteter
                    .filter { yrkesaktivitet -> dagerTilVurdering.any { yrkesaktivitet.harRettTilSykepenger(it.dato) } }
                    .map { Varsel(it.yrkesaktivitet, Varselkode.RV_SØ_8) }
            val vurderteDager = gjeldendeVurderinger.map { (dato, vurdering) -> vurdering.vurdertDag(dato) }
            val avslagsdager =
                vurderteDager
                    .filter { it.utfall == Utfall.IkkeOppfylt }
                    .map { Avslagsdag(dato = it.dato, vilkår = it.vilkår, vilkårskoder = it.vilkårskoder) }
            return Grunnlagsinnhold(behov, dagerMedOpphold, dagerTilVurdering, varsler, vurderteDager, avslagsdager)
        }
    }
}

// Den nyeste søknaden gjelder, så oppholdene i en søknad som er korrigert, regnes ikke med
private fun List<OppgittUtlandsopphold>.gjeldende(): List<OppgittUtlandsopphold> {
    val korrigerte = mapNotNull { it.korrigerer }.toSet()
    return filterNot { it.søknadId in korrigerte }
}
