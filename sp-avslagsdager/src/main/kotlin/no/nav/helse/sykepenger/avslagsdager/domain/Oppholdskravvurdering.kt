package no.nav.helse.sykepenger.avslagsdager.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal enum class UnntakAndreLedd {
    MEDLEM_ETTER_2_5_2_6_2_8,
    INNLAGT_I_HELSEINSTITUSJON,
    INGEN,
}

/**
 * Saksbehandlerens svar på spørsmålene om oppholdskravet i § 8-9.
 *
 * - [eøsBorger] velger setningen i første ledd, og [oppholdIGodkjentOmråde] gir utfallet for den.
 * - [unntakAndreLedd] svares når første ledd ikke er oppfylt.
 * - [godkjentOppholdInntilFireUker] svares når ingen av unntakene i andre ledd gjelder.
 */
internal data class SvarPåOppholdskrav(
    val eøsBorger: Boolean,
    val oppholdIGodkjentOmråde: Boolean,
    val unntakAndreLedd: UnntakAndreLedd?,
    val godkjentOppholdInntilFireUker: Boolean?,
)

internal enum class UgyldigOppholdskravvurdering {
    DAGER_MANGLER,
    UNNTAK_ANDRE_LEDD_MANGLER,
    GODKJENT_OPPHOLD_INNTIL_FIRE_UKER_MANGLER,
    FRITEKSTBEGRUNNELSE_MANGLER,
}

internal sealed interface OppholdskravvurderingResultat {
    data class Vurdert(
        val vurdering: Oppholdskravvurdering,
    ) : OppholdskravvurderingResultat

    data class Ugyldig(
        val ugyldighet: UgyldigOppholdskravvurdering,
    ) : OppholdskravvurderingResultat
}

/**
 * Saksbehandlerens vurdering av oppholdskravet i § 8-9 for en mengde dager. Vurderingen endres aldri. En omgjøring
 * er en ny vurdering.
 */
internal data class Oppholdskravvurdering(
    val id: OppholdskravvurderingId,
    val fødselsnummer: String,
    val behandlingId: UUID,
    val dager: List<LocalDate>,
    val svar: SvarPåOppholdskrav,
    val vilkårsvurderinger: List<Vilkårsvurdering>,
    val utfall: Utfall,
    val fritekstbegrunnelse: String,
    val journalpostId: List<String>,
    val saksbehandlerIdent: String,
    val vurdertTidspunkt: Instant,
) {
    companion object {
        fun vurder(
            fødselsnummer: String,
            behandlingId: UUID,
            dager: List<LocalDate>,
            svar: SvarPåOppholdskrav,
            fritekstbegrunnelse: String,
            journalpostId: List<String>,
            saksbehandlerIdent: String,
            vurdertTidspunkt: Instant = Instant.now(),
        ): OppholdskravvurderingResultat {
            val ugyldighet =
                when {
                    dager.isEmpty() -> UgyldigOppholdskravvurdering.DAGER_MANGLER
                    fritekstbegrunnelse.isBlank() -> UgyldigOppholdskravvurdering.FRITEKSTBEGRUNNELSE_MANGLER
                    else -> svar.ugyldighet()
                }
            if (ugyldighet != null) return OppholdskravvurderingResultat.Ugyldig(ugyldighet)

            val vilkårsvurderinger = svar.vilkårsvurderinger()
            return OppholdskravvurderingResultat.Vurdert(
                Oppholdskravvurdering(
                    id = OppholdskravvurderingId.ny(),
                    fødselsnummer = fødselsnummer,
                    behandlingId = behandlingId,
                    dager = dager.distinct().sorted(),
                    svar = svar.utenUbrukteSvar(),
                    vilkårsvurderinger = vilkårsvurderinger,
                    utfall = vilkårsvurderinger.samletUtfall(),
                    fritekstbegrunnelse = fritekstbegrunnelse,
                    journalpostId = journalpostId,
                    saksbehandlerIdent = saksbehandlerIdent,
                    vurdertTidspunkt = vurdertTidspunkt,
                ),
            )
        }

        fun fraLagring(
            id: OppholdskravvurderingId,
            fødselsnummer: String,
            behandlingId: UUID,
            dager: List<LocalDate>,
            svar: SvarPåOppholdskrav,
            vilkårsvurderinger: List<Vilkårsvurdering>,
            utfall: Utfall,
            fritekstbegrunnelse: String,
            journalpostId: List<String>,
            saksbehandlerIdent: String,
            vurdertTidspunkt: Instant,
        ) = Oppholdskravvurdering(
            id = id,
            fødselsnummer = fødselsnummer,
            behandlingId = behandlingId,
            dager = dager,
            svar = svar,
            vilkårsvurderinger = vilkårsvurderinger,
            utfall = utfall,
            fritekstbegrunnelse = fritekstbegrunnelse,
            journalpostId = journalpostId,
            saksbehandlerIdent = saksbehandlerIdent,
            vurdertTidspunkt = vurdertTidspunkt,
        )
    }
}

