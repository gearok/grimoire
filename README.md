# Grimoire

A small D&D 5e spell reference built with Kotlin, Spring Boot, Thymeleaf, HTMX,
and Elasticsearch.

The interface uses [The Monospace Web](https://owickstrom.github.io/the-monospace-web/)
by Oskar Wickström, distributed under the MIT License. Its pinned base styles are loaded
before the app-specific stylesheet.

## What is included

- Spring Boot web application with Spring MVC HTML and JSON routes
- server-rendered Thymeleaf templates in `src/main/resources/templates`, enhanced by HTMX
- read-only Spring Data Elasticsearch access for spell lookup and search
- bilingual fuzzy and type-ahead name search, lower-weight rules-text search, and exact filters
- responsive spell index and detail pages
- route tests using an in-memory repository

The application intentionally does not scrape or republish the source site. It provides a
data model and an ingestion endpoint so that data can be loaded from a source you are
permitted to use.

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

The application treats Elasticsearch as read-only. It does not create the index, install a
mapping, seed documents, or expose a write endpoint. Provision and populate the configured
index before starting the application, then visit <http://localhost:8080/spells>.

## HTTP surface

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/spells` | HTML search (`q`, `level`, `school`, `class`, `page`) |
| `GET` | `/spells/{id}` | HTML spell detail |
| `GET` | `/api/spells/{id}` | retrieve a spell as JSON |

`level`, `school`, and `class` may be repeated to select multiple values, for example
`/spells?level=1&level=3&school=evocation&school=enchantment`. Values within one
category are ORed; different categories are ANDed.

Requests to `/spells` with `HX-Request: true` receive only the result fragment. The same
route therefore remains usable without JavaScript.

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

## Verification

```bash
./gradlew test
./gradlew build
```
