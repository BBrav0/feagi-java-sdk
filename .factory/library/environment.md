# Environment

Environment variables, external dependencies, and setup notes.

**What belongs here:** Required env vars, external API keys/services, dependency quirks, platform-specific notes.
**What does NOT belong here:** Service ports/commands (use `.factory/services.yaml`).

---

## Java Environment
- Java 17 LTS (Microsoft JDK 17.0.15 on dev machine)
- Gradle 9.2.0 (Kotlin DSL)
- Maven NOT installed locally — use `./gradlew` for all operations

## Gradle Properties for Publishing (CI only)
- `signing.gnupg.keyId` or `signingKeyId` — GPG key ID
- `signing.gnupg.passphrase` or `signingPassword` — GPG passphrase
- `signing.secretKeyRingFile` or in-memory key via `signingKey`
- `ossrhUsername` — Sonatype OSSRH username
- `ossrhPassword` — Sonatype OSSRH token

## GitHub Secrets (configured on upstream repo)
- `MAVEN_GPG_PRIVATE_KEY` — base64-encoded GPG private key
- `MAVEN_GPG_PASSPHRASE` — GPG key passphrase
- `OSSRH_USERNAME` — Sonatype username
- `OSSRH_TOKEN` — Sonatype token