private val SvarPåOppholdskrav.førsteLeddOppfylt get() = oppholdIGodkjentOmråde

private val SvarPåOppholdskrav.vurdererTredjeLedd get() = !førsteLeddOppfylt && unntakAndreLedd == UnntakAndreLedd.INGEN

private fun SvarPåOppholdskrav.ugyldighet(): UgyldigOppholdskravvurdering? =
    when {
        !førsteLeddOppfylt && unntakAndreLedd == null -> UgyldigOppholdskravvurdering.UNNTAK_ANDRE_LEDD_MANGLER
        vurdererTredjeLedd && godkjentOppholdInntilFireUker == null ->
            UgyldigOppholdskravvurdering.GODKJENT_OPPHOLD_INNTIL_FIRE_UKER_MANGLER
        else -> null
    }

// Svar på spørsmål som ikke ble stilt, ville gitt et misvisende bilde av vurderingen i historikken
private fun SvarPåOppholdskrav.utenUbrukteSvar() =
    copy(
        unntakAndreLedd = unntakAndreLedd.takeUnless { førsteLeddOppfylt },
        godkjentOppholdInntilFireUker = godkjentOppholdInntilFireUker.takeIf { vurdererTredjeLedd },
    )

// Kalles bare etter at svarene er validert, så svarene som trengs for hvert ledd er satt
private fun SvarPåOppholdskrav.vilkårsvurderinger(): List<Vilkårsvurdering> =
    buildList {
        val førsteLedd =
            if (eøsBorger) Vilkårskode.OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND else Vilkårskode.OPPHOLD_I_NORGE_IKKE_EØS_BORGER
        add(vilkårsvurdering(førsteLedd, førsteLeddOppfylt))
        if (førsteLeddOppfylt) return@buildList

        when (checkNotNull(unntakAndreLedd)) {
            UnntakAndreLedd.MEDLEM_ETTER_2_5_2_6_2_8 -> {
                add(vilkårsvurdering(Vilkårskode.UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8, oppfylt = true))
                return@buildList
            }

            UnntakAndreLedd.INNLAGT_I_HELSEINSTITUSJON -> {
                add(vilkårsvurdering(Vilkårskode.UNNTAK_INNLAGT_HELSEINSTITUSJON, oppfylt = true))
                return@buildList
            }

            UnntakAndreLedd.INGEN -> {
                add(vilkårsvurdering(Vilkårskode.UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8, oppfylt = false))
                add(vilkårsvurdering(Vilkårskode.UNNTAK_INNLAGT_HELSEINSTITUSJON, oppfylt = false))
            }
        }

        add(vilkårsvurdering(Vilkårskode.UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER, checkNotNull(godkjentOppholdInntilFireUker)))
    }

private fun vilkårsvurdering(
    vilkårskode: Vilkårskode,
    oppfylt: Boolean,
) = Vilkårsvurdering(
    vilkårskode = vilkårskode,
    utfall = if (oppfylt) Utfall.Oppfylt else Utfall.IkkeOppfylt,
    lovreferanse = vilkårskode.lovreferanse,
)

// Første ledd og unntakene i andre og tredje ledd er alternative grunnlag: ett oppfylt holder
private fun List<Vilkårsvurdering>.samletUtfall() = if (any { it.utfall == Utfall.Oppfylt }) Utfall.Oppfylt else Utfall.IkkeOppfylt
