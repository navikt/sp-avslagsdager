package no.nav.helse.sykepenger.avslagsdager.infra.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.avslagsdager.application.OppholdskravvurderingRepository
import no.nav.helse.sykepenger.avslagsdager.domain.Lovreferanse
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.SvarPåOppholdskrav
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd
import no.nav.helse.sykepenger.avslagsdager.domain.Utfall
import no.nav.helse.sykepenger.avslagsdager.domain.Vilkårskode
import no.nav.helse.sykepenger.avslagsdager.domain.Vilkårsvurdering
import org.intellij.lang.annotations.Language
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

internal class PostgresOppholdskravvurderingRepository(
    private val session: Session,
) : OppholdskravvurderingRepository {
    override fun lagre(vurdering: Oppholdskravvurdering) {
        try {
            lagreVurdering(vurdering)
        } catch (e: SQLException) {
            if (e.sqlState != UNIKHETSBRUDD) throw e
            throw IllegalStateException("Oppholdskravvurdering ${vurdering.id} er allerede lagret. Vurderinger endres ikke.", e)
        }
        vurdering.dager.forEach { dato ->
            session.run(queryOf(INSERT_DAG, mapOf("vurderingId" to vurdering.id.value, "dato" to dato)).asUpdate)
        }
        vurdering.vilkårsvurderinger.forEach { vilkårsvurdering ->
            session.run(
                queryOf(
                    INSERT_VILKARSVURDERING,
                    mapOf(
                        "vurderingId" to vurdering.id.value,
                        "vilkarskode" to vilkårsvurdering.vilkårskode.tilDb(),
                        "utfall" to vilkårsvurdering.utfall.tilDb(),
                        "lov" to vilkårsvurdering.lovreferanse.lov,
                        "paragraf" to vilkårsvurdering.lovreferanse.paragraf,
                        "avsnitt" to vilkårsvurdering.lovreferanse.avsnitt,
                        "setning" to vilkårsvurdering.lovreferanse.setning,
                        "bokstav" to vilkårsvurdering.lovreferanse.bokstav,
                        "iKraftFra" to vilkårsvurdering.lovreferanse.iKraftFra,
                    ),
                ).asUpdate,
            )
        }
    }

    override fun finn(id: OppholdskravvurderingId): Oppholdskravvurdering? {
        val rad = session.run(queryOf(SELECT_VURDERING, mapOf("id" to id.value)).map(::tilVurderingRad).asSingle) ?: return null
        val dager = session.run(queryOf(SELECT_DAGER, mapOf("vurderingId" to id.value)).map { it.localDate("dato") }.asList)
        val vilkårsvurderinger =
            session.run(queryOf(SELECT_VILKARSVURDERINGER, mapOf("vurderingId" to id.value)).map(::tilVilkårsvurdering).asList)
        return Oppholdskravvurdering.fraLagring(
            id = id,
            fødselsnummer = rad.fødselsnummer,
            behandlingId = rad.behandlingId,
            dager = dager,
            svar = rad.svar,
            vilkårsvurderinger = vilkårsvurderinger,
            utfall = rad.utfall,
            fritekstbegrunnelse = rad.fritekstbegrunnelse,
            journalpostId = rad.journalpostId,
            saksbehandlerIdent = rad.saksbehandlerIdent,
            vurdertTidspunkt = rad.vurdertTidspunkt,
        )
    }

    override fun finnForPeriode(
        fødselsnummer: String,
        periode: Periode,
    ): List<Oppholdskravvurdering> =
        session
            .run(
                queryOf(
                    SELECT_VURDERINGER_I_PERIODE,
                    mapOf("fodselsnummer" to fødselsnummer, "fom" to periode.fom, "tom" to periode.tom),
                ).map { OppholdskravvurderingId(it.uuid("id")) }.asList,
            ).map { id -> checkNotNull(finn(id)) }

    private fun lagreVurdering(vurdering: Oppholdskravvurdering) {
        session.run(
            queryOf(
                INSERT_VURDERING,
                mapOf(
                    "id" to vurdering.id.value,
                    "fodselsnummer" to vurdering.fødselsnummer,
                    "behandlingId" to vurdering.behandlingId,
                    "eosBorger" to vurdering.svar.eøsBorger,
                    "oppholdIGodkjentOmrade" to vurdering.svar.oppholdIGodkjentOmråde,
                    "unntakAndreLedd" to vurdering.svar.unntakAndreLedd?.tilDb(),
                    "godkjentOppholdInntilFireUker" to vurdering.svar.godkjentOppholdInntilFireUker,
                    "utfall" to vurdering.utfall.tilDb(),
                    "fritekstbegrunnelse" to vurdering.fritekstbegrunnelse,
                    "journalpostId" to session.createArrayOf("text", vurdering.journalpostId),
                    "saksbehandlerIdent" to vurdering.saksbehandlerIdent,
                    "vurdertTidspunkt" to vurdering.vurdertTidspunkt,
                ),
            ).asUpdate,
        )
    }

    private companion object {
        const val UNIKHETSBRUDD = "23505"

        @Language("PostgreSQL")
        const val INSERT_VURDERING = """
            insert into oppholdskravvurdering (
                id, fødselsnummer, behandling_id, eos_borger, opphold_i_godkjent_omrade, unntak_andre_ledd,
                godkjent_opphold_inntil_fire_uker, utfall, fritekstbegrunnelse, journalpost_id, saksbehandler_ident,
                vurdert_tidspunkt
            )
            values (
                :id, :fodselsnummer, :behandlingId, :eosBorger, :oppholdIGodkjentOmrade, :unntakAndreLedd,
                :godkjentOppholdInntilFireUker, :utfall, :fritekstbegrunnelse, :journalpostId, :saksbehandlerIdent,
                :vurdertTidspunkt
            )
        """

        @Language("PostgreSQL")
        const val INSERT_DAG = """
            insert into oppholdskravvurdering_dag (vurdering_id, dato) values (:vurderingId, :dato)
        """

        @Language("PostgreSQL")
        const val INSERT_VILKARSVURDERING = """
            insert into oppholdskravvurdering_vilkarsvurdering (
                vurdering_id, vilkårskode, utfall, lov, paragraf, avsnitt, setning, bokstav, i_kraft_fra
            )
            values (:vurderingId, :vilkarskode, :utfall, :lov, :paragraf, :avsnitt, :setning, :bokstav, :iKraftFra)
        """

        @Language("PostgreSQL")
        const val SELECT_VURDERING = """
            select fødselsnummer, behandling_id, eos_borger, opphold_i_godkjent_omrade, unntak_andre_ledd,
                   godkjent_opphold_inntil_fire_uker, utfall, fritekstbegrunnelse, journalpost_id, saksbehandler_ident,
                   vurdert_tidspunkt
            from oppholdskravvurdering
            where id = :id
        """

        @Language("PostgreSQL")
        const val SELECT_VURDERINGER_I_PERIODE = """
            select distinct v.id
            from oppholdskravvurdering v
            join oppholdskravvurdering_dag d on d.vurdering_id = v.id
            where v.fødselsnummer = :fodselsnummer and d.dato between :fom and :tom
        """

        @Language("PostgreSQL")
        const val SELECT_DAGER = """
            select dato from oppholdskravvurdering_dag where vurdering_id = :vurderingId order by dato
        """

        // Rekkefølgen på vilkårsvurderingene følger leddene i § 8-9, slik utledningen lager dem
        @Language("PostgreSQL")
        const val SELECT_VILKARSVURDERINGER = """
            select vilkårskode, utfall, lov, paragraf, avsnitt, setning, bokstav, i_kraft_fra
            from oppholdskravvurdering_vilkarsvurdering
            where vurdering_id = :vurderingId
            order by avsnitt, setning nulls last, bokstav nulls last
        """
    }
}

