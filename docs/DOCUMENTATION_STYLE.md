# Documentation & Comment Style

Which file owns which fact, how much of the reasoning goes where, and what `❗️` means.

`CLAUDE.md` carries each of these rules as a single imperative line and links here for the argument.
That split is itself an application of the first rule: `CLAUDE.md` is loaded into every session, so a
paragraph of rationale in it is charged to every task, including the ones that never touch a document.

---

## 1. One topic, one owner

A file that needs a fact another file owns **links to it and does not restate it**. A copied
rationale paragraph is a copy that diverges silently at the first edit.

| Topic | Owner |
|---|---|
| Module boundaries, ports, value objects, error handling | `ARCHITECTURE.md` |
| Subdomain classification and where each subdomain lives | `SUBDOMAINS.md` |
| Authentication and the API key | `SECURITY.md` |
| The REST surface | `api/` |
| Domain decisions | `domain/` |
| Release and version bump | `.claude/skills/version-bump/SKILL.md` |
| Documentation, comments, `COMMENT ON`, the `❗️` marker | this file |
| The TDD cycle | `TDD_WORKFLOW.md` |
| What an issue updates before it closes: surface docs, Bruno, the changelog | `.claude/skills/close-the-issue/SKILL.md` |
| The reviewer/author loop and the marker schema | `REVIEW_WORKFLOW.md` |

**KDoc states the contract and links the section; it does not re-argue it.**

## 2. Docs describe the present, not the process

Everything under `docs/` (and KDoc) states what the system **is now** and why. Do **not** put process
metadata in it: no version labels (`v1`, `in v2`, …), no issue/PR ids, and no "to be added to X when
implemented" notes — that history lives in git and in `CHANGELOG.md`. A "v1" tag becomes a lie the
moment the next version ships, and an issue number tells the reader nothing.

Roadmap and deferrals are legitimate on a **surface** document — they describe the current scope — but
phrase them without version labels: prefer "not exposed yet", "deferred until needed", "may be added
later" over "not in v1". ❗️ This paragraph does not reach `docs/domain/`, where the subsection below
forbids those three phrasings outright: a surface doc says what a caller can and cannot do today, and
"not exposed yet" is part of that statement; a domain doc states what the model *is*, and a deferral is
not part of it.

**Exception:** `CHANGELOG.md` references issue numbers by design.

### `docs/domain/` records decisions, and describes the present like every other document

It states the decisions that govern the domain model and the reasoning behind them, **including the
alternative each decision was taken over**: why a date is a `LocalDate` and not a `LocalDateTime` is
what makes the present shape understandable, and it stays. The present tense there means what it means
everywhere else under `docs/` — this is how the system works now.

What a domain doc does **not** carry is anything about a future: an open question nobody has answered,
an extension path, a feature deferred, an idea weighed and set aside. None of those is a decision, and
written in the same present tense as the rest they are indistinguishable from one. Where such a thing
needs writing down it belongs to the issue that will settle it.

A domain doc is therefore kept current by the change that alters the model, in the same commit, the
way `api/` is. No per-section "implemented" marker is ever added: everything a domain doc describes is
built, so there is nothing to mark. What the REST surface *exposes* is still owned elsewhere —
`api/API_INDEX.md`, `CHANGELOG.md` and the code — and a domain doc does not mirror it.

## 3. Comments are terse; the argument lives in `docs/`

A comment says *what* holds and links; a document says *why*. Default to one or two sentences at a
declaration. When the reasoning needs a paragraph — an alternative that was rejected, a
counterexample, a trade-off — that paragraph belongs in the file that owns the topic, and the KDoc
links to it and stops.

**A class KDoc long enough to need its own review pass is a document written in the wrong file**, and
it is charged to every reviewer of every PR that touches the file — and to every read of the file,
whether or not the reader came for the reasoning.

The operative test at a declaration: *would someone who already knows what this does still need this
sentence to change it safely?* If not, it belongs in a document.

