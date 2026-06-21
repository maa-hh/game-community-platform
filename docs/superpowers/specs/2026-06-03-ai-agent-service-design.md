# AI Agent Service Design

Date: 2026-06-03

## Scope

Rebuild `demo/service/ai-agent-service` inside the current project with these constraints:

- Redis stores chat memory and keeps only the latest 10 rounds per session
- RAG indexes only AI knowledge documents maintained manually by admins
- Elasticsearch stores segmented knowledge documents and supports hybrid retrieval
- Retrieval strategy is BM25 plus semantic kNN, followed by service-side reranking
- LangChain4j remains the chat orchestration layer and existing business tools stay available

## Architecture

### Responsibilities

1. `AiChatService`
- Loads recent session memory from Redis
- Executes hybrid retrieval against Elasticsearch
- Builds prompt context
- Delegates answer generation and tool calling to LangChain4j assistant
- Persists the latest 10 rounds back to Redis

2. `KnowledgeDocumentService`
- Manages document metadata in MySQL
- Supports add, page, detail, disable, delete, and reindex operations

3. `KnowledgeIndexService`
- Splits documents into segments
- Generates embeddings
- Writes and deletes Elasticsearch segment documents

4. `KnowledgeRetrievalService`
- Runs BM25 and semantic kNN queries
- Deduplicates, normalizes, reranks, and truncates results
- Produces structured retrieval context for the LLM

5. `ChatMemoryService`
- Reads and writes Redis session memory
- Enforces latest 10 rounds retention and TTL

## Storage

### MySQL

Table: `t_ai_knowledge_document`

Suggested columns:

- `id`
- `title`
- `source_type`
- `source_name`
- `content_hash`
- `status`
- `index_status`
- `segment_count`
- `created_by`
- `updated_by`
- `create_time`
- `update_time`

Purpose:

- Source-of-truth for document lifecycle
- Upload deduplication and reindex/delete workflow control

### Elasticsearch

Index: `ai_knowledge_segment`

Suggested fields:

- `segmentId`
- `documentId`
- `title`
- `content`
- `contentPreview`
- `sourceName`
- `segmentOrder`
- `tags`
- `status`
- `createdAt`
- `embedding`

Mapping:

- `title`, `content`, `contentPreview` use Chinese analyzers with IK
- `embedding` uses `dense_vector`

### Redis

Key pattern:

- `ai:chat:memory:{sessionId}`

Value:

- JSON array of recent chat messages

Policy:

- A round equals one user message and one assistant message
- Keep the latest 10 rounds, or 20 messages
- Refresh TTL on every write

## Retrieval

### Query flow

1. Validate query text
2. Run BM25 search against `title + content`
3. Generate query embedding and run kNN search against `embedding`
4. Merge by `segmentId`
5. Normalize both score channels
6. Apply weighted rerank:

`final = 0.45 * bm25 + 0.55 * semantic + dual_hit_bonus`

7. Keep top results and build structured context for the model

### Segmentation

- Paragraph-first splitting
- Target 300 to 500 Chinese characters per segment
- 50 to 80 character overlap

## APIs

### User

- `POST /ai/chat`
- `GET /ai/chat/session/{sessionId}/messages`
- `DELETE /ai/chat/session/{sessionId}`

### Admin

- `POST /ai/knowledge/text`
- `POST /ai/knowledge/file`
- `GET /ai/knowledge/page`
- `GET /ai/knowledge/{documentId}`
- `PUT /ai/knowledge/{documentId}/disable`
- `DELETE /ai/knowledge/{documentId}`
- `POST /ai/knowledge/{documentId}/reindex`

### Debug

- `POST /ai/knowledge/search/debug`
- `GET /ai/knowledge/index/stats`

## Concurrency and consistency

- Upload uses `content_hash` to reject or detect duplicate content
- Reindex guarded by `index_status=INDEXING`
- Delete first changes MySQL lifecycle state, then deletes ES segments
- Session writes are whole-session rewrites with bounded message windows

## Frontend

### User view

- AI chat page
- Streaming responses
- Local session ID cache
- Session history and clear action

### Admin view

- Knowledge document add via text and file upload
- Document page and detail
- Disable, delete, and reindex actions
- Search debug panel showing BM25, semantic, and merged results
