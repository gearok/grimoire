# Grimoire

Grimoire is a small, searchable D&D 5e spell and monster reference. It is built with
Kotlin, Spring Boot, Thymeleaf, HTMX, and Elasticsearch.

## Run with Docker

Start the published application image and Elasticsearch with Docker Compose:

```bash
docker compose up -d
```

Open <http://localhost:8080>.

To build and run the image yourself, use the local Compose file. It builds the
application from source in a multi-stage Docker build, so no local JDK or Gradle
installation is required:

```bash
docker compose -f docker-compose.local.yml up -d --build
```

You can also build the image directly:

```bash
docker build -t grimoire:local .
GRIMOIRE_IMAGE=grimoire:local docker compose up -d
```

Set `ELASTICSEARCH_PASSWORD` when starting Compose to use a password other than the local
default.

## Run the scrapers

The scrapers require JDK 26+ and a running Elasticsearch instance. Import spells with:

```bash
./gradlew :scraper:run
```

Import monsters with:

```bash
./gradlew :scraper:run --args='--content=monsters'
```

Import spells and monsters in one run with:

```bash
./gradlew :scraper:run --args='--content=spells,monsters'
```

Combined runs use `spells-v1` and `monsters-v1` by default. Override them independently
with `--spells-index=NAME` and `--monsters-index=NAME`.

Detail pages are fetched asynchronously with up to four concurrent requests. Use
`--concurrency=N` to change that limit. `--delay-ms=N` enforces a global minimum interval
between request starts, including retries, so concurrent workers cannot produce an
unbounded burst.

Each run clears the destination index before importing. Use a disposable index for a
limited test:

```bash
./gradlew :scraper:run --args='--limit=10 --index=spells-scraper-test'
./gradlew :scraper:run --args='--content=monsters --limit=10 --index=monsters-scraper-test'
```

Run `./gradlew :scraper:run --args='--help'` to see all scraper options. The most common
settings can also be supplied through `ELASTICSEARCH_URL`, `ELASTICSEARCH_INDEX`,
`ELASTICSEARCH_MONSTERS_INDEX`, `SCRAPER_DELAY_MS`, `SCRAPER_CONCURRENCY`, and
`SCRAPER_BATCH_SIZE`.