❗️ **One exception, and it is the only one: a note whose reader is the person editing that line.**
"Change this and something elsewhere breaks, and nothing will fail" cannot live in `docs/`, because
whoever is about to make the change is not reading `docs/`. Keep it in the code, keep it to a
sentence or two, and mark it — that is what `❗️` is for.

## 4. `❗️` means: changing this silently breaks something, and no test will tell you

Not "this is important", not "this was deliberate", not "this surprised me while writing it". If a
compiler error, a failing test or a red build would catch the mistake, write the note without the
marker.

A marker used for emphasis stops being a marker: the reader can no longer tell which of a file's
warnings concerns them, and the ones that guard a silent breakage are the ones that get skipped.

❗️ An **inbound port** carries none. It is the black-box contract, read by whoever calls the use
case, and a contract is not a hazard notice.

## 5. Never justify code by what a test can reach

"A branch no test could reach", "pinned by a test", "two slice tests drive it" — say what the code is
*for*. A reader who then wonders whether the line is an accommodation for the test suite has been
misled by the comment, and the next reader may delete the line.

❗️ The inverse is legitimate and is the `❗️` case in §3: a warning that a rule has **no** test behind
it tells an editor something running the suite would never reveal.

## 6. A migration comment must be true forever, without maintenance

A migration is immutable once a database has applied it: Flyway validates its checksum, and the
deployed schema already carries it. Nothing written in one can ever be corrected — a comment that will
need updating is a comment that will be wrong. Write only about **what that migration creates or modifies**, and write it **on the
object itself** wherever the schema can carry it (§7).

❗️ `--` prose is strictly dominated: a `COMMENT ON` statement sits in the script too, so it reaches
the script reader *as well as* everyone who only ever sees the database. Reach for `--` only when
nothing in the schema can hold the note — rarer than it looks, since an absence can almost always be
recorded on the nearest object that does exist, the way "no cycle constraint" belongs on the
self-referencing column.

❗️ **Never instruct the future from the script prose.** "Change both widths together" is addressed to
whoever alters the column, and after the freeze that person is writing a *different* file — so put it
where they will be looking, which is the column comment. For the same reason, do not explain how
migrations work in general: whoever opens one already knows.

❗️ **No pointer that can rot** — and class and constant names rot too. A rename refactor reaches
neither a `.sql` file nor a string stored in the database, so a Kotlin constant named in a migration
is a claim nobody is told to update; doc paths and `§` section numbers are worse still, since nothing
signals when they break. **Name the responsibility, not the artefact:** "the application enforces the
same maximum length" survives every refactor. Precise pointers belong on the mutable side of the pair
— the code names the column, never the reverse.

## 7. What a database consumer needs goes in `COMMENT ON`, not in `--`

A column comment reaches everyone who reads the schema through `\d+` or a client, not just the few
who open the `.sql`; it is where a warning meets whoever is about to `ALTER` the column. It is also
the **less** frozen of the two, since a later migration can re-issue it while `--` text never changes
again.

Keep it to one contract-shaped line — what the column holds, what `NULL` means, what the application
enforces on it — and leave rationale to the code that owns it.

**Repetition across columns is correct here and is not redundancy.** Whoever reads `\d+` sees one
column at a time and has no other source; the same sentence on four `id` columns is four readers
served, not one sentence written four times. This is the one place where the terseness of §3 does not
apply, and the reason it does not is the audience, which cannot follow a link.

❗️ `COMMENT ON` **is schema**: it shows up in `pg_dump`, so adding or rewording one is a schema change
and belongs in a migration like any other. **Nothing verifies its text**: the behaviour it describes
is pinned by the persistence slice running on the Flyway schema, never by a test that reads the DDL.

## 8. Surface docs carry the contract, and the reasoning at the end

For a REST resource (`api/<resource>.md`): an endpoint section carries request, response and errors
only, and the *why* goes in the file's closing `# Design notes` section with a link from the rule.
**Move rationale there, never delete it.**
