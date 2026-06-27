# Building & Releasing

## Building locally

Requires Git and Maven. JDK 21 builds the 1.21 line; JDK 25 builds the 26 line.

```bash
# 26.x build (default, needs JDK 25)
mvn clean package

# 1.21.x build (needs JDK 21+)
mvn clean package -Dpaper.version=1.21.4-R0.1-SNAPSHOT \
                  -Dmaven.compiler.release=21 \
                  -Dapi.version=1.21 -Dtarget.id=mc1.21 \
                  -Dmc.range="1.21.4 - 1.21.x"
```

Jars are produced in `target/` as `connection-verify-<version>-<target>.jar`.

## Continuous integration

The [`Build`](https://github.com/neramc/connection-verify/blob/main/.github/workflows/build.yml)
workflow runs on every push and pull request:

1. **validate** — checks `plugin.yml`, `config.yml` and `lang/*.yml` are valid.
2. **build** — a matrix builds both targets (`mc1.21` on JDK 21, `mc26` on JDK 25)
   and uploads each jar as an artifact.
3. **release** *(main only)* — publishes a GitHub Release `v<version>` with both
   jars, and (optionally) to Modrinth.

The [`Publish Wiki`](https://github.com/neramc/connection-verify/blob/main/.github/workflows/wiki.yml)
workflow mirrors the `wiki/` folder to this GitHub Wiki on every push to `main`.

## Modrinth auto-deploy

Releases can be published to Modrinth automatically. It is **opt-in**: nothing
is sent to Modrinth unless you add a token secret.

### One-time setup

1. **Create the project** on Modrinth (type: *Plugin*, loader: *Paper*). Set its
   **slug** to `connection-verify` (or update the slug in `build.yml`).
2. **Create a token:** Modrinth → *Settings → PATs* → create a token with the
   **Create versions** scope (add **Write projects** if you also want the
   description synced).
3. **Add the secret:** GitHub repo → *Settings → Secrets and variables →
   Actions → New repository secret*:
   - Name: `MODRINTH_TOKEN`
   - Value: the token from step 2.

That's it. On the next push to `main`:

- the built jars are uploaded as a new Modrinth version, and
- the project description is synced from
  [`resources/modrinth.md`](https://github.com/neramc/connection-verify/blob/main/resources/modrinth.md).

If `MODRINTH_TOKEN` is not set, both steps are skipped and only the GitHub
Release is created.

### Adjusting game versions

Edit the `game-versions` list in the *Publish to Modrinth* step of `build.yml`
to match the Minecraft versions you support.

## Cutting a new release

1. Bump `<version>` in `pom.xml` on `main`.
2. Push to `main`. CI builds both jars, creates `v<version>` on GitHub, and (if
   configured) publishes to Modrinth.

> Pushing the same version again refreshes that release/version in place.
