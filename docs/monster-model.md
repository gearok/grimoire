# Monster Elasticsearch model

Monster pages combine identity, filterable catalogue metadata, numeric combat statistics,
and long rules text. The `monsters-v1` document keeps those concerns separate:

- `name.ru`, `name.en`, and `aliases` are full-text fields with keyword subfields for
  deterministic sorting.
- `size`, `type`, `challenge.value`, languages, environments, immunities, and movement
  types are exact values suitable for filters and aggregations.
- armor class, hit points, ability scores, modifiers, movement distance, challenge rating,
  experience, and proficiency are numeric.
- traits, actions, reactions, legendary actions, and similar blocks are stored as ordered
  `sections` with named entries. Their names and text participate in full-text search.
- `description` is searched at a lower boost than names.
- `sourceUrl` is retained for attribution but is not indexed.

Name search uses the same ranking strategy as spells: high-boost boolean-prefix matching,
fuzzy bilingual name matching, then lower-boost rules-text matching. Browse results sort by
challenge rating and Russian name; text results put `_score` first. Values inside one facet
are ORed, and different facets are combined with AND.

The mapping is strict so scraper/model drift fails during import instead of silently
creating unintended Elasticsearch fields.
