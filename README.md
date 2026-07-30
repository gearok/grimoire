# Grimoire

Grimoire is a small, searchable D&D 5e spell and monster reference. It is built with
Kotlin, Spring Boot, Thymeleaf, HTMX, and Elasticsearch.

## Run with Docker

Pull the latest published image from [GitHub Container Registry][container-package] and
start it with Elasticsearch using Docker Compose:

```bash
docker compose pull
docker compose up -d
```

Open <http://localhost:8080>.

The Compose file uses `ghcr.io/gearok/grimoire:latest` by default. To run a specific
published version, set `GRIMOIRE_IMAGE` to its package tag:

```bash
GRIMOIRE_IMAGE=ghcr.io/gearok/grimoire:0.0.3 docker compose up -d
```

To build and run the image yourself:

```bash
./gradlew bootJar
docker build -t grimoire:local .
GRIMOIRE_IMAGE=grimoire:local docker compose up -d
```

Set `ELASTICSEARCH_PASSWORD` when starting Compose to use a password other than the local
default.

## Run the scrapers

The scrapers require JDK 26+ and a running Elasticsearch instance. Download
`grimoire-scraper.jar` from the [latest release assets][latest-release]:

```bash
curl -LO https://github.com/HermanShpryhau/grimoire/releases/latest/download/grimoire-scraper.jar
```

Import spells and monsters:

```bash
java -jar grimoire-scraper.jar
```

To import only spells:

```bash
java -jar grimoire-scraper.jar --content=spells
```

To import only monsters:

```bash
java -jar grimoire-scraper.jar --content=monsters
```

Combined runs use `spells-v1` and `monsters-v1` by default. Override them independently
with `--spells-index=NAME` and `--monsters-index=NAME`.

Detail pages are fetched asynchronously with up to four concurrent requests. Use
`--concurrency=N` to change that limit. `--delay-ms=N` enforces a global minimum interval
between request starts, including retries, so concurrent workers cannot produce an
unbounded burst.

Each run imports every official entry found in the sitemap and clears the destination
index before importing.

To see all scraper options run:

```bash
java -jar grimoire-scraper.jar --help
```

The most common settings can also be supplied through `ELASTICSEARCH_URL`,
`ELASTICSEARCH_INDEX`, `ELASTICSEARCH_MONSTERS_INDEX`, `SCRAPER_DELAY_MS`,
`SCRAPER_CONCURRENCY`, and `SCRAPER_BATCH_SIZE`.

[container-package]: https://github.com/gearok/grimoire/pkgs/container/grimoire
[latest-release]: https://github.com/HermanShpryhau/grimoire/releases/latest
