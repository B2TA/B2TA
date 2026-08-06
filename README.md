# B2TA — Rubric-Linked Grading Assistant

AI-powered web app that helps Teaching Assistants grade faster by connecting rubric criteria directly to evidence passages in student submissions.

## Quick Start

### Frontend (prototype)
```bash
pnpm install
pnpm dev
```
Preview runs on port 8443.

### Backend
```bash
cd backend
./mvnw clean compile
```
Requires Java 21.

## Project Structure

```
src/              # React frontend (prototype + production SPA)
backend/          # Java Spring Boot (api + worker + common modules)
infra/            # AWS CloudFormation templates
.kiro/specs/      # Feature specifications
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 19, Vite 8, Tailwind CSS v4, TypeScript |
| Backend | Java 21, Spring Boot 3.3, Maven multi-module |
| Database | PostgreSQL 16 (Amazon RDS) |
| Storage | Amazon S3 |
| AI | Amazon Bedrock (Claude Sonnet 4 + Haiku 4.5) |
| Auth | Amazon Cognito |
| Messaging | Amazon SQS |
| Deploy | AWS Amplify (frontend), ECS Fargate (backend) |
