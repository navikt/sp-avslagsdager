# sp-avslagsdager

Domeneapp for avslagsdager i sykepenger. Appen er foreløpig et skjelett uten domene: den har rapid, database, REST-oppsett for Speil og en klient mot spleis-api.

## REST-API

Appen bruker REST-rammeverket fra sp-vilkarsproving (`rammeverk/rest`). Endepunktene registreres i `endepunkter()` i `bootstrap/App.kt` og ligger bak Azure AD-autentisering.

Kallene krever et Azure AD-token med appen som audience og claimene `NAVident` og `oid`. Gruppene i `TILGANG_LES` og `TILGANG_SKRIV` gir tilgang. Begge variablene tar kommaseparerte gruppe-ID-er. Appen slår opp pseudo-ID-en i Valkey-instansen `personpseudoid` og kontrollerer kjernetilgang gjennom tilgangsmaskinen med saksbehandlerens token, vekslet av Texas.

Med `EKSPONER_OPENAPI=true` eksponerer appen `/api/openapi.json` og Swagger UI på `/api/swagger`.

Bygg og kjør testene:

```shell
./gradlew build -x addKtlintFormatGitPreCommitHook
```

## Moduler

- `sp-avslagsdager`: selve appen.
- `migreringer`: Flyway-migreringer for databasen.
- `opprydding-dev`: svarer på `slett_person` på rapiden i dev. Den sletter ingenting før appen har tabeller med persondata.

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For Nav-ansatte

Interne henvendelser kan sendes via Slack i
kanalen [#sykepenger-værsågod](https://nav-it.slack.com/archives/C019637N90X).

## Kode generert av GitHub Copilot

Dette repoet bruker GitHub Copilot til å generere kode.