private class VurderingRad(
    val fødselsnummer: String,
    val behandlingId: UUID,
    val svar: SvarPåOppholdskrav,
    val utfall: Utfall,
    val fritekstbegrunnelse: String,
    val journalpostId: List<String>,
    val saksbehandlerIdent: String,
    val vurdertTidspunkt: Instant,
)

private fun tilVurderingRad(row: Row) =
    VurderingRad(
        fødselsnummer = row.string("fødselsnummer"),
        behandlingId = row.uuid("behandling_id"),
        svar =
            SvarPåOppholdskrav(
                eøsBorger = row.boolean("eos_borger"),
                oppholdIGodkjentOmråde = row.boolean("opphold_i_godkjent_omrade"),
                unntakAndreLedd = row.stringOrNull("unntak_andre_ledd")?.let(::unntakAndreLeddFraDb),
                godkjentOppholdInntilFireUker = row.anyOrNull("godkjent_opphold_inntil_fire_uker") as Boolean?,
            ),
        utfall = utfallFraDb(row.string("utfall")),
        fritekstbegrunnelse = row.string("fritekstbegrunnelse"),
        journalpostId = row.array<String>("journalpost_id").toList(),
        saksbehandlerIdent = row.string("saksbehandler_ident"),
        vurdertTidspunkt = row.instant("vurdert_tidspunkt"),
    )

