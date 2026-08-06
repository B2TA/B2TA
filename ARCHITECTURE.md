# Architecture

## Overview

Standalone web app for TA grading with AI-powered rubric-to-passage matching.

## Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 19 SPA, Vite 8, Tailwind CSS v4, deployed on AWS Amplify |
| Backend | Java 21, Spring Boot 3.3, two ECS Fargate services (API + Worker) |
| Database | PostgreSQL 16 on Amazon RDS |
| File Storage | Amazon S3 (pre-signed URLs for direct browser upload/download) |
| AI | Amazon Bedrock (Claude Sonnet 4 for analysis, Haiku 4.5 for comments) |
| Auth | Amazon Cognito (admin-created accounts, JWT validation) |
| Messaging | Amazon SQS (async job queue for ingestion and analysis) |

## Service Boundaries

- **API Service** — Synchronous REST endpoints (save, load, pre-signed URLs, comment generation). Scales on CPU.
- **Worker Service** — Polls SQS for long-running jobs (text extraction, rubric parsing, Bedrock analysis). Scales on queue depth.

## Key Design Decisions

- PostgreSQL over DynamoDB — relational joins for the review screen, ACID transactions for atomic saves.
- Two ECS services from one codebase — keeps 60s Bedrock calls from blocking 200ms saves.
- Pre-signed S3 URLs — files go browser to S3 directly, never through the backend.
- Credentials stored as environment variables (prototype; no Secrets Manager).
- Stdout logging only (no CloudWatch log groups for prototype).

## Security

- Cognito JWT validated on every API request.
- Tenant isolation: every query filters by TA ID. Cross-tenant access returns 404.
- Student names and feedback text are not logged.
