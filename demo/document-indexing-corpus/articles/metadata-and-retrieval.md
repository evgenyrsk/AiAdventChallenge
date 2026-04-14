# Metadata and Retrieval Quality

## Why metadata matters

Embeddings alone are not enough for a practical retrieval system. A chunk should
carry metadata that explains where it came from and how it fits into the source
document. This is useful for ranking, filtering, debugging, result display and
future answer citations.

At minimum, each chunk should have:

- a stable chunk identifier
- a source identifier
- a title or file name
- a section label
- the chunking strategy that produced it

## Useful additional metadata

File path, document type, document identifier and text positions are valuable
even if the first MVP does not use them directly. These fields become important
when the retrieval layer adds filters, reindexing, source cards or context
assembly limits.

PDF page numbers are especially useful because users often ask questions about
where information was found. If page metadata exists, the system can later show
clear citations without redesigning the storage model.

## Metadata and trust

When a retrieval system surfaces a chunk with clear metadata, developers can
reason about errors faster. If the result comes from the wrong file, the problem
might be ranking or chunking. If the correct file is found but the wrong
section is returned, the issue might be chunk granularity or section parsing.

Metadata therefore improves both product behavior and engineering velocity.
