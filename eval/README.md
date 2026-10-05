# Gold set

The gold set scores AwardTrace's LLM categories against the free PSC baseline ([ADR 0007](../docs/decisions/0007-llm-enrichment-is-optional-and-evaluated.md)): 200 award descriptions, each labeled by hand with one of the 14 categories. The same labels score both, so the comparison is fair only if every label comes from the description alone.

| File | Contents |
|---|---|
| `gold.csv` | One row per description: `description_hash`, `description`, `psc_code`, and `label` |
| `sample-gold.sql` | The query that drew `gold.csv` from a loaded database |
| `results/` | One report per model and prompt version, written by the `eval` task |

## How the gold set is drawn

`sample-gold.sql` drew the committed `gold.csv` from the Department of Agriculture's contract awards for fiscal years 2025 and 2026.

| Property | Detail |
|---|---|
| Size | 200 rows, one per `description_hash`, with 166 distinct PSC codes among them |
| Clear rows | 180, spread evenly across the 13 baseline categories rather than in proportion to the data, so small categories such as `CYBERSECURITY` appear: 14 rows each, or 13 for `CYBERSECURITY` and `OTHER`. Each category's PSC codes take turns, so no one code dominates |
| Terse rows | 20 whose descriptions have at most one word of three or more letters, spread across PSC codes. Some are bare codes and others name a thing in a word or two, so together they test where `UNCLASSIFIABLE` begins |
| Order | Shuffled, so the rows don't arrive grouped by category |
| Repeatability | A fixed seed in the draw gives the same sample from the same data |
| `label` | Empty until you label it |

## Label the gold set

