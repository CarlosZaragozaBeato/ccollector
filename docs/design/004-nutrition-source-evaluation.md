# Design 004: Nutrition Data Source Evaluation

- Status: Accepted
- Date: 2026-07-02
- Relates to: Issue #23 — evaluate open-source nutrition data sources
- Relates to: [Design 003 — Extended Domain Model §1.2 `NutritionLog`](003-extended-domain-model.md)
- Closes: Design 003 §5.1 (food item normalization strategy — deferred open question)

## Purpose

Design 003 §1.2 defines `NutritionLog` with an `externalFoodItemId` field that
references an external nutrition catalog. It notes "Open Food Facts" as the
preferred source but defers the catalog strategy to this document (§5.1).

This document answers that deferred question: which external source backs the
`externalFoodItemId` field, how does food item lookup work at runtime, and how does
the chosen source satisfy the self-hosting constraint.

The **non-negotiable constraint** is that the platform must be operable without
any dependency on private or paid third-party services. A source that requires a
live third-party API call at lookup time fails the self-hosting test for offline
or air-gapped deployments.

---

## 1. Evaluation Criteria

The following criteria are derived directly from the project constraint set.

### 1.1 Self-hosting capability

Can the food item data be used without calling a third-party server at runtime?
This requires either:

- A full database dump that can be imported into a locally-managed store, **or**
- An official Docker image that can be deployed alongside CCollector.

A source that only offers a live REST API with no offline data path fails this
criterion regardless of its other qualities.

### 1.2 License

Is the data open enough to store locally, embed in a deployed instance, and
redistribute as part of a self-hosted installation? Permissive public-domain or
open-database licenses satisfy this criterion. Proprietary or all-rights-reserved
data does not, even if the API is free.

### 1.3 Coverage

Number of distinct food products; geographic breadth (is international packaged
food represented?); language support for product names. Coverage matters because
athletes in different countries log different foods. A database heavily biased
toward one geography is less useful as a general-purpose source.

### 1.4 Data quality

Are the four macros required by `NutritionLog` — `energyKcal`, `carbohydratesGrams`,
`proteinsGrams`, `fatsGrams` — reliably present for products in the database?
The rating here reflects the aggregate macro completeness of the source, not
individual product quality (which always varies).

### 1.5 Live API quality

Relevant for deployments that choose not to run a local mirror. Evaluated on:
REST maturity, response format (JSON preferred), rate limits, and authentication
requirements. A source that requires an API key for read queries adds an operational
secret that must be managed in every deployment.

### 1.6 Integration effort

How much custom code or infrastructure is required to integrate the source into
CCollector? A source with an official Docker image and a documented import path
is rated higher than one requiring a custom ETL pipeline from raw CSV.

### 1.7 Community and maintenance health

Is the project or dataset actively maintained? Is there a community that will keep
the data current and fix quality issues? A dormant or abandoned source degrades
over time.

---

## 2. Candidate Evaluation

### 2.1 Open Food Facts

**Overview:** Open Food Facts (OFT) is a collaborative, open-source database of
food products contributed by volunteers, producers, and automated extraction.
Products are identified by their barcode (EAN-13, UPC-A, etc.).

**Product count:** 4,593,715+ products as of 2026-07-02 (live counter on
`world.openfoodfacts.org`), sourced from 150+ countries. This is the largest
open food database in existence.

**License:**
- Database schema and software: AGPL-3.0
- Database itself: Open Database License (ODbL 1.0)
- Individual database contents (facts, labels): Database Contents License (DbCL 1.0)
- Product images: Creative Commons Attribution–ShareAlike (CC BY-SA 4.0)

ODbL is permissive for use, redistribution, and local deployment as long as
derivative databases remain open under the same terms. This satisfies the
self-hosting constraint.

**Bulk data dumps (no authentication required):**