private fun tilVilkårsvurdering(row: Row) =
    Vilkårsvurdering(
        vilkårskode = vilkårskodeFraDb(row.string("vilkårskode")),
        utfall = utfallFraDb(row.string("utfall")),
        lovreferanse =
            Lovreferanse(
                lov = row.string("lov"),
                paragraf = row.string("paragraf"),
                avsnitt = row.intOrNull("avsnitt"),
                setning = row.intOrNull("setning"),
                bokstav = row.stringOrNull("bokstav"),
                iKraftFra = row.localDate("i_kraft_fra"),
            ),
    )

private fun Utfall.tilDb() =
    when (this) {
        Utfall.Oppfylt -> "OPPFYLT"
        Utfall.IkkeOppfylt -> "IKKE_OPPFYLT"
    }

private fun utfallFraDb(verdi: String) =
    when (verdi) {
        "OPPFYLT" -> Utfall.Oppfylt
        "IKKE_OPPFYLT" -> Utfall.IkkeOppfylt
        else -> error("Ukjent utfall: $verdi")
    }

private fun UnntakAndreLedd.tilDb() =
    when (this) {
        UnntakAndreLedd.MEDLEM_ETTER_2_5_2_6_2_8 -> "MEDLEM_ETTER_2_5_2_6_2_8"
        UnntakAndreLedd.INNLAGT_I_HELSEINSTITUSJON -> "INNLAGT_I_HELSEINSTITUSJON"
        UnntakAndreLedd.INGEN -> "INGEN"
    }

private fun unntakAndreLeddFraDb(verdi: String) =
    when (verdi) {
        "MEDLEM_ETTER_2_5_2_6_2_8" -> UnntakAndreLedd.MEDLEM_ETTER_2_5_2_6_2_8
        "INNLAGT_I_HELSEINSTITUSJON" -> UnntakAndreLedd.INNLAGT_I_HELSEINSTITUSJON
        "INGEN" -> UnntakAndreLedd.INGEN
        else -> error("Ukjent unntak i andre ledd: $verdi")
    }

private fun Vilkårskode.tilDb() =
    when (this) {
        Vilkårskode.OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND -> "OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND"
        Vilkårskode.OPPHOLD_I_NORGE_IKKE_EØS_BORGER -> "OPPHOLD_I_NORGE_IKKE_EØS_BORGER"
        Vilkårskode.UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8 -> "UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8"
        Vilkårskode.UNNTAK_INNLAGT_HELSEINSTITUSJON -> "UNNTAK_INNLAGT_HELSEINSTITUSJON"
        Vilkårskode.UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER -> "UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER"
    }

private fun vilkårskodeFraDb(verdi: String) =
    when (verdi) {
        "OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND" -> Vilkårskode.OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND
        "OPPHOLD_I_NORGE_IKKE_EØS_BORGER" -> Vilkårskode.OPPHOLD_I_NORGE_IKKE_EØS_BORGER
        "UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8" -> Vilkårskode.UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8
        "UNNTAK_INNLAGT_HELSEINSTITUSJON" -> Vilkårskode.UNNTAK_INNLAGT_HELSEINSTITUSJON
        "UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER" -> Vilkårskode.UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER
        else -> error("Ukjent vilkårskode: $verdi")
    }
