import {
  BedrockRuntimeClient,
  ConverseCommand,
  type ConverseCommandOutput,
} from "@aws-sdk/client-bedrock-runtime"

import type { Rubric } from "./types.js"

export type EvidenceCandidate = {
  criterionId: string
  passageStart?: number
  passageEnd?: number
  passageText: string
  rationale: string
  confidence: number
}

export type EvidenceRequest = {
  rubric: Rubric
  submissionText: string
}

export interface EvidenceSuggester {
  suggest(input: EvidenceRequest): Promise<EvidenceCandidate[]>
}

type BedrockClient = {
  send(command: ConverseCommand): Promise<ConverseCommandOutput>
}

export class EvidenceSuggestionError extends Error {}

export class BedrockEvidenceSuggester implements EvidenceSuggester {
  readonly #client: BedrockClient
  readonly #modelId: string

  constructor(
    client: BedrockClient = new BedrockRuntimeClient({
      region: process.env.AWS_REGION ?? "us-east-1",
    }),
    modelId = process.env.BEDROCK_MODEL_ID ??
      "global.anthropic.claude-sonnet-4-6",
  ) {
    this.#client = client
    this.#modelId = modelId
  }

  async suggest({ rubric, submissionText }: EvidenceRequest) {
    const criteria = rubric.criteria.map((criterion) => ({
      id: criterion.id,
      title: criterion.title,
      description: criterion.description,
    }))
    const command = new ConverseCommand({
      modelId: this.#modelId,
      system: [
        {
          text: "You identify exact submission passages that may help a teaching assistant evaluate rubric criteria. Never assign, recommend, infer, or discuss scores or performance levels.",
        },
      ],
      messages: [
        {
          role: "user",
          content: [
            {
              text: `Return JSON only in this shape: {"suggestions":[{"criterionId":"...","passageText":"exact text copied from the submission","rationale":"why it may address the criterion","confidence":0.8}]}. Suggest at most two concise passages per criterion. Do not include grades, scores, or performance-level recommendations.\n\nRUBRIC\n${JSON.stringify(criteria)}\n\nSUBMISSION\n${submissionText}`,
            },
          ],
        },
      ],
      inferenceConfig: { maxTokens: 4_096, temperature: 0 },
    })

    let response: ConverseCommandOutput
    try {
      response = await this.#client.send(command)
    } catch {
      throw new EvidenceSuggestionError(
        "Bedrock could not generate evidence suggestions",
      )
    }

    const text = response.output?.message?.content
      ?.map((block) => block.text ?? "")
      .join("")
      .trim()
    if (!text) throw new EvidenceSuggestionError("Bedrock returned no text")

    try {
      const cleaned = text
        .replace(/^```(?:json)?\s*/i, "")
        .replace(/\s*```$/, "")
      const parsed = JSON.parse(cleaned) as { suggestions?: unknown }
      if (!Array.isArray(parsed.suggestions))
        throw new Error("Missing suggestions")
      return parsed.suggestions as EvidenceCandidate[]
    } catch {
      throw new EvidenceSuggestionError(
        "Bedrock returned malformed evidence suggestions",
      )
    }
  }
}
