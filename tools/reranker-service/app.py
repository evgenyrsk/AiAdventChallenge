from __future__ import annotations

import os
from functools import lru_cache
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import CrossEncoder


DEFAULT_MODEL = os.getenv("RERANKER_MODEL", "BAAI/bge-reranker-base")
MAX_CANDIDATES = int(os.getenv("RERANKER_MAX_CANDIDATES", "24"))

app = FastAPI(title="AiAdventChallenge Reranker", version="1.0.0")


class Candidate(BaseModel):
    chunkId: str
    text: str
    title: str
    relativePath: str
    section: str
    retrievalScore: float
    semanticScore: float
    keywordScore: float


class RerankRequest(BaseModel):
    query: str
    candidates: List[Candidate]
    top_k_after: int = Field(alias="top_k_after")
    min_score_threshold: Optional[float] = Field(default=None, alias="min_score_threshold")
    timeout_ms: Optional[int] = Field(default=None, alias="timeout_ms")
    query_context: Optional[str] = Field(default=None, alias="query_context")


class RerankedCandidate(BaseModel):
    chunkId: str
    rerankScore: float
    rank: int
    title: str
    relativePath: str
    section: str
    retrievalScore: float
    semanticScore: float
    keywordScore: float
    filteredOut: bool = False
    filterReason: Optional[str] = None


class RerankDebugInfo(BaseModel):
    inputCandidateCount: int
    outputCandidateCount: int
    topKAfter: int
    thresholdApplied: Optional[float] = None
    timedOut: bool = False
    fallbackUsed: bool = False
    fallbackReason: Optional[str] = None


class RerankResponse(BaseModel):
    provider: str = "local_http"
    model: str
    results: List[RerankedCandidate]
    debug: RerankDebugInfo


@lru_cache(maxsize=1)
def get_model() -> CrossEncoder:
    return CrossEncoder(DEFAULT_MODEL)


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "model": DEFAULT_MODEL,
        "loaded": get_model.cache_info().currsize > 0,
    }


@app.post("/rerank", response_model=RerankResponse)
def rerank(request: RerankRequest) -> RerankResponse:
    if not request.query.strip():
        raise HTTPException(status_code=400, detail="query must not be blank")
    if not request.candidates:
        return RerankResponse(
            model=DEFAULT_MODEL,
            results=[],
            debug=RerankDebugInfo(
                inputCandidateCount=0,
                outputCandidateCount=0,
                topKAfter=max(request.top_k_after, 1),
            ),
        )
    if len(request.candidates) > MAX_CANDIDATES:
        raise HTTPException(
            status_code=400,
            detail=f"too many candidates: {len(request.candidates)} > {MAX_CANDIDATES}",
        )

    query = request.query.strip()
    model = get_model()
    pairs = [[query, candidate.text] for candidate in request.candidates]
    scores = model.predict(pairs)

    ranked = []
    for candidate, score in zip(request.candidates, scores):
        ranked.append(
            RerankedCandidate(
                chunkId=candidate.chunkId,
                rerankScore=float(score),
                rank=0,
                title=candidate.title,
                relativePath=candidate.relativePath,
                section=candidate.section,
                retrievalScore=candidate.retrievalScore,
                semanticScore=candidate.semanticScore,
                keywordScore=candidate.keywordScore,
            )
        )

    ranked.sort(key=lambda item: item.rerankScore, reverse=True)

    threshold = request.min_score_threshold
    if threshold is not None:
        ranked = [item for item in ranked if item.rerankScore >= threshold]

    ranked = ranked[: max(request.top_k_after, 1)]

    for index, item in enumerate(ranked, start=1):
        item.rank = index

    return RerankResponse(
        model=DEFAULT_MODEL,
        results=ranked,
        debug=RerankDebugInfo(
            inputCandidateCount=len(request.candidates),
            outputCandidateCount=len(ranked),
            topKAfter=max(request.top_k_after, 1),
            thresholdApplied=threshold,
            timedOut=False,
            fallbackUsed=False,
            fallbackReason=None,
        ),
    )
