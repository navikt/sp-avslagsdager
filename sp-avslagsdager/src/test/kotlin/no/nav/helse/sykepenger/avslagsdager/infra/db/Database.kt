package no.nav.helse.sykepenger.avslagsdager.infra.db

import no.nav.sykepenger.libs.postgres.TestDatabase

internal object Database {
    private val database = TestDatabase.start(postgresImage = "postgres:18").also { it.migrer() }

    val transaksjonProvider = PostgresTransaksjonProvider(database.dataSource)

    fun tøm() =
        database.tøm(
            "oppholdskravvurdering_vilkarsvurdering",
            "oppholdskravvurdering_dag",
            "oppholdskravvurdering",
            "outbox",
        )

    fun antallRader(tabell: String) = database.antallRader(tabell)

    fun meldingstyperIOutbox(): List<String> =
        database.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT melding ->> 'type' AS type FROM outbox ORDER BY sekvensnummer").use { rs ->
                    generateSequence { if (rs.next()) rs.getString("type") else null }.toList()
                }
            }
        }
}
