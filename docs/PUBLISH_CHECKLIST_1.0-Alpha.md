# Flylang 1.0-Alpha — Publish Checklist

This checklist guides packaging and publishing Flylang 1.0-Alpha across artifacts and channels.

## 0) Preconditions
- [ ] Git working tree clean, CI green on main/default branch
- [ ] Version set to 1.0-Alpha across all modules (compiler, runtime, stdlib, CLI, REPL, LSP, Maven plugin, examples)
- [ ] Docs updated (README, guides) and no stale 0.4.0 references

## 1) Build and Verify
- [ ] Full build: `mvn -q -DskipTests clean install`
- [ ] Run all tests: `./scripts/test.sh --all` (or interactive: `./scripts/test.sh`)
- [ ] Manually run Spring Boot demo (optional):
  - `cd examples/spring-boot-demo && mvn clean compile && mvn spring-boot:run`
  - Verify GET /hello, GET /users/{id}?greet=Name, POST /users

## 2) Tag and Release Draft
- [ ] Create tag: `git tag -a v1.0-Alpha -m "Flylang 1.0-Alpha" && git push origin v1.0-Alpha`
- [ ] Prepare release notes from docs/RELEASE_NOTES_1.0-Alpha.md

## 3) Publish Maven Artifacts
Note: Ensure credentials are configured for your repository host (e.g., OSSRH, internal Nexus/Artifactory).
- [ ] Dry-run (optional): `mvn -DskipTests -Prelease -Dgpg.skip=true deploy`
- [ ] Publish: `mvn -DskipTests -Prelease deploy`
- Artifacts to verify:
  - firefly-compiler, firefly-runtime, firefly-stdlib
  - firefly-cli, firefly-repl, firefly-lsp
  - firefly-maven-plugin

## 4) VS Code Extension
- [ ] Build: `cd ide-plugins/vscode-firefly && npm ci && npm run compile`
- [ ] Package: `vsce package` (produces .vsix)
- [ ] Publish (optional): `vsce publish` (requires marketplace token)
- [ ] Update local install (optional dev flow): `./install.sh` or `code --install-extension *.vsix`

## 5) IntelliJ Plugin
- [ ] Build: `cd ide-plugins/intellij-firefly && ./gradlew buildPlugin`
- [ ] Verify artifact in `build/distributions/*.zip`
- [ ] Publish (optional): `./gradlew publishPlugin` (requires JetBrains token)

## 6) GitHub Release
- [ ] Create GitHub release for tag v1.0-Alpha
- [ ] Attach artifacts (optional):
  - LSP jar: `firefly-lsp/target/firefly-lsp.jar`
  - VSIX: `ide-plugins/vscode-firefly/*.vsix`
  - IntelliJ plugin zip: `ide-plugins/intellij-firefly/build/distributions/*.zip`
- [ ] Paste contents of docs/RELEASE_NOTES_1.0-Alpha.md

## 7) Final Validation
- [ ] Fresh clone build: `git clone ... && mvn -q -DskipTests clean install`
- [ ] Validate plugin coordinates in a sample project:
  ```xml
  <plugin>
    <groupId>com.firefly</groupId>
    <artifactId>firefly-maven-plugin</artifactId>
    <version>1.0-Alpha</version>
    <executions><execution><goals><goal>compile</goal></goals></execution></executions>
  </plugin>
  ```
- [ ] Run a couple of examples via `fly run examples/<name>`

## 8) Post-Release
- [ ] Bump versions to next cycle (e.g., 1.0-Alpha-SNAPSHOT) if desired
- [ ] Announce release internally/externally (link to release notes)
- [ ] Open issues for known limitations and follow-ups (parser static call args, collections in REST endpoints)
