# Office DOCX judging

## Supported scope

Stage 5 hardens the existing document-formatting exercise. It accepts only OOXML
Word `.docx` packages. Office choice questions may still be tagged Word, Excel,
or PowerPoint; that quiz metadata does not mean XLSX or PPTX file judging exists.

Unsupported file-judging formats include `.doc`, `.docm`, `.xlsx`, `.xlsm`,
`.pptx`, encrypted/password-protected Office files, macro packages, embedded
objects, and documents with external relationships. The service never executes
macros, Microsoft Office, or LibreOffice.

## Upload and package validation

Uploads are untrusted input. The HTTP multipart default is 10 MiB per file and
11 MiB per request. The Office service independently enforces a 10 MiB stream
limit before Apache POI opens the file. Defaults may be lowered through the
`OFFICE_*` settings in `.env.example`.

The validator requires all of the following:

- a simple `.docx` display filename without path separators, drive paths, or
  traversal components;
- an allowed DOCX/ZIP MIME type plus a ZIP signature;
- required OOXML entries and the DOCX main-part content type;
- no duplicate or unsafe ZIP entry names;
- no macros, embedded objects, external relationships, or encrypted OLE2
  wrapper;
- no more than 2,048 entries, 8 MiB per expanded entry, 32 MiB total expanded
  content, and a minimum inflate ratio of 0.01.

Apache POI `poi-ooxml` is 5.4.1. This is the first maintained line containing the
duplicate-entry fix for CVE-2025-31672. POI `ZipSecureFile` limits remain enabled;
the project validator provides an additional streaming boundary before parsing.
Client errors are stable categories and never include POI stack traces or server
paths.

## Storage lifecycle

The original filename is display-only. The service streams an upload to a unique
temporary file under the configured storage root, validates and parses it, then
atomically moves it to a server-generated UUID `.docx` filename. The database
stores that storage ID for new records; historical absolute paths remain readable
only when they resolve to a regular, non-symlink file directly under the storage
root.

If parsing or persistence fails, temporary and newly committed files are removed.
Rejected student uploads retain a bounded `FAILED` database result but no untrusted
file. Exercise deletion commits database deletion before file cleanup, and a
scheduled reconciler removes only old, UUID-named, unreferenced managed files.
It never follows symlinks or deletes user-provided paths.

Reference documents use the same validator and parser before replacing the active
reference. Only the owning teacher or an administrator may download a reference.
Students may download only their own submissions; teachers may download submissions
for exercises they own.

## Canonical model and normalization

POI objects never enter the comparator or database. A parse produces immutable
paragraph, run, and table records.

Supported Word properties are:

- paragraph order and text;
- paragraph alignment, first/left/right indent in twips, before/after spacing in
  twips, and line spacing in hundredths of a line;
- every run's text, direct font family, size in hundredths of a point, bold,
  italic, underline, and color;
- table order, row count, column count, cell order, and cell text.

Text normalization converts CRLF and CR to LF, applies Unicode NFC, preserves
leading and internal spaces, and removes trailing whitespace only at paragraph or
cell boundaries. Comparisons are case-sensitive and exact. Font names are
case-normalized. Missing direct formatting is a canonical explicit value; Stage 5
does not resolve theme, character-style, paragraph-style, or document-default
cascades.

Unsupported judging properties include page rendering, margins/sections, images,
merged-cell formatting, borders/shading, tracked changes, fields, shapes, SmartArt,
and pixel-level layout. Office judging is deterministic rule comparison, not a
pixel-perfect Microsoft Word rendering engine.

## Scoring and results

The judge version is `office-docx-v1`. Every supported canonical rule has equal
weight. A failed rule receives zero for that rule; the final integer score is the
nearest percentage in the inclusive range 0–100. There is no fuzzy comparison or
undocumented tolerance.

Results include total/earned score, pass state, bounded error items, total error
count, truncation state, judge version, and judge timestamp. At most 200 error
items are retained and the serialized JSONB detail is capped at 256 KiB. Reference
paragraph and table text is not returned to students; details identify the target
and mismatch category and may expose only formatting expectations.

Submission states are `PENDING`, `JUDGING`, `COMPLETED`, and `FAILED`, while legacy
`AUTO_CHECKED`, `NEEDS_REVIEW`, and `REVIEWED` records remain readable. A failed
parse is persisted as `FAILED` with a sanitized category and does not remain
pending.

## Resource model and tests

Parsing is file-backed, opens OOXML packages with `PackageAccess.READ`, and closes
`OPCPackage`, `XWPFDocument`, ZIP streams, and file streams with try-with-resources.
Read-only package access prevents concurrent judges from mutating a shared
reference document. Element and text complexity have hard caps.
Each backend instance admits at most four concurrent Office judges by default;
additional valid requests wait for a permit rather than spawning unbounded parser
threads.

`OfficeDocumentSecurityIT` is part of the normal backend Docker test task and is
not skipped. It covers forged and corrupted files, oversized and high-compression
packages, huge XML, traversal, macros/embeddings, encrypted packages, external
relationships, and malformed OOXML. Comparator tests require ten identical runs
to produce an identical model-level result, and storage tests exercise 20 concurrent
unique uploads plus immediate Windows-compatible deletion. The integration suite
also judges 20 simultaneous submissions, checks that all storage IDs and results
remain isolated, and verifies the configured peak of four active parsers.
