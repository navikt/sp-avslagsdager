package no.nav.helse.sykepenger.avslagsdager.domain

import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd.INGEN
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd.INNLAGT_I_HELSEINSTITUSJON
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd.MEDLEM_ETTER_2_5_2_6_2_8
import no.nav.helse.sykepenger.avslagsdager.domain.Utfall.IkkeOppfylt
import no.nav.helse.sykepenger.avslagsdager.domain.Utfall.Oppfylt
import no.nav.helse.sykepenger.avslagsdager.domain.Vilkårskode.OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND
import no.nav.helse.sykepenger.avslagsdager.domain.Vilkårskode.OPPHOLD_I_NORGE_IKKE_EØS_BORGER
import no.nav.helse.sykepenger.avslagsdager.domain.Vilkårskode.UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER
import no.nav.helse.sykepenger.avslagsdager.domain.Vilkårskode.UNNTAK_INNLAGT_HELSEINSTITUSJON
import no.nav.helse.sykepenger.avslagsdager.domain.Vilkårskode.UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class OppholdskravvurderingTest {
    @Test
    fun `opphold i godkjent område gir første ledd oppfylt og stopper vurderingen`() {
        // When
        val vurdering = vurdert(svar(eøsBorger = true, oppholdIGodkjentOmråde = true))

        // Then
        assertEquals(listOf(OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND to Oppfylt), vurdering.koderOgUtfall())
        assertEquals(Oppfylt, vurdering.utfall)
    }

    @Test
    fun `den som ikke er EØS-borger vurderes etter andre setning i første ledd`() {
        // When
        val vurdering = vurdert(svar(eøsBorger = false, oppholdIGodkjentOmråde = true))

        // Then
        assertEquals(listOf(OPPHOLD_I_NORGE_IKKE_EØS_BORGER to Oppfylt), vurdering.koderOgUtfall())
        assertEquals(Oppfylt, vurdering.utfall)
    }

    @Test
    fun `medlem etter 2-5, 2-6 eller 2-8 gir unntaket i bokstav a oppfylt og stopper vurderingen`() {
        // When
        val vurdering =
            vurdert(svar(eøsBorger = true, oppholdIGodkjentOmråde = false, unntakAndreLedd = MEDLEM_ETTER_2_5_2_6_2_8))

        // Then
        assertEquals(
            listOf(OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND to IkkeOppfylt, UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8 to Oppfylt),
            vurdering.koderOgUtfall(),
        )
        assertEquals(Oppfylt, vurdering.utfall)
    }

    @Test
    fun `innlagt i helseinstitusjon gir unntaket i bokstav b oppfylt og stopper vurderingen`() {
        // When
        val vurdering =
            vurdert(svar(eøsBorger = false, oppholdIGodkjentOmråde = false, unntakAndreLedd = INNLAGT_I_HELSEINSTITUSJON))

        // Then
        assertEquals(
            listOf(OPPHOLD_I_NORGE_IKKE_EØS_BORGER to IkkeOppfylt, UNNTAK_INNLAGT_HELSEINSTITUSJON to Oppfylt),
            vurdering.koderOgUtfall(),
        )
        assertEquals(Oppfylt, vurdering.utfall)
    }

    @Test
    fun `godkjent opphold i inntil fire uker gir tredje ledd oppfylt`() {
        // When
        val vurdering =
            vurdert(
                svar(
                    eøsBorger = true,
                    oppholdIGodkjentOmråde = false,
                    unntakAndreLedd = INGEN,
                    godkjentOppholdInntilFireUker = true,
                ),
            )

        // Then
        assertEquals(
            listOf(
                OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND to IkkeOppfylt,
                UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8 to IkkeOppfylt,
                UNNTAK_INNLAGT_HELSEINSTITUSJON to IkkeOppfylt,
                UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER to Oppfylt,
            ),
            vurdering.koderOgUtfall(),
        )
        assertEquals(Oppfylt, vurdering.utfall)
    }

    @Test
    fun `ingen unntak og ikke godkjent opphold gir avslag`() {
        // When
        val vurdering =
            vurdert(
                svar(
                    eøsBorger = true,
                    oppholdIGodkjentOmråde = false,
                    unntakAndreLedd = INGEN,
                    godkjentOppholdInntilFireUker = false,
                ),
            )

        // Then
        assertEquals(
            listOf(
                OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND to IkkeOppfylt,
                UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8 to IkkeOppfylt,
                UNNTAK_INNLAGT_HELSEINSTITUSJON to IkkeOppfylt,
                UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER to IkkeOppfylt,
            ),
            vurdering.koderOgUtfall(),
        )
        assertEquals(IkkeOppfylt, vurdering.utfall)
    }

    @Test
    fun `svar som ikke ble brukt i vurderingen lagres ikke`() {
        // When
        val vurdering =
            vurdert(
                svar(
                    eøsBorger = true,
                    oppholdIGodkjentOmråde = true,
                    unntakAndreLedd = INGEN,
                    godkjentOppholdInntilFireUker = false,
                ),
            )

        // Then
        assertNull(vurdering.svar.unntakAndreLedd)
        assertNull(vurdering.svar.godkjentOppholdInntilFireUker)
    }

    @Test
    fun `godkjent opphold lagres ikke når et unntak i andre ledd er oppfylt`() {
        // When
        val vurdering =
            vurdert(
                svar(
                    eøsBorger = true,
                    oppholdIGodkjentOmråde = false,
                    unntakAndreLedd = MEDLEM_ETTER_2_5_2_6_2_8,
                    godkjentOppholdInntilFireUker = true,
                ),
            )

        // Then
        assertEquals(MEDLEM_ETTER_2_5_2_6_2_8, vurdering.svar.unntakAndreLedd)
        assertNull(vurdering.svar.godkjentOppholdInntilFireUker)
    }

    @Test
    fun `vilkårsvurderingene har lovreferansen til vilkårskoden`() {
        // When
        val vurdering =
            vurdert(
                svar(
                    eøsBorger = true,
                    oppholdIGodkjentOmråde = false,
                    unntakAndreLedd = INGEN,
                    godkjentOppholdInntilFireUker = false,
                ),
            )

        // Then
        vurdering.vilkårsvurderinger.forEach { assertEquals(it.vilkårskode.lovreferanse, it.lovreferanse) }
        assertEquals(
            Lovreferanse(
                lov = "folketrygdloven",
                paragraf = "8-9",
                avsnitt = 2,
                setning = null,
                bokstav = "b",
                iKraftFra = LocalDate.of(2021, 6, 1),
            ),
            UNNTAK_INNLAGT_HELSEINSTITUSJON.lovreferanse,
        )
    }

    @Test
    fun `dagene sorteres og dupliserte dager fjernes`() {
        // When
        val vurdering =
            vurdert(
                svar(eøsBorger = true, oppholdIGodkjentOmråde = true),
                dager = listOf(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6)),
            )

        // Then
        assertEquals(listOf(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6)), vurdering.dager)
    }

    @Test
    fun `vurderingen tar vare på hvem som vurderte, når og hvorfor`() {
        // Given
        val tidspunkt = Instant.parse("2026-01-10T10:00:00Z")
        val behandlingId = UUID.randomUUID()

        // When
        val vurdering =
            vurdert(
                svar(eøsBorger = true, oppholdIGodkjentOmråde = true),
                behandlingId = behandlingId,
                fritekstbegrunnelse = "Begrunnelse",
                journalpostId = listOf("123"),
                vurdertTidspunkt = tidspunkt,
            )

        // Then
        assertEquals("12345678901", vurdering.fødselsnummer)
        assertEquals(behandlingId, vurdering.behandlingId)
        assertEquals("Z999999", vurdering.saksbehandlerIdent)
        assertEquals("Begrunnelse", vurdering.fritekstbegrunnelse)
        assertEquals(listOf("123"), vurdering.journalpostId)
        assertEquals(tidspunkt, vurdering.vurdertTidspunkt)
    }

    @Test
    fun `ingen dager er ugyldig`() {
        // When
        val ugyldighet = ugyldig(svar(eøsBorger = true, oppholdIGodkjentOmråde = true), dager = emptyList())

        // Then
        assertEquals(UgyldigOppholdskravvurdering.DAGER_MANGLER, ugyldighet)
    }

    @Test
    fun `tom fritekstbegrunnelse er ugyldig`() {
        // When
        val ugyldighet = ugyldig(svar(eøsBorger = true, oppholdIGodkjentOmråde = true), fritekstbegrunnelse = "  ")

        // Then
        assertEquals(UgyldigOppholdskravvurdering.FRITEKSTBEGRUNNELSE_MANGLER, ugyldighet)
    }

    @Test
    fun `manglende unntak i andre ledd er ugyldig når første ledd ikke er oppfylt`() {
        // When
        val ugyldighet = ugyldig(svar(eøsBorger = true, oppholdIGodkjentOmråde = false))

        // Then
        assertEquals(UgyldigOppholdskravvurdering.UNNTAK_ANDRE_LEDD_MANGLER, ugyldighet)
    }

    @Test
    fun `manglende svar på godkjent opphold er ugyldig når det ikke er noe unntak i andre ledd`() {
        // When
        val ugyldighet = ugyldig(svar(eøsBorger = true, oppholdIGodkjentOmråde = false, unntakAndreLedd = INGEN))

        // Then
        assertEquals(UgyldigOppholdskravvurdering.GODKJENT_OPPHOLD_INNTIL_FIRE_UKER_MANGLER, ugyldighet)
    }

    @Test
    fun `en vurdering som ikke er oppfylt, gir én avslagsdag per dag med vilkårskodene som ikke er oppfylt`() {
        // Given
        val vurdering =
            vurdert(
                svar(eøsBorger = false, oppholdIGodkjentOmråde = false, unntakAndreLedd = INGEN, godkjentOppholdInntilFireUker = false),
                dager = listOf(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 5)),
            )

        // When
        val avslagsdager = vurdering.dager.mapNotNull(vurdering::avslagsdag)

        // Then
        val vilkårskoder =
            listOf(
                OPPHOLD_I_NORGE_IKKE_EØS_BORGER,
                UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8,
                UNNTAK_INNLAGT_HELSEINSTITUSJON,
                UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER,
            )
        assertEquals(
            listOf(
                Avslagsdag(vurdering.id, LocalDate.of(2026, 1, 5), vilkårskoder),
                Avslagsdag(vurdering.id, LocalDate.of(2026, 1, 6), vilkårskoder),
            ),
            avslagsdager,
        )
    }

    @Test
    fun `en oppfylt vurdering gir ingen avslagsdager, selv om noen vilkår ikke er oppfylt`() {
        // Given
        val vurdering =
            vurdert(svar(eøsBorger = true, oppholdIGodkjentOmråde = false, unntakAndreLedd = MEDLEM_ETTER_2_5_2_6_2_8))

        // When / Then
        assertEquals(emptyList<Avslagsdag>(), vurdering.dager.mapNotNull(vurdering::avslagsdag))
    }

    private fun Oppholdskravvurdering.koderOgUtfall() = vilkårsvurderinger.map { it.vilkårskode to it.utfall }

    private fun svar(
        eøsBorger: Boolean,
        oppholdIGodkjentOmråde: Boolean,
        unntakAndreLedd: UnntakAndreLedd? = null,
        godkjentOppholdInntilFireUker: Boolean? = null,
    ) = SvarPåOppholdskrav(eøsBorger, oppholdIGodkjentOmråde, unntakAndreLedd, godkjentOppholdInntilFireUker)

    private fun vurder(
        svar: SvarPåOppholdskrav,
        dager: List<LocalDate> = listOf(LocalDate.of(2026, 1, 5)),
        behandlingId: UUID = UUID.randomUUID(),
        fritekstbegrunnelse: String = "Begrunnelse",
        journalpostId: List<String> = emptyList(),
        vurdertTidspunkt: Instant = Instant.now(),
    ) = Oppholdskravvurdering.vurder(
        fødselsnummer = "12345678901",
        behandlingId = behandlingId,
        dager = dager,
        svar = svar,
        fritekstbegrunnelse = fritekstbegrunnelse,
        journalpostId = journalpostId,
        saksbehandlerIdent = "Z999999",
        vurdertTidspunkt = vurdertTidspunkt,
    )

    private fun vurdert(
        svar: SvarPåOppholdskrav,
        dager: List<LocalDate> = listOf(LocalDate.of(2026, 1, 5)),
        behandlingId: UUID = UUID.randomUUID(),
        fritekstbegrunnelse: String = "Begrunnelse",
        journalpostId: List<String> = emptyList(),
        vurdertTidspunkt: Instant = Instant.now(),
    ): Oppholdskravvurdering {
        val resultat = vurder(svar, dager, behandlingId, fritekstbegrunnelse, journalpostId, vurdertTidspunkt)
        return assertInstanceOf(OppholdskravvurderingResultat.Vurdert::class.java, resultat).vurdering
    }

    private fun ugyldig(
        svar: SvarPåOppholdskrav,
        dager: List<LocalDate> = listOf(LocalDate.of(2026, 1, 5)),
        fritekstbegrunnelse: String = "Begrunnelse",
    ): UgyldigOppholdskravvurdering {
        val resultat = vurder(svar, dager, fritekstbegrunnelse = fritekstbegrunnelse)
        return assertInstanceOf(OppholdskravvurderingResultat.Ugyldig::class.java, resultat).ugyldighet
    }
}