| Format | URL | Approx. size |
|---|---|---|
| CSV (gzip) | `https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz` | ~0.9 GB compressed / ~9 GB raw |
| JSONL (gzip) | `https://static.openfoodfacts.org/data/openfoodfacts-products.jsonl.gz` | Similar |
| MongoDB dump | `https://static.openfoodfacts.org/data/openfoodfacts-mongodbdump.gz` | Full dump |
| Parquet (Hugging Face) | `huggingface.co/datasets/openfoodfacts/product-database` | Columnar format |

Dumps are regenerated daily. There is no official PostgreSQL dump — the canonical
store is MongoDB. Importing the CSV or JSONL into PostgreSQL requires an ETL step
(see §5).

**Docker / self-hosted deployment:** Officially supported. The `openfoodfacts-server`
GitHub repository provides a `docker-compose.yml` that brings up the full Product
Opener stack: backend, frontend, MongoDB, Nginx, Keycloak, and optional services
(Redis, Robotoff). Import commands are documented:

```bash
make import_sample_data   # ~100 products for development
make import_prod_data     # full production dump from static.openfoodfacts.org
```

The OFT documentation explicitly recommends self-hosting for high-traffic
applications: *"we strongly encourage you to host a local instance of Product
Opener … and use the daily exports to update your local database."*

**Live API rate limits (if not self-hosting):**
- Product lookup by barcode: **15 requests/minute per IP**
- Search queries: **10 requests/minute per IP**
- Exceeding the limit returns HTTP 503. The documentation warns that using search
  for real-time features at scale will result in being blocked quickly.

**Authentication for read queries:** None required. A descriptive `User-Agent`
header (`AppName/Version (contact@email.com)`) is mandatory by convention but
is not enforced by authentication tokens or API keys.

**Data quality / macro completeness:** OFT is crowdsourced. Macro completeness
varies significantly by product and region — products contributed from packaged
food labels in Western Europe tend to be more complete than those entered manually.
No official aggregate figure for "% products with all four macros" is published.
In practice, products without a valid nutritional label (e.g., loose produce, or
products entered without the label photo) will have null macro fields. CCollector
must handle null macros and fall back to manual entry when they are absent.

**Geographic coverage:** Strongest for Western Europe (France, Germany, UK,
Spain), with declining density for other regions. International supermarket chains
are often represented because the same EAN barcode appears across markets.

---

### 2.2 USDA FoodData Central

**Overview:** FoodData Central (FDC) is a US government database maintained by the
USDA Agricultural Research Service. It consolidates multiple datasets with different
provenance, quality levels, and update cadences.

| Dataset | Scope | Quality |
|---|---|---|
| Foundation Foods | Commodity foods (beef, apple, milk), minimally processed | Laboratory-analyzed; very high |
| Standard Reference Legacy (SR Legacy) | Historical USDA composition data (final update 2018) | Laboratory-analyzed; high |
| FNDDS | Dietary survey foods from NHANES (What We Eat in America) | Survey-based; high |
| Branded Food Products | Label data from manufacturer submissions, updated monthly | Label-accuracy; varies |
| Experimental Foods | Data from peer-reviewed publications | High; small set |

**Product count:** No total count is listed on the download page. The April 2026
all-datasets bundle is 458 MB compressed / 3.1 GB unzipped, suggesting hundreds
of thousands of entries total (the Branded Foods dataset alone is large).

**License:** CC0 1.0 Universal (public domain). No copyright restriction; free to
use, distribute, embed, or modify without attribution. This is the most permissive
possible license.

**Bulk data downloads:** Available at `https://fdc.nal.usda.gov/download-datasets/`
with no authentication. Direct CSV and JSON links for each dataset and release
cycle. The full bundle or individual datasets can be downloaded freely.

**Live API:** Requires an API key (free registration via `data.gov`). The
`DEMO_KEY` is available without registration but is limited to 30 requests/hour
and 50 requests/day per IP. Standard registered keys allow 1,000 requests/hour.
Exceeding the limit returns HTTP 429 with a one-hour block.

