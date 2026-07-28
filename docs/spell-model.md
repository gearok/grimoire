# Spell data model

The model was derived by checking the dynamic index and representative D&D 5e spell
pages on 2026-07-28:

- [spell index](https://dnd.su/spells/)
- [Fireball](https://dnd.su/spells/205-fireball/) — ordinary leveled spell with
  material components and higher-level scaling
- [Aura of Vitality](https://dnd.su/spells/3-aura-of-vitality/) — concentration,
  optional class access, and subclass access
- [Mordenkainen's Magnificent Mansion](https://dnd.su/spells/16-mordenkainens-magnificent-mansion/)
  — a long material description and multi-paragraph rules text

The list is loaded by the source site from `/piece/spells/index-list/`. It adds useful
search facets that are not all visible as distinct fields in the old project: English
name, casting-time category, damage type, concentration, ritual, class, optional
Tasha's class access, subclass, source, school, and component flags.

## Shape and rationale

| Field | Shape | Reason |
| --- | --- | --- |
| `id`, `slug` | keyword strings | Stable document identity and readable URL identity are separate concerns. |
| `name` | `{ ru, en }` | The pages carry both localized and English names; both are boosted in search. |
| `aliases` | text list | Supports alternate translations and historic names without overloading the canonical name. |
| `level` | integer `0..9` | `0` represents a cantrip and works naturally as an exact filter. |
| `school` | enum/keyword | One of the eight stable magic schools. |
| `castingTime` | text, category, optional reaction trigger | Keeps display wording while enabling a future exact casting-time filter. |
| `range` | text | Source wording includes shapes such as “Self (30-foot radius)”; flattening this too early loses meaning. |
| `components` | flags plus material details | `V/S/M` are independently filterable. Cost and consumption affect rules behavior. |
| `duration`, `concentration`, `ritual` | text plus booleans | Concentration and ritual are mechanics, not merely decoration in a duration string. |
| `classes` | access objects | Records optional access and the rulebook that grants it. |
| `subclasses` | name plus parent class | Subclass names alone can be ambiguous. |
| `description`, `higherLevels` | separate text | Higher-slot scaling is semantically distinct and can be rendered separately. |
| `damageTypes` | keyword list | Present as an index facet and useful for filtering. |
| `sources` | reference list | A spell can be printed or revised in more than one book/edition. |
| `sourceUrl` | HTTPS URL | Retains provenance without making the external site's numeric ID the whole model. |

Elasticsearch uses text fields for names/rules text and normalized keyword fields for
facets. The mapping is `dynamic: strict`: ingest failures expose a model/mapping mismatch
instead of silently creating a field with the wrong type.

## Deliberate boundaries

- Rules text is plain text. If rich inline rules references are needed later, add a
  separately sanitized rich-text field rather than storing arbitrary source HTML.
- Distance and duration remain display text in the base model. Normalized units can be
  added when there is a concrete range/duration query requirement.
- This setup models and stores spells but does not include a crawler. Any importer should
  respect the source's terms, rate limits, and content rights.
