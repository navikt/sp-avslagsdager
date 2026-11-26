# sp-avslagsdager

Domeneapp for avslagsdager i sykepenger. Saksbehandler vurderer oppholdskravet (folketrygdloven § 8-9) for enkeltdager fra Speil, og appen lagrer vurderingene.

Appen svarer på behovet `Avslagsdager` fra Spleis med avslagsdagene i beregningsperioden og et varsel for hver yrkesaktivitet som har dager der oppholdskravet må vurderes. Den henter søknadene bak behovet fra Spedisjon og lagrer oppholdene utenfor EØS som søknadene oppgir. Hvert svar lagres som et grunnlag for løpende vilkår.

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
- `opprydding-dev`: svarer på `slett_person` på rapiden i dev og sletter dataene appen har lagret om personen.

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For Nav-ansatte

Interne henvendelser kan sendes via Slack i
kanalen [#sykepenger-værsågod](https://nav-it.slack.com/archives/C019637N90X).

## Kode generert av GitHub Copilot

Dette repoet bruker GitHub Copilot til å generere kode.
