# Issue Format

How an issue is written: its sections, the Example Mapping inside it, and the test list the TDD loop
works from. The TDD cycle itself is owned by [`TDD_WORKFLOW.md`](TDD_WORKFLOW.md).

---

## Lifecycle

An issue goes through two separate flows, usually in separate sessions:

| | Refinement | Development |
|---|---|---|
| Starts from | An idea or a rough story | An issue with status `ready` |
| Work | Example Mapping: rules, examples, questions | Test list, then the TDD loop of [`TDD_WORKFLOW.md`](TDD_WORKFLOW.md) |
| Ends with | An issue with status `ready`, with as few open questions as possible | Code, tests, docs, and the final GitHub text |
| Human gate | The user declares the issue ready | The user approves the test list |

**The draft is the only hand-over between the two flows.** Development reads the issue, not the
conversation that produced it, so whatever development needs is written in the draft.

**Development can send an issue back to refinement.** The TDD loop adds the rules, examples and
questions it discovers to the map. When the map health signals (below) say the story is no longer
understood or no longer small, the status returns to `mapping`.

- **A draft lives in `issues/`**, one file per issue, named `ISSUE-<kebab-case-name>.md`. When the
  GitHub stub gives it a number, the file becomes `ISSUE-<N>-<kebab-case-name>.md`, and every draft
  that links it follows the new name. The folder
  is ignored by git: a draft changes at every TDD cycle, and that churn stays local.
- **A GitHub stub is opened as soon as the draft exists.** It carries the title and one line, and it
  gives the issue its number for branches and commits. The draft records that number in its header.
- **A draft is written in Italian.** Code identifiers stay in English (`failedHeaters`,
  `heating.enabled`), and so do the section headings and the `Given` / `When` / `Then` keywords.
- **At closing, the draft is translated to English and becomes the GitHub issue text.** The
  translation uses the vocabulary of the test names and the code, which the loop has already fixed.
  The test list, the answered questions and the discovery markers are removed. A link to another
  draft becomes its GitHub number (`#263`), because `issues/` does not reach GitHub.

## Nature

Every issue declares its nature in the header: `feature`, `bug`, `refactoring` or `removal`.
[`tdd-feature`](../.claude/skills/tdd-feature/SKILL.md) reads it to decide whether the TDD workflow
applies.

- **feature**: adds a behaviour. It carries the full Example Mapping.
- **bug**: fixes a behaviour. The rule states the correct behaviour; its first example reproduces the
  bug with the real values.
