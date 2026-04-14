# Chunking Strategies for Local RAG

## Why chunking exists

A local retrieval system does not search full documents directly in most
practical setups. It searches smaller pieces of text that can be embedded,
stored and ranked efficiently. The quality of chunking affects recall,
precision, source attribution and the ability to assemble useful context for a
language model.

When chunks are too small, important ideas are fragmented and queries may match
isolated words without enough semantic context. When chunks are too large, the
embedding can blur several topics together and retrieval quality becomes less
predictable. Large chunks also reduce the number of distinct retrieval units and
make it harder to attribute the answer to a specific section.

## Fixed-size chunking

Fixed-size chunking creates predictable chunk lengths. This is useful when the
system needs stable embedding cost, consistent storage behavior and a simple
baseline for experiments. Overlap is often added so that boundary sentences do
not disappear when a concept happens to be split between two adjacent chunks.

The main weakness of fixed-size chunking is that document structure is ignored.
A heading may be separated from the section body. A function declaration may end
up in one chunk and the implementation in another. The chunk is valid as text
but weaker as an explanation unit.

## Structure-aware chunking

Structure-aware chunking keeps headings, paragraphs, file boundaries and other
logical separators. This helps preserve meaning and produces better metadata for
future retrieval, citations and debugging. In markdown documents, section
headings often become natural retrieval anchors. In source code, file-level or
block-level boundaries can preserve cohesive logic.

The tradeoff is that chunk sizes become less uniform. Some sections may be short
and some may be long, so a structure-aware strategy often needs fallback rules
for oversized sections and minimum size thresholds for tiny fragments.

## Recommendation

For a practical local document index, fixed-size chunking is a good baseline and
structure-aware chunking is usually the better default retrieval representation.
Both should be measured on the same corpus because indexing architecture should
support comparison rather than forcing one irreversible policy too early.
