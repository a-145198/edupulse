# Project Status Document: Offline Homework-Helper App

## 1. What This Project Is

This is an entry for the **iQOO Hackathon 2026, Hyderabad City Battle**,
under the **Smart Education track**, built by a 3-person team. The
product is an **offline homework-helper app**, with a specific focus on
**handwritten student reasoning analysis** — not just answer generation,
but understanding how a student worked through a problem from a photo
of their handwriting.

Target hardware: an iQOO device (final hackathon device). No iQOO or
Vivo device is currently available for testing; a Pixel 6a is being used
as the current practice and benchmark device instead.

The problem set used for testing so far (physics, chemistry, math word
problems at three difficulty tiers) reflects the test content chosen,
not a fixed constraint on who the product is for or what level it must
remain at. The pipeline's logic (OCR extraction, error correction,
structured solving) is not tied to any particular grade level or age
group.

## 2. The Core Mission

Build a pipeline that:

1. Takes a photo of a handwritten question.
2. Extracts the text offline, with no internet dependency, using
   PaddleOCR (chosen over Mathpix OCR specifically because it can run
   fully offline/on-device).
3. Passes the extracted text to a local, offline LLM (Gemma, run via
   `litert-lm`) to produce a structured solution: Given, Formula,
   Substitution, Final Answer.
4. Does this reliably enough to demo live at a hackathon, on
   real handwritten input, without internet access.

The "offline" constraint is not incidental — it is the core
differentiator of the product concept. Every design decision (local
OCR, local LLM, no cloud API calls) traces back to this requirement.

## 3. Where the Project Actually Stands

A working end-to-end pipeline exists: photo -> PaddleOCR -> text cleanup
-> Gemma -> structured answer. It has been validated on:

- One kinematics problem (fully correct after multiple rounds of OCR
  tuning).
- A batch of six problems across physics, chemistry, and math at three
  difficulty levels each, used specifically to test generalization
  beyond the first single test case.

Result of that batch test: 4 of 6 images produced fully correct
answers. One image failed OCR almost completely (rejected safely by a
newly added quality gate, without reaching the LLM). One image produced
a **confidently wrong answer** with no visible indication of error,
because a handwritten number was misread as a word ("15N" read as
"isn") and the LLM computed with the wrong text rather than flagging
it as suspicious.

## 4. Where We Are Currently Stuck

The unresolved problem is **silent numeric/unit corruption from OCR** —
cases where PaddleOCR misreads a number or unit as a different,
plausible-looking short word, with high confidence, producing a
complete and well-formatted but factually incorrect answer from the
LLM. Two known instances:

- "15N" misread as "isn" (a number and its unit collapsed into one
  wrong word).
- "11 grams" split across a line break into "1," and "grams"
  separately, losing a digit.

Confidence scores from PaddleOCR do not catch either case — both scored
above 0.95, indistinguishable from correctly-read text. A regex-based
attempt to catch these by checking for suspect words near units or unit-
related verbs was tried and failed for a structural reason: OCR often
deletes the unit itself along with the number, leaving nothing for a
unit-adjacency rule to anchor against. A looser version caught the
error but also flagged a legitimate chemistry abbreviation ("STP") as
suspicious, since shape-based pattern matching cannot distinguish a
corrupted token from a legitimate short domain term without an explicit
list of known valid terms.

This is the live decision point: whether to build an allow-list-based
regex system (imperfect, incremental, no added user step) or add a
manual text-confirmation step before solving (catches all OCR errors,
adds a review step every time).

## 5. Constraints Operating on This Project

**Hardware/testing constraint:** No physical iQOO or Vivo device is
available yet. All testing happens on a Pixel 6a and the developer's
own machine (macOS 26). This means performance and behavior on the
actual target hardware are currently unverified.

**Team/time constraint:** This is a 3-person hackathon team, implying a
fixed, competition-driven deadline rather than open-ended development
time. Effort spent on any one piece (e.g., perfecting OCR error
correction) has a direct opportunity cost against other required parts
of the product (UI, the reasoning-analysis feature itself, the demo).

**Data constraint:** The test set currently in use is six labeled
images, handwritten under broadly similar conditions (similar paper,
similar lighting, limited handwriting-style variation). This is
sufficient to catch regressions but has been explicitly flagged as
insufficient to prove the pipeline generalizes to varied handwriting,
paper types, and photo conditions more broadly. A larger, more varied
test set has not yet been built.

**Author constraint:** The person operating this pipeline currently has
a hand issue preventing them from handwriting new test samples
independently; recent test images required someone else to write them
by hand, which limits how quickly new, varied handwriting samples can
be generated for testing.

**Model/runtime constraint:** The LLM runs locally via `litert-lm`
using a Gemma model file (`gemma-4-E2B-it-gpu.litertlm`) on a GPU
backend. This is confirmed working on the current development machine,
but has not been verified on the actual target hardware (iQOO device),
which may differ in available compute, memory, or GPU support.

## 6. What "Done" Looks Like for the Immediate Blocker

The immediate blocker (silent numeric corruption) does not need a
perfect, general solution to be acceptable for a hackathon demo. It
needs to be reliable enough that a live demo does not produce a visibly
wrong answer with no warning. Given the time constraint, the practical
bar is: catch the specific failure patterns already observed in testing
(number+unit collapsing into a word; digit loss at line breaks), reduce
the chance of a repeat of the "isn" incident during a live demo, and
document remaining known gaps rather than attempting to solve every
possible OCR failure mode before the deadline.