**Geographic coverage:** Primarily US-centric. Foundation Foods, SR Legacy, and
FNDDS are built around US dietary surveys and USDA nutrient composition research.
The Branded Foods database includes a "Global Branded Food Products Database"
partnership and therefore has some non-US entries, but the dominant taxonomy,
portion sizes, and product selection reflect US consumption patterns. It is not
a reliable source for non-US packaged goods.

**Self-hosting:** No Docker image and no self-hosted server is provided. The bulk
CSV download is available, but importing it into a queryable local database requires
a custom ETL pipeline. There is no officially supported tooling for this. The
import schema must be reverse-engineered from the FDC documentation and CSV headers.

**Data quality / macro completeness:** Excellent for the laboratory-analyzed
datasets (Foundation, SR Legacy, FNDDS). The four macros are present for virtually
all Foundation and SR Legacy entries. Branded Foods quality depends on manufacturer
submission accuracy.

---

### 2.3 Nutritionix

**Overview:** Nutritionix is a commercial SaaS API offering structured nutritional
data for branded, restaurant, and generic foods. It is used primarily by fitness
and nutrition tracking applications.

**Free tier:** The developer documentation is behind a login wall. Based on public
information: the free tier historically allowed approximately 50 natural-language
food queries per day and 500 item lookups per day. Current limits may differ and
could not be confirmed from accessible pages at the time of writing.

**Behaviour when limits are exceeded:** HTTP 402 or HTTP 429 with a JSON error
body. No graceful degradation — the API simply stops serving requests until the
quota resets. A self-hosted deployment serving an unknown number of athletes could
exhaust the free tier and fail silently if error handling is not explicit.

**Offline / self-hosted deployment:** Not possible. Nutritionix is a closed
commercial service. There is no data dump, no open license, no Docker image, and
no on-premises deployment path. Any deployment using Nutritionix is permanently
dependent on a third-party SaaS provider.

**Pricing:** Multi-tier (Starter → Professional → Enterprise). Prices are not
publicly listed; a sales contact is required for paid tier details.

**Conclusion:** Nutritionix fails the self-hosting constraint unconditionally.
Even if the free tier were sufficient for a single athlete, it violates the
principle of no dependency on private or paid services. It is excluded from
further consideration.

---

### 2.4 Cronometer

**Overview:** Cronometer is a consumer nutrition-tracking application (web + mobile)
with a reputation for high data quality, drawing from curated sources including NCCDB
(Nutrition Coordinating Centre Food and Nutrient Database).

**Public developer API:** None. Cronometer does not offer a public developer API
for third-party integration. Their integrations page lists consumer-facing data
import features (Apple Health, Fitbit, Garmin) but these are one-way data import
flows for end users, not an API available to external developers. No developer
portal, no OAuth application registration, no API documentation for third parties
exists.

**Conclusion:** Cronometer is excluded for a factual reason: it provides no
integration surface. There is no mechanism by which CCollector could query food
item data from Cronometer programmatically.

---

## 3. Comparison Matrix

| Criterion | Open Food Facts | USDA FoodData Central | Nutritionix | Cronometer |
|---|---|---|---|---|
| **Self-hosting / offline** | ✅ Official Docker + daily dump | ⚠️ Bulk CSV (no server; DIY ETL) | ❌ SaaS only | ❌ No API |
| **License** | ✅ ODbL (open database) | ✅ CC0 (public domain) | ❌ Proprietary | ❌ N/A |
| **Coverage — product count** | ✅ 4.6 M+ products | ⚠️ Large; predominantly US | ⚠️ Unknown (proprietary) | ❌ No API |
| **Coverage — geographic breadth** | ✅ 150+ countries | ⚠️ US-focused | ⚠️ US-focused | ❌ N/A |
| **Coverage — language support** | ✅ Multilingual (product names in native language) | ⚠️ English only | ⚠️ English only | ❌ N/A |
| **Macro completeness** | ⚠️ Varies (crowdsourced; null fields possible) | ✅ Excellent (lab-analyzed core datasets) | ⚠️ Unknown | ❌ N/A |
| **Live API — auth required** | ✅ No auth for reads | ❌ API key mandatory | ❌ API key mandatory | ❌ No API |
| **Live API — rate limits** | ⚠️ 15 req/min (low) | ⚠️ 1,000 req/hr (standard key) | ❌ Quota exhaustion possible | ❌ No API |
| **Integration effort** | ✅ Official Docker + documented import | ⚠️ Custom ETL from CSV | ❌ API-only (SaaS) | ❌ Impossible |
| **Community & maintenance** | ✅ Active open-source community | ✅ US government (monthly updates) | ⚠️ Commercial; external dependency | ⚠️ Consumer app; no dev community |

