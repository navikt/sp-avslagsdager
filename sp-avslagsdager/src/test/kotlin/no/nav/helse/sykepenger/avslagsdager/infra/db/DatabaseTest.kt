package no.nav.helse.sykepenger.avslagsdager.infra.db

import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import org.junit.jupiter.api.BeforeEach

internal abstract class DatabaseTest {
    @BeforeEach
    fun tømDatabasen() {
        Database.tøm()
    }

    protected fun <T> transaksjon(block: (Transaksjonskontekst) -> T): T = Database.transaksjonProvider.transaksjon(block)
}
