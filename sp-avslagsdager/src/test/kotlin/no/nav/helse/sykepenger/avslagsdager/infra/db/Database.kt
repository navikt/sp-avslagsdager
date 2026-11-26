package no.nav.helse.sykepenger.avslagsdager.infra.db

import no.nav.sykepenger.libs.postgres.TestDatabase

internal object Database {
    private val database = TestDatabase.start(postgresImage = "postgres:18").also { it.migrer() }

    val dataSource = database.dataSource
    val transaksjonProvider = PostgresTransaksjonProvider(dataSource)

    fun tøm() =
        database.tøm(
            "oppholdskravvurdering_vilkarsvurdering",
            "oppholdskravvurdering_dag",
            "oppholdskravvurdering",
            "outbox",
            "oppgitt_utlandsopphold",
            "grunnlag_for_lopende_vilkar",
        )

    fun antallRader(tabell: String) = database.antallRader(tabell)
}
