plugins {
    id("no.nav.sykepenger.deployable")
    // Kreves av ktors Resources-plugin for å generere serializers for @Resource-klassene
    alias(libs.plugins.kotlin.serialization)
}

sykepengerDeployable {
    mainClass = "no.nav.helse.sykepenger.avslagsdager.bootstrap.AppKt"
}

dependencies {
    implementation(libs.kotliquery)
    implementation(libs.cloud.sql.postgres.socket.factory)
    implementation(libs.rapids.and.rivers)
    implementation(libs.sykepengerLibs.ktor.auth)
    implementation(libs.sykepengerLibs.logging)
    implementation(libs.sykepengerLibs.postgres)
    implementation(libs.sykepengerLibs.leader.election)
    implementation(libs.sykepengerLibs.outbox)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.jackson3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.smiley4.ktor.openapi.tools)
    implementation(libs.tbd.libs.access.token.provider.texas)
    implementation(libs.tbd.libs.azure.token.client.default)
    implementation(libs.tbd.libs.person.pseudo.id)
    implementation(libs.tbd.libs.populasjonstilgangskontroll.provider.api)
    implementation(libs.tbd.libs.populasjonstilgangskontroll.provider.tilgangsmaskinen)
    implementation(project(":migreringer"))

    testImplementation(libs.tbd.libs.mock.http.client)
    testImplementation(libs.tbd.libs.rapids.and.rivers.test)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(testFixtures(libs.sykepengerLibs.postgres))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mock.oauth2.server)
}