1. Open `gold.csv` in a spreadsheet and hide the `psc_code` column. It's there to score the baseline, and seeing it while you label would tilt the labels toward the baseline.
2. For each row, read `description` and write exactly one category code in `label`, copied exactly from [Categories](#categories).
3. Decide from the description, this guide, and the category definitions alone. Don't look up the award, the recipient, or the PSC.
4. When more than one category fits, check [Known boundaries](#known-boundaries) first, then apply [Tie-break rules](#tie-break-rules) in order.
5. Leave no row blank. If you can't tell what was bought, the label is `UNCLASSIFIABLE`.
6. Save as CSV in UTF-8, with the header and the row order unchanged, and change no other column.

Labels are final once the first evaluation runs. Fix a misspelled code, but never change a label after seeing a model's answers. A later prompt version can change; the labels can't.

## Categories

The Definition column is the exact text the classifier prompt uses. The Notes column is for you; it settles common cases.

The model sees only the definitions and three rules: choose exactly one code per item, use `UNCLASSIFIABLE` for vague text, and return every ID exactly once. It never sees the notes, rules, or examples in this guide. If the results show the model losing points to one of them, a later prompt version can add it; the labels never change to suit a model.

| Code | Definition | Notes |
|---|---|---|
| `IT_SOFTWARE` | Software development, modernization, licenses, and software as a service | Includes software maintenance and support agreements |
| `IT_INFRASTRUCTURE` | Computers, networks, data centers, cloud hosting, and telecommunications | Includes phones, radios, printers, and help desks and other IT operations that aren't software development or security |
| `CYBERSECURITY` | Security operations, assessments, identity management, and information assurance | Computer and information security only. Guards, gates, and alarms are physical security, which goes with the facility: `CONSTRUCTION_FACILITIES` |
| `PROFESSIONAL_SERVICES` | Management consulting, program support, and administrative and financial services | Office work: consulting, program and project management support, clerical and records work, accounting, auditing, and budgeting |
| `ENGINEERING_RESEARCH` | Research and development, engineering, and scientific and technical studies | Includes architect-engineer design, surveys and studies, lab analysis, testing, and inspection. Designing a bridge is here; building it is `CONSTRUCTION_FACILITIES`. Studying land or wildlife is here; working on it is `NATURAL_RESOURCES` |
| `CONSTRUCTION_FACILITIES` | Construction, renovation, repair, and operation of buildings and real property | Includes roads, trails, bridges, and fences, even on forest land; janitorial, guard, pest control, and grounds services at a facility; utilities; and leases of buildings and land |
| `HEALTH_MEDICAL` | Medical services, pharmaceuticals, medical equipment, and health programs | Includes veterinary medicine: animal vaccines, veterinary services, and diagnostic kits |
| `DEFENSE_SYSTEMS` | Weapons, ammunition, military vehicles, aircraft, ships, and their parts | Weapons and ammunition count whoever buys them, law enforcement included. Vehicles, aircraft, and ships count only when built for combat; civilian ones are `LOGISTICS_TRANSPORT` |
| `LOGISTICS_TRANSPORT` | Freight, shipping, fuel, travel, and vehicle fleets not built for combat | Includes buying, leasing, and repairing cars, trucks, aircraft, and boats not built for combat; moving and relocation; and fuel for vehicles, equipment, and buildings |
| `SUPPLIES_EQUIPMENT` | General supplies, furniture, tools, and equipment not covered elsewhere | Includes food bought as goods, such as commodities for nutrition programs; seed, chemicals, clothing, and lab supplies; and the repair or rental of equipment. A category that names the item wins: computers are `IT_INFRASTRUCTURE`, and medical equipment is `HEALTH_MEDICAL` |
| `TRAINING_EDUCATION` | Training delivery, curriculum, and education services | Includes courses for firefighters and other staff, and education programs for the public |
| `NATURAL_RESOURCES` | Wildfire suppression, forestry, land and wildlife management, conservation, and environmental cleanup | Includes aircraft, engines, and crews hired to fight fires; hazardous fuels reduction, the cutting of brush and small trees to slow wildfires; thinning, planting, and weed control; and removing contamination |
| `OTHER` | A clear description that fits none of the categories above | You can tell what was bought, and nothing above fits: catering, child care, laundry, and social services |
| `UNCLASSIFIABLE` | The description is too vague to classify, such as "SEE SCHEDULE" or a bare modification number | You can't tell what was bought. See [Vague descriptions](#vague-descriptions) |

## Known boundaries

These cases read like one category but belong in another. They override the tie-break rules.

| Description reads like | Label | Why |
|---|---|---|
| Construction, but the work removes contamination: an underground storage tank, contaminated soil, asbestos, or hazardous waste | `NATURAL_RESOURCES` | The definition names environmental cleanup. Work that only demolishes or repairs a structure stays `CONSTRUCTION_FACILITIES` |
| Transport or an equipment rental, but the aircraft or equipment fights fire: an air tanker, a helicopter with crew, a fire engine, or a water tender | `NATURAL_RESOURCES` | Hiring them is buying wildfire suppression. Goods and services for the people fighting a fire, such as meals, showers, medical units, phones, and land for a camp, take their own category |
| Only codes, such as `0101-011426 N474NA PKG-70329` | `UNCLASSIFIABLE` | Nothing says what was bought |

## Tie-break rules

1. Label what was bought, not who bought it, where, or why. Laptops for a fire dispatch center are `IT_INFRASTRUCTURE`.
2. A category whose definition names the item beats a general one. `SUPPLIES_EQUIPMENT` and `OTHER` apply only when nothing else fits. Security work on IT systems is `CYBERSECURITY`, not `IT_SOFTWARE`, and medical equipment is `HEALTH_MEDICAL`, not `SUPPLIES_EQUIPMENT`.
3. Repair, maintenance, installation, and rental take the category of the thing worked on or rented. Building repair is `CONSTRUCTION_FACILITIES`, software maintenance is `IT_SOFTWARE`, vehicle repair is `LOGISTICS_TRANSPORT`, and equipment rental is `SUPPLIES_EQUIPMENT`.
4. When a description lists several things, label the main one: the one the description is about, or else the first one named.
5. Ignore codes and boilerplate: contract and order numbers, markers such as `IGF::OT::IGF`, fiscal years, and option-year wording. Label what remains.

## Vague descriptions

The label is `UNCLASSIFIABLE` when you can't tell what was bought:

| Kind | Example |
|---|---|
| Only codes or numbers | `0101-011426 N474NA PKG-70329` |
| A placeholder | `SEE SCHEDULE`, `PURCHASE ORDER`, `BPA CALL` |
| A contract action with no subject | `MOD P00003`, `EXERCISE OPTION YEAR 2`, `INCREMENTAL FUNDING` |
| Only a name: a company, a person, an incident, a program, or an agency | `NOMADIC LAND CAMPS, LLC IDIPF000347 E40` |

A company's name doesn't say what was bought, so don't infer a category from it. A few plain words are enough, though: `JANITORIAL SERVICES` is `CONSTRUCTION_FACILITIES`.

## Wildfire incident orders

Many Forest Service descriptions are wildfire incident orders. They pack a request number, the incident name, an incident number, a resource code, and often the resource's name into one line. Label from the resource's name, the plain words near the end, by the rules above. If there are no plain words, the label is `UNCLASSIFIABLE`.

| Description | Label | Why |
|---|---|---|
| `E127, UPPER SMITH, IDIPF000347, MESU, MEDICAL SUPPORT UNIT;` | `HEALTH_MEDICAL` | A medical unit serves the crews; it doesn't fight the fire |
| `S198, 0587 SHINGLE, OR953S000587, SLND, SERVICE - LAND RENTAL;` | `CONSTRUCTION_FACILITIES` | Land rented for a camp is a lease of real property |
| `E12, CEDAR CREEK, WAOKF000412, ENGB, ENGINE TYPE 6 WITH CREW;` | `NATURAL_RESOURCES` | An engine with its crew fights the fire |
| `NOMADIC LAND CAMPS, LLC IDIPF000347 E40` | `UNCLASSIFIABLE` | A company name and codes; nothing says what was bought |

The first, second, and fourth are real Forest Service descriptions from the test fixtures. If one turns up in the gold set, label it as shown.

## Examples

Two per category. Apart from the incident orders above, they're written for this guide in the style of Agriculture's descriptions.

| Code | Example | Why |
|---|---|---|
| `IT_SOFTWARE` | `ANNUAL RENEWAL OF ARCGIS ENTERPRISE SOFTWARE LICENSES` | Licenses |
| `IT_SOFTWARE` | `MODERNIZATION OF THE FOOD SAFETY INSPECTION REPORTING APPLICATION` | Software modernization |
| `IT_INFRASTRUCTURE` | `40 RUGGEDIZED LAPTOPS WITH DOCKING STATIONS FOR FIELD OFFICES` | Computers |
| `IT_INFRASTRUCTURE` | `SATELLITE PHONE SERVICE FOR DISTRICT FIRE CREWS` | Telecommunications; serving fire crews doesn't make it suppression |
| `CYBERSECURITY` | `SECURITY ASSESSMENT AND AUTHORIZATION OF THE NATIONAL FINANCE CENTER SYSTEMS` | A security assessment |
| `CYBERSECURITY` | `IDENTITY, CREDENTIAL, AND ACCESS MANAGEMENT SUPPORT` | Identity management |
| `PROFESSIONAL_SERVICES` | `PROGRAM MANAGEMENT AND ADMINISTRATIVE SUPPORT FOR THE SINGLE FAMILY HOUSING PROGRAM` | Program and administrative support |
| `PROFESSIONAL_SERVICES` | `FINANCIAL STATEMENT AUDIT SUPPORT, FY2026` | Financial services |
| `ENGINEERING_RESEARCH` | `RESEARCH ON DROUGHT TOLERANCE IN WINTER WHEAT VARIETIES` | Research |
| `ENGINEERING_RESEARCH` | `ENGINEERING DESIGN FOR REPLACEMENT OF THE BEAR CREEK BRIDGE` | Design, not construction |
| `CONSTRUCTION_FACILITIES` | `REROOF AND HVAC REPLACEMENT, ASHLAND RANGER STATION` | Building repair |
| `CONSTRUCTION_FACILITIES` | `JANITORIAL SERVICES, BELTSVILLE AGRICULTURAL RESEARCH CENTER` | Operation of a building |
| `HEALTH_MEDICAL` | `OCCUPATIONAL HEALTH EXAMS FOR WILDLAND FIREFIGHTERS` | A medical service; serving firefighters doesn't make it suppression |
| `HEALTH_MEDICAL` | `AVIAN INFLUENZA VACCINE FOR POULTRY, NATIONAL VETERINARY STOCKPILE` | Veterinary medicine counts |
| `DEFENSE_SYSTEMS` | `9MM DUTY AMMUNITION FOR FOREST SERVICE LAW ENFORCEMENT` | Ammunition counts whoever buys it |
| `DEFENSE_SYSTEMS` | `SPARE PARTS FOR M1A2 ABRAMS TANK TRANSMISSIONS` | Parts for a combat vehicle |
| `LOGISTICS_TRANSPORT` | `FREIGHT HAULING OF COMMODITY CHEESE FROM WAREHOUSES TO STATE DISTRIBUTION SITES` | Freight; the cheese itself would be `SUPPLIES_EQUIPMENT` |
| `LOGISTICS_TRANSPORT` | `LEASE OF TEN HALF-TON PICKUP TRUCKS FOR THE RANGER DISTRICT FLEET` | A vehicle fleet |
| `SUPPLIES_EQUIPMENT` | `120,000 POUNDS OF FROZEN DICED CHICKEN FOR THE NATIONAL SCHOOL LUNCH PROGRAM` | Food bought as goods; the program it feeds doesn't change that |
| `SUPPLIES_EQUIPMENT` | `REPAIR OF TWO JOHN DEERE TRACTORS, MANDAN RESEARCH LABORATORY` | Equipment repair |
| `TRAINING_EDUCATION` | `DELIVERY OF THE S-212 WILDLAND FIRE CHAINSAWS COURSE` | Training, even for firefighters |
| `TRAINING_EDUCATION` | `CURRICULUM DEVELOPMENT FOR THE FARM TO SCHOOL EDUCATION PROGRAM` | Curriculum |
| `NATURAL_RESOURCES` | `PRECOMMERCIAL THINNING AND HAND PILING, 450 ACRES, MALHEUR NATIONAL FOREST` | Forestry |
| `NATURAL_RESOURCES` | `EXCLUSIVE USE TYPE 1 HELICOPTER WITH CREW FOR WILDFIRE SUPPRESSION` | Wildfire suppression, though it reads like an aircraft rental |
| `OTHER` | `CATERING FOR THE 2026 NATIONAL FOOD SAFETY CONFERENCE` | A clear service that no category covers |
| `OTHER` | `CHILD CARE SERVICES AT THE BELTSVILLE CHILD DEVELOPMENT CENTER` | A social service |
| `UNCLASSIFIABLE` | `SEE SCHEDULE` | A placeholder |
| `UNCLASSIFIABLE` | `0101-011426 N474NA PKG-70329` | Only codes |

## Run the evaluation

The `eval` task classifies every row of `gold.csv` with one model and prompt version, scores the model and the PSC baseline against the labels, and writes a report. It writes nothing to the database, and it stops before sending anything if a row has no label or a label that isn't one of the 14 codes.

| Requirement | Detail |
|---|---|
| Labels | Every row in `gold.csv` has one |
| Decision rule | Written in ADR 0013 before the first run, such as "keep Haiku unless Opus scores more than five points higher" |
| Database | The local stack running with migrations applied. The baseline comes from `psc_baseline_category()`, read only |
| API key | `ANTHROPIC_API_KEY` set in the environment. Never commit it or write it to a file |
| Cost | About $0.04 a run on Claude Haiku 4.5 and $0.20 on Claude Opus 5.5 |
| Spending cap | $1.00 a run. A request that could take the run past it isn't sent. Change it with `--awardtrace.enrichment.eval.cap-usd` |

From the repository root:

```sh
cd backend
read -rsp "Anthropic API key: " ANTHROPIC_API_KEY && export ANTHROPIC_API_KEY
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,enricher \
  -Dspring-boot.run.arguments="--awardtrace.enrichment.task=eval --awardtrace.enrichment.model=claude-haiku-4-5 --awardtrace.enrichment.prompt-version=v1"
```

For the ceiling check, run it again with `--awardtrace.enrichment.model=claude-opus-5-5`.

Each run writes `results/{model}-{prompt_version}.md`, such as `results/claude-haiku-4-5-v1.md`, and adds a summary row to `docs/results.md`:

| Measure | Detail |
|---|---|
| Accuracy | For the model and the baseline, over all rows and over the rows not labeled `UNCLASSIFIABLE`. The baseline never answers `UNCLASSIFIABLE`, so the second figure is the fair comparison on clear descriptions |
| Precision and recall | Per category, for the model and the baseline |
| Confusion matrix | Labels against the model's answers |
| `UNCLASSIFIABLE` rate | How often the model answered `UNCLASSIFIABLE`, and how often the label agreed |
| Agreement with the baseline | The share of rows where the model and the baseline give the same category |
| Refusals | Groups the model refused, counted and never retried |
| Tokens and cost | Per 100 items, from the API's `usage` |