**Rating key:** ✅ Satisfies criterion / ⚠️ Partial or conditional / ❌ Fails criterion

---

## 4. Recommendation

### 4.1 Primary source: Open Food Facts

**Open Food Facts is the recommended primary source.** It is the only candidate
that satisfies the self-hosting constraint without reservations:

- It provides an **official Docker Compose deployment** (`openfoodfacts-server`) with
  a documented import path for the full product database. A self-hosted OFT instance
  runs entirely within the operator's infrastructure.
- Daily database dumps (CSV, JSONL, MongoDB) are available without authentication,
  enabling an import-and-cache strategy that removes any runtime dependency on the
  live `world.openfoodfacts.org` API.
- The ODbL license is compatible with local storage and redistribution as part of
  a self-hosted installation.
- With 4.6 million products from 150+ countries, coverage is adequate for
  international use — the likely demographic for a self-hosted sports platform
  operated by non-US athletes.
- The OFT documentation itself recommends self-hosting for high-traffic scenarios,
  which is an unusual but valuable alignment with this project's constraints.
- No API key is required for read operations, reducing operational secret management
  to zero for deployments that choose the live API path during development.

### 4.2 Fallback / complementary source: USDA FoodData Central

USDA FDC is recommended as a **complementary source for nutritional accuracy**, not
as a general fallback. Its laboratory-analyzed Foundation and SR Legacy datasets
contain highly accurate macro values for commodity foods (beef, chicken, rice, oats)
that are commonly logged by endurance athletes. These foods may appear in OFT with
crowdsourced macro data of variable quality.

A practical use case: if CCollector's local cache derives from the OFT CSV dump,
operators can augment it with FDC's Foundation Foods dataset (small, high-quality,
CC0) for generic ingredient entries. The two datasets can coexist in the local
`food_items` table with a `source` column (`OPEN_FOOD_FACTS` or `USDA_FDC`).

**FDC is not a replacement for OFT** because it has no self-hosted server, requires
a custom ETL pipeline, and has limited international coverage.

### 4.3 Known limitations of Open Food Facts

- **Macro completeness is not guaranteed.** OFT is crowdsourced. Products entered
  without a nutritional label scan may have null macro fields. CCollector's lookup
  implementation must treat null macros as an incomplete result and prompt the user
  to enter values manually. Design 003 §1.2 already models this: `NutritionSource.MANUAL`
  is the fallback path.
- **Crowdsourced data errors.** OFT relies on contributor accuracy. A product's
  macro values may be incorrectly entered and later corrected by another contributor.
  Since CCollector stores macros at entry time (Design 003 §1.2 rationale), entries
  already logged will not automatically reflect OFT corrections. This is a known
  and accepted tradeoff — nutrition logging applications universally store snapshotted
  values for the same reason.
- **Western European coverage bias.** Density decreases for products outside Western
  Europe. Athletes in non-European markets may find lower match rates for local
  packaged goods.
- **No native PostgreSQL store.** The OFT stack uses MongoDB. Importing the CSV
  dump into a local PostgreSQL `food_items` table (the approach recommended in §5)
  requires an ETL step, but this is documented and straightforward (see §5).

---

