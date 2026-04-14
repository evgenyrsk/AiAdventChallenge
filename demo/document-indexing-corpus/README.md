# Local Retrieval Demo Corpus

This directory is a stable corpus for the document indexing feature.

It is intentionally small enough to inspect manually and large enough to produce
meaningful chunking, embeddings and metadata.

## Included materials

- markdown guides
- plain text notes
- source-like files
- XML configuration sample

## Goals

The corpus is designed to validate:

- fixed-size chunking
- structure-aware chunking
- embedding generation
- SQLite index persistence
- metadata quality for future citations

## Retrieval expectations

Questions about indexing, chunking, metadata, architecture, pipelines and
retrieval should find relevant chunks in this corpus.

The structure-aware strategy should preserve headings and section boundaries
better than fixed-size chunking, while fixed-size should provide more uniform
chunk lengths and easier baseline comparisons.