- **refactoring**: pure structural change, no new behaviour. It has no Story, Rules or Test list
  sections; it follows the [refactoring template](#refactoring-template).
- **removal**: takes a behaviour away, and adds none. It follows the
  [refactoring template](#refactoring-template), with `Nature: removal` in the header. A removal does
  not assert the absence of what it removes: the tests deleted with the code and the invariants on
  what remains verify it.

## Example Mapping

Example Mapping is a technique for exploring a story before writing code. It sorts what is known into
four kinds of card, each with its colour:

| Card | Colour | Holds |
|---|---|---|
| Story | 🟨 yellow | The one capability the issue delivers |
| Rule | 🟦 blue | A business rule the story must obey |
| Example | 🟩 green | A concrete case of one rule, with values |
| Question | 🟥 red | Something nobody can answer yet |

The colour marks the heading or the label of each card, so the map reads at a glance in any Markdown
viewer.

**One issue holds exactly one story.** Behaviour that belongs to another story becomes a new file in
`issues/`, and the current issue lists it under *Out of scope*.

**Questions do not block the issue.** They measure how well the story is understood. A question blocks
only the examples and tests that depend on it, and it names them. Many open questions mean the story
is not ready for the TDD loop.

### Identifiers

Rules, examples and questions are numbered across the whole issue: `R1`, `R2`, …; `E1`, `E2`, …;
`Q1`, `Q2`, …. A number is never reused, and an example moved under another rule keeps its number.
The test list and the TDD audit trail cite these identifiers.

An identifier becomes stable when the issue gets its test list. During refinement nothing cites the
map yet, so refinement may renumber it, for example after splitting an issue or removing a rule.

### Map health

| Signal | Reading |
|---|---|
| More than 6 rules | The story is too big: split it |
| More than 3 examples under one rule | Check what varies between them. Different values are coverage. A different reason is a hidden rule: give it its own card |
| Many open questions | The story is not understood well enough to start |
| Several new stories split off | The original slice was too big; the current story can still proceed |

### Splitting a story by its effects

An action with several effects can be split across issues: one issue delivers some effects, a later
one adds the rest. Disabling heating, for example, first saves the flag in one issue and turns the
heaters off in the next. The split holds under two conditions:

- **The earlier issue states only what stays true after the later one.** Its examples assert the
  effects it delivers and nothing about the effects it leaves out. An example asserting "no heater
  receives a command" would become false, and its test would break, when the later issue adds the
  commands.
- **The intermediate state is no worse than before the earlier issue.** When it is worse, such as a
  known defect that only the later issue removes, both issues ship in the same release.

## Template

````markdown
# <Imperative title: what changes>

> GitHub: #<N> · Nature: feature | bug | refactoring | removal · Status: mapping | ready | in-progress | closing

## Context
<Why this issue exists and how things stand today. Short prose.>

## Depends on
- `ISSUE-<name>.md` (#<N>): <what this issue needs from it>
<or: None.>

## 🟨 Story
<One sentence: who obtains what, and why.>

## Rules

### 🟦 R1: <the rule, one declarative sentence>

**🟩 E1: <short title>**
```
Given <concrete state, with values>
When  <one action>
Then  <observable outcome, with values>
```

**🟩 E2: <short title>**

| <input> | <outcome> |
|---------|-----------|
| …       | …         |

**🟥 Q2:** <question about this rule> · blocks: <E3, test 7, or nothing> · answer from: <who or what>

### 🟦 R2: <rule>
…

## Story questions
- **🟥 Q1:** <question about the whole story> · blocks: <items, or nothing>

## Contract
<Shape only: request, response, the possible statuses. When each status occurs is a rule.>

| Status | Body | Rule |
|--------|------|------|
| 200 | `{ … }` | R1, R2 |
| 400 | `MessageResponse` | R3 |

## Out of scope
- <what this issue deliberately does not do> (→ `ISSUE-<name>.md` when it became a new story)

## Known limits
- <a behaviour the story accepts and does not fix, with its consequence for the user>
<or: None.>

## Test list
| # | Origin | Level | Test | Kind | Status |
|---|--------|-------|------|------|--------|
| 1 | E1 | unit | <behaviour in domain words> | driving | todo |
| 2 | E4 | mvc | … | driving | blocked: Q2 |
| 3 | port: `HeaterPort` | unit | … | driving | todo |
| 4 | E1+E5 | unit | … | verification (from 1, 3) | todo |
| 5 | story | integration | … | driving | done |
````

### Sections

- **Header**: the GitHub number, the nature, and the status:

  | Status | Flow | Meaning |
  |---|---|---|
  | `mapping` | Refinement | Example Mapping in progress |
  | `ready` | Refinement → Development | The user declared the map ready; development may start |
  | `in-progress` | Development | Test list approved, TDD loop running |
  | `closing` | Development | Translation and publication |
- **Story**: one sentence. Two sentences that each stand alone are two stories.
- **Rules**: each rule is one declarative sentence, followed by its examples and its questions. An
  example takes the `Given` / `When` / `Then` form, or a table when the cases differ only in values.
- **Contract**: the shape of the REST surface the issue adds or changes. The behaviour behind each
  status lives in the rule the table links.
- **Out of scope**: what the issue deliberately leaves out, with a link to the draft that took it.
- **Known limits**: a behaviour the story accepts without fixing it, stated by its effect on the user.

### What stays out of an issue

A feature or bug issue states behaviour, never implementation; a refactoring is the exception (see
[Refactoring template](#refactoring-template)). The test for any decision is whether a user of the
system would notice the difference:

- **A user would notice**: the decision has an observable effect, and the effect is a rule with its
  examples. An ordering guarantee between two operations is a rule whose example is a sequence of
  instants. An undecided effect is a question.
- **A user would not notice**: names, packages, ports, locks. The code records the decision. When the
  reasoning behind it must outlive the issue, it goes to the document that owns the topic
  ([`DOCUMENTATION_STYLE.md`](DOCUMENTATION_STYLE.md) § 1), written during development.

An issue has no Design, Acceptance criteria, Tests, Documentation or Changelog section. Rules and
examples carry the acceptance criteria; the test list carries the tests; the closing checklist in
[`close-the-issue`](../.claude/skills/close-the-issue/SKILL.md) carries the documentation and the
changelog.

### What the map holds

The map holds the behaviour the issue adds or changes, and the current behaviour that no test protects
yet. A rule of the second kind carries `(current behaviour)` after its sentence: its tests guard
existing code rather than drive new code.

Current behaviour that a test already protects stays out of the map, since that test owns it. When
the story cannot be understood without it, the Context states it in one sentence.

A rule belongs to one issue only. When two issues need the same rule, the one whose story changes it
owns it, and the other relies on it through *Depends on* or states it in its Context.

### Test list

The test list is the Phase 0 list of [`TDD_WORKFLOW.md`](TDD_WORKFLOW.md), kept inside the draft so the
loop updates it at every cycle. Development writes it; a draft in refinement has no test list.

| Column | Values |
|---|---|
| Origin | An example (`E1`), a combination (`E1+E5`), a rule (`R3`, for a property test that verifies it on generated inputs), the whole story (`story`, for the integration test), or a port contract (`port: <Name>`, for a technical test with no example behind it) |
| Level | `unit`, `mvc`, `persistence` or `integration`, as defined in `TDD_WORKFLOW.md` § Test Levels |
| Kind | `driving`: expected to fail first, and its failure justifies the next production change. `verification (from …)`: expected to pass once the listed tests are green; it guards their combination |
| Status | `todo`, `done`, or `blocked: Q<n>` |

Each example is tested once per aspect, at the level that owns the rule. An example that spans two
levels splits into one test per level, each verifying a different part of it.

A property test verifies a rule written as a statement over every input, such as "every `enabled`
that is not a JSON boolean is rejected". It is a verification test from the example tests of that
rule, which still drive the implementation. When it fails, the minimal counterexample becomes a new
example of the rule, marked `(TDD, cycle <n>)`.

### Refactoring template

A refactoring changes structure only, so every decision in it is technical and belongs in the issue.
Its invariants take the place of the rules: each states what must not change and how it is verified.

````markdown
# <Imperative title: what changes>

> GitHub: #<N> · Nature: refactoring · Status: mapping | ready | in-progress | closing

## Context
<Why the structure must change, and how it stands today.>

## Depends on
<…, or: None.>

## Target
- <the structure after the change>

## Invariants

### I1: <what must not change, one declarative sentence>
<Why the change puts it at risk, when that is not obvious.>

Verified by: <the tests that guard it; a manual check, and why no test reaches it>

## Steps
Each step ends with `./gradlew build` and `./gradlew detekt` green.

1. **<step>:** <what changes; the risk, with ❗️ and the invariant it threatens>

## Questions
- **🟥 Q1:** <question> · blocks: <step or nothing> · answer from: <who or what>

## Out of scope
## Known limits
````

- **Invariants** are numbered `I1`, `I2`, …, with the rules of [Identifiers](#identifiers). An invariant
  that no test verifies names the manual check; that check is part of closing the issue.
- **Steps** are ordered. A step that leaves the build red is split, or merged with the next one.

### Markers during the loop

- **A question answered while the draft is open** stays visible as `**🟥 Q2:** ✔ → R5`, so the origin
  of `R5` can be traced.
- **A card or test added during the TDD loop** carries `(TDD, cycle <n>)`. The markers show what
  changed after the map was approved.

---

# Design notes

**Colours as emoji.** Markdown has no portable colour. GitHub strips inline `style` attributes,
LaTeX colour renders only on GitHub, and GitHub alert blocks carry fixed colours with a different
meaning. Coloured square emoji render the same on GitHub, in an IDE preview and in a terminal, and
they survive the translation unchanged.

**Headings in English, content in Italian.** The headings form the structure that tools and skills
look for; keeping them fixed means the translation at closing touches content only.

**The contract holds the shape, the rules hold the behaviour.** A status table that also said when
each status occurs would restate the rules, and the two copies would diverge at the first edit.

**No design section.** A design written in refinement either restates a rule in technical terms,
where development cannot find it among the rules and does not test it, or fixes an internal choice
before the tests that would shape it exist.

**Identifiers across the whole issue.** A number scoped to its rule would change when an example
moves to another rule, and every citation in the test list and the audit trail would break with it.

**A refactoring keeps its technical decisions.** The rule that keeps implementation out of an issue
protects behaviour from being fixed before the tests that shape it. A refactoring has no behaviour to
protect, and its risk sits in the structure: what it must preserve, and which of those guarantees no
test checks.

**A removal asserts no absence.** A test that a removed endpoint returns `404` checks one path among
infinitely many that do not exist; a new route exposing the same data would pass it. Once the code is
gone, the framework produces the `404`, so the test never fails first and drives nothing. It would
also keep the removed thing named in the suite forever, where `CHANGELOG.md` is the place for history.

**Invariants carry no colour.** They are not Example Mapping cards: no story generates them, and no
example illustrates them.

**Words for test status.** A white square for `todo` would read as a fifth card colour next to the
four coloured squares of the map.
