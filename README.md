# Grimoire

Grimoire is a small, searchable D&D 5e spell and monster reference. It is built with
Kotlin, Spring Boot, Thymeleaf, HTMX, and Elasticsearch.

## Run with Docker

Start Elasticsearch with Docker Compose:

```bash
docker compose up -d
```

Then run the published application image:

```bash
docker run --rm \
  --name grimoire \
  --add-host=host.docker.internal:host-gateway \
  -p 8080:8080 \
  -e ELASTICSEARCH_URL=http://host.docker.internal:9200 \
  -e ELASTICSEARCH_USERNAME=elastic \
  -e ELASTICSEARCH_PASSWORD=grimoire-local-password \
  ghcr.io/hermanshpryhau/grimoire:edge
```

Open <http://localhost:8080>.

To build and run the image yourself, replace the image in the command above with
`grimoire:local` after building it:

```bash
./gradlew bootJar
docker build -t grimoire:local .
```

Set `ELASTICSEARCH_PASSWORD` for both Compose and the application container to use a
password other than the local default.

## Run the scrapers

The scrapers require JDK 17+ and a running Elasticsearch instance. Import spells with:

```bash
./gradlew :scraper:run
```

Import monsters with:

```bash
./gradlew :scraper:run --args='--content=monsters'
```

Each run clears the destination index before importing. Use a disposable index for a
limited test:

```bash
./gradlew :scraper:run --args='--limit=10 --index=spells-scraper-test'
./gradlew :scraper:run --args='--content=monsters --limit=10 --index=monsters-scraper-test'
```

Run `./gradlew :scraper:run --args='--help'` to see all scraper options. The most common
settings can also be supplied through `ELASTICSEARCH_URL`, `ELASTICSEARCH_INDEX`,
`ELASTICSEARCH_MONSTERS_INDEX`, `SCRAPER_DELAY_MS`, and `SCRAPER_BATCH_SIZE`.