## 5. Integration Architecture Sketch

This section describes how food item lookup would work in CCollector with Open Food
Facts as the primary source. **This is not an implementation spec** — no Java
classes, Liquibase changesets, or database schemas are defined here. Those belong
in a future implementation issue.

### 5.1 Deployment model: local cache over OFT CSV dump

The recommended architecture avoids a runtime dependency on `world.openfoodfacts.org`
by importing the OFT CSV dump into a local `food_items` table in CCollector's own
PostgreSQL database.

```
[OFT daily CSV dump]
         |
         | scheduled import (e.g. weekly)
         v
[collector PostgreSQL]
    food_items table
    (barcode, name, energy_kcal_per_100g, carbs_g, protein_g, fat_g, source, updated_at)
         |
         | SELECT by barcode or full-text search on name
         v
[CCollector food lookup service]
         |
         v
[NutritionLog entry — macros stored at entry time, as per Design 003 §1.2]
```

This approach means:
- Food lookup is a local SQL query — no network call, no rate limit, no external auth.
- The OFT CSV column mapping is applied once during the import job; the
  application code treats `food_items` as a stable internal table.
- The import job can be run by the operator at any frequency (weekly is sufficient
  — OFT data does not change so rapidly that daily imports are necessary for a
  training log).

### 5.2 Lookup flow

1. **User enters a food name or barcode** in the client (dashboard or future
   mobile app).

2. **CCollector queries the local `food_items` table:**
   - By barcode: `SELECT … WHERE barcode = :barcode` — exact match, fast.
   - By name: full-text search using PostgreSQL `tsvector` / `to_tsquery` on the
     `name` column — returns ranked candidates. The user selects from the results.

3. **CCollector returns candidate(s)** with name, energy per 100 g, and macros per
   100 g. The client shows the result(s).

4. **User confirms the food item and enters a portion weight** (grams). CCollector
   computes the macros for the entered quantity and writes the `NutritionLog` entry
   with the computed values stored directly (Design 003 §1.2 — "macros stored at
   entry level, not recomputed on every read").

5. **If lookup fails** (no result by barcode, or no satisfactory name match):
   - The response indicates that the item was not found locally.
   - The client prompts the user to enter macros manually.
   - The `NutritionLog` entry is written with `source = MANUAL` and
     `externalFoodItemId = null` (Design 003 §1.2 — both fields are nullable for
     manual entries).
   - As an optional enhancement for future implementation: the client could attempt
     a live lookup against `world.openfoodfacts.org` as a one-time fallback, and
     if found, offer the user the option to add the product to the local cache.

### 5.3 Live API as alternative for development

For development environments where running a full OFT import is inconvenient,
CCollector can be configured to call the live OFT API directly:

```
GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json
```

No authentication is required; the `User-Agent` header must identify the application
(`CCollector/1.0 (contact@example.com)`). The 15 req/min rate limit is sufficient
for a single developer testing the feature. This mode is **not suitable for
production** — a deployment with multiple athletes would saturate the limit
immediately and trigger HTTP 503 responses.

A `NUTRITION_SOURCE_MODE` configuration property (e.g., `local` vs. `live`)
could toggle between the two paths without code changes.

### 5.4 NutritionSource enum

Design 003 §1.2 defines `NutritionSource` as an enum with `OPEN_FOOD_FACTS` and
`MANUAL`. This document refines it:

| Value | Meaning |
|---|---|
| `OPEN_FOOD_FACTS` | Macros resolved from the local OFT cache or live OFT API. `externalFoodItemId` holds the OFT barcode. |
| `USDA_FDC` | Macros resolved from the local FDC supplement (commodity foods). `externalFoodItemId` holds the FDC `fdcId`. |
| `MANUAL` | User entered macros directly. `externalFoodItemId` is null. |

The `USDA_FDC` value is included now to avoid a future migration if FDC data is
added as a supplement (see §4.2). It does not need to be implemented in the first
iteration.
