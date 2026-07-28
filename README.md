# Grimoire

A small D&D 5e spell and monster reference built with Kotlin, Spring Boot, Thymeleaf, HTMX,
and Elasticsearch.

The interface uses [The Monospace Web](https://owickstrom.github.io/the-monospace-web/)
by Oskar Wickström, distributed under the MIT License. Its pinned base styles are loaded
before the app-specific stylesheet.

## What is included

- Spring Boot web application with Spring MVC HTML and JSON routes
- server-rendered Thymeleaf templates in `src/main/resources/templates`, enhanced by HTMX
- read-only Spring Data Elasticsearch access for spell and monster lookup and search
- a separate sitemap-driven CLI importer in the `scraper` module
- bilingual fuzzy and type-ahead name search, lower-weight rules-text search, and exact filters
- responsive spell and monster index and detail pages
- route tests using an in-memory repository

Only import and republish source material you are permitted to use.

## Run locally

Requirements: JDK 17+ and Docker.

```bash
docker compose up -d
./gradlew bootRun
```

Open <http://localhost:8080>. Elasticsearch is exposed at <http://localhost:9200>.
The Compose service uses Elasticsearch 9.4.2 and a dedicated
`elasticsearch-data-v9` volume; an existing Elasticsearch 8 volume is left untouched.

The server reads these optional environment variables:

| Variable | Default |
| --- | --- |
| `PORT` | `8080` |
| `ELASTICSEARCH_URL` | `http://localhost:9200` |
| `ELASTICSEARCH_INDEX` | `spells-v1` |
| `ELASTICSEARCH_MONSTERS_INDEX` | `monsters-v1` |

The web application treats Elasticsearch as read-only and exposes no write endpoint. The
separate scraper creates the configured index and mapping when the index does not exist,
clears all existing documents, then bulk-indexes the selected content. Spring Data
repository auto-configuration is disabled in the main application.

## Import spells

The importer discovers official 2014-edition spell pages through the site's public sitemap;
it does not call the `/piece/` endpoint disallowed by the site's `robots.txt`. Start
Elasticsearch, then run:

```bash
docker compose up -d
./gradlew :scraper:run
```

The default delay is 500 ms between spell-page requests. Every run clears the destination
index after successfully discovering source URLs and before fetching the detail pages.
Consequently, `--limit` should only be used with a disposable test index. Useful options
include:

```bash
./gradlew :scraper:run --args='--limit=10 --index=spells-scraper-test'
./gradlew :scraper:run --args='--elasticsearch-url=http://localhost:9200 --index=spells-v1'
./gradlew :scraper:run --args='--help'
```

The importer shares `ELASTICSEARCH_URL` and `ELASTICSEARCH_INDEX` with the main app. It also
accepts `SCRAPER_DELAY_MS`, `SCRAPER_BATCH_SIZE`, `SCRAPER_USER_AGENT`, and
`DND_SU_SITEMAP_URL`.

## Import monsters

Monster import uses the same sitemap-driven importer and the dedicated `monsters-v1`
mapping:

```bash
./gradlew :scraper:run --args='--content=monsters'
```

For a non-production smoke test, use a disposable index:

```bash
./gradlew :scraper:run --args='--content=monsters --limit=10 --index=monsters-scraper-test'
```

The monster destination can be configured with `ELASTICSEARCH_MONSTERS_INDEX`.

## HTTP surface

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/spells` | HTML search (`q`, `level`, `school`, `class`, `page`) |
| `GET` | `/spells/{id}` | HTML spell detail |
| `GET` | `/api/spells/suggestions?q=...` | compact type-ahead spell-name suggestions |
| `GET` | `/api/spells/{id}` | retrieve a spell as JSON |
| `GET` | `/monsters` | HTML search (`q`, `size`, `type`, `challenge`, `page`) |
| `GET` | `/monsters/{id}` | HTML monster detail |
| `GET` | `/api/monsters/suggestions?q=...` | compact type-ahead monster-name suggestions |
| `GET` | `/api/monsters/{id}` | retrieve a monster as JSON |

`level`, `school`, and `class` may be repeated to select multiple values, for example
`/spells?level=1&level=3&school=evocation&school=enchantment`. Values within one
category are ORed; different categories are ANDed.

Requests to `/spells` with `HX-Request: true` receive only the result fragment. The same
route therefore remains usable without JavaScript.

Monster facets follow the same rule: repeated values are ORed within a facet, while
size, type, and challenge facets are ANDed together.

The HTML lives entirely in resource templates:

- `templates/layout.html` contains the shared Thymeleaf page layout
- `templates/fragments/multi-select.html` contains the reusable filter control
- `templates/spells/index.html` contains the search page and form
- `templates/spells/results.html` is both included in the full page and returned to HTMX
- `templates/spells/detail.html` contains the spell detail page

## Spell model

The model is in
[`Spell.kt`](src/main/kotlin/dev/shph/grimoire/model/Spell.kt). The reasoning and fields
observed on the source pages are recorded in
[`docs/spell-model.md`](docs/spell-model.md).

## Monster model

The application model is in
[`Monster.kt`](src/main/kotlin/dev/shph/grimoire/model/Monster.kt), and its strict
Elasticsearch mapping is in
[`monsters-index.json`](scraper/src/main/resources/monsters-index.json). Field and search
decisions are summarized in [`docs/monster-model.md`](docs/monster-model.md).

## Verification

```bash
./gradlew test
./gradlew build
```
