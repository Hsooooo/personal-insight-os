-- Vector embeddings for similarity search in Ask RAG
ALTER TABLE questions ADD COLUMN IF NOT EXISTS embedding vector(1536);
ALTER TABLE insights ADD COLUMN IF NOT EXISTS embedding vector(1536);

-- HNSW works on empty tables; IVFFlat can fail without enough rows
CREATE INDEX IF NOT EXISTS idx_questions_embedding
    ON questions USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_insights_embedding
    ON insights USING hnsw (embedding vector_cosine_ops);
