# AI Agent Service Implementation Plan

Date: 2026-06-03

## Phase 1: Backend scaffolding

1. Create `AiAgentApplication`
2. Add `bootstrap.yml` and `application.yml`
3. Add AI agent constants, entities, DTOs, and VOs
4. Add SQL migration script for `t_ai_knowledge_document`
5. Add Elasticsearch config and AI agent properties

## Phase 2: Knowledge document lifecycle

1. Implement MySQL mapper and service for document metadata
2. Implement text and file upload endpoints
3. Implement document page and detail endpoints
4. Implement disable and delete flow
5. Add tests for lifecycle operations

## Phase 3: Indexing and retrieval

1. Implement paragraph-aware segment splitter
2. Implement embedding generation integration
3. Implement Elasticsearch index initialization
4. Implement segment bulk indexing and delete-by-document
5. Implement BM25 query
6. Implement semantic kNN query
7. Implement merge and rerank
8. Add retrieval debug endpoint
9. Add tests for segmentation and score merge logic

## Phase 4: Chat memory and AI chat

1. Implement Redis session memory service
2. Enforce latest 10 rounds retention
3. Implement assistant interface and chat service
4. Reuse or adapt business tools for article/search/shop
5. Implement chat history and clear-session endpoints
6. Add tests for memory retention behavior

## Phase 5: Frontend

1. Add AI chat page
2. Add admin knowledge management page
3. Add admin retrieval debug page
4. Wire streaming chat and session history

## Phase 6: Verification

1. Import sample knowledge files
2. Verify upload, reindex, delete, and disable flows
3. Verify hybrid retrieval with debug endpoint
4. Verify chat memory truncation
5. Verify end-to-end chat answers grounded in indexed knowledge
6. Update root startup instructions if new service steps are required
