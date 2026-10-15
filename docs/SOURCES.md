# Source and ingestion policy

`data/knowledge.json` is the source manifest and reviewed corpus in one file. Each record links to its supporting page; review date is when the editorial summary was checked, not the page's publication date. English paraphrases and Hindi translations are brief, original summaries. The collection is a starting corpus of 40 records, not exhaustive Varanasi coverage.

## Source tiers

| Source | Use | Caveat |
| --- | --- | --- |
| [District Varanasi](https://varanasi.nic.in/) | Locations, directories, temples, crafts and heritage | Tourism pages mix history and belief; classify the claim itself |
| [Ministry of Tourism](https://www.incredibleindia.gov.in/en/uttar-pradesh/varanasi) | Food, places, cultural introductions | Promotional material can be simplified or inconsistent |
| [UNESCO Varanasi profile](https://www.unesco.org/en/creative-cities/varanasi) | Music designation and cultural programs | Program descriptions do not establish current schedules |
| [Ministry Buddhist circuit guide](https://tourism.gov.in/sites/default/files/2021-10/Buddhist%20Tourism%20Circuit%20in%20India_ani_English_Low%20res.pdf) | Sarnath monuments and Buddhist association | Historical visitor guide, not live travel instructions |
| Original repository news feed | Publisher headlines, short snippets, URLs and dates | Reports are unverified; upstream coverage and date accuracy vary |

The new assistant fetches only the operator-configured JSON feed. It has no general crawler. It does not bypass login, robots, paywalls or publisher restrictions. Public accessibility is not a blanket reuse license. The legacy fetcher is preserved for compatibility; preservation does not certify that every publisher currently permits every form of reuse. Review current publisher terms before operating it commercially. Do not index complete articles or images without a suitable license. Disable a disallowed source upstream and remove its indexed records on the next refresh. For takedowns, edit the maintained feed/corpus and rebuild; no persistent vector cache retains deleted records.

## Claim taxonomy

- `historical_fact`: a sourced historical statement, still open to scholarly correction.
- `official_information`: an institution's description or directory record, reviewed on a date.
- `religious_tradition`: a belief, legend or devotional narrative, explicitly attributed.
- `current_news`: a publisher report, never silently promoted to verified fact.

When a sentence mixes history and legend, split it into separately typed records. Avoid repeating questionable medical claims, superlatives or exact dates merely because a tourism page contains them. Current opening hours, admission rules and festival dates require fresh authoritative checks; the static corpus does not supply them.

## Add knowledge safely

1. Select a specific authoritative page or legally reusable collection. Check terms and access policy. Prefer official APIs/RSS where suitable.
2. Write a concise original paraphrase and a reviewed Hindi translation; do not bulk copy source text. Preserve uncertainty.
3. Add a stable ID, title, aliases, category, claim type, source URL, publisher, language, `reviewed_on` and rights note following the existing JSON schema.
4. Split conflicting perspectives rather than synthesizing away disagreement. Use the page that supports the actual claim, not a generic home page.
5. Add relevant and adversarial questions to evaluation, restart the service, and run tests. The LlamaIndex ingestion pipeline processes the updated corpus at startup.
6. Recheck periodically and after an institution changes its rules; remove dead or no-longer-permitted material.

## News metadata

Canonicalization removes fragments and common tracking query parameters, preserves path case and semantic query parameters, and rejects non-HTTP(S) links. Stable IDs derive from the canonical URL. Same-publisher identical titles collapse; cross-publisher titles remain for comparison. This does not detect all near-duplicate or syndicated stories.

Only headline and at most 800 characters of the upstream summary are indexed. `content` is deliberately ignored. `date` is parsed independently of `firstSeenAt`/`updatedAt`. Unknown relative dates are not invented. `retrieved_at` is ingestion time, never evidence that the news is recent. The UI exposes publication, review and snapshot status separately. No political stance or truth score is inferred from publisher identity.
