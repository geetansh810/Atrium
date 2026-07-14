// Net-new mock-only domain — a docs/embeddings schema already exists in
// core-api (15 §4) but no endpoints are wired up yet (MF-5). Fixture-only in
// both VITE_USE_MOCKS modes; useKnowledgeDocs() is the future swap point once
// real Knowledge endpoints land.
import knowledgeJson from "../mocks/knowledge.json";
import type { KnowledgeDoc } from "../types";

export function useKnowledgeDocs(): KnowledgeDoc[] {
  return knowledgeJson.docs as KnowledgeDoc[];
}
