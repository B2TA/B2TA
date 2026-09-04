import {
  DeleteObjectCommand,
  GetObjectCommand,
  PutObjectCommand,
  S3Client,
} from "@aws-sdk/client-s3"
import { getSignedUrl } from "@aws-sdk/s3-request-presigner"

export const MAX_PDF_BYTES = 25 * 1024 * 1024

export type StoredObject = {
  bytes: Uint8Array
  contentType: string | undefined
}

export interface ObjectStore {
  createUploadUrl(key: string, contentType: "application/pdf"): Promise<string>
  createDownloadUrl(key: string): Promise<string>
  delete(key: string): Promise<void>
  get(key: string): Promise<StoredObject>
  put(
    key: string,
    bytes: Uint8Array,
    contentType: "application/pdf",
  ): Promise<void>
}

export class S3ObjectStore implements ObjectStore {
  constructor(
    private readonly client: S3Client,
    private readonly bucket: string,
  ) {}

  static fromEnv(): S3ObjectStore {
    const bucket = process.env.DOCUMENT_BUCKET
    if (!bucket) throw new Error("DOCUMENT_BUCKET is required")
    return new S3ObjectStore(
      new S3Client({ region: process.env.AWS_REGION ?? "us-west-2" }),
      bucket,
    )
  }

  async createUploadUrl(
    key: string,
    contentType: "application/pdf",
  ): Promise<string> {
    return getSignedUrl(
      this.client,
      new PutObjectCommand({
        Bucket: this.bucket,
        Key: key,
        ContentType: contentType,
      }),
      { expiresIn: 15 * 60 },
    )
  }

  async createDownloadUrl(key: string): Promise<string> {
    return getSignedUrl(
      this.client,
      new GetObjectCommand({ Bucket: this.bucket, Key: key }),
      { expiresIn: 15 * 60 },
    )
  }

  async get(key: string): Promise<StoredObject> {
    const result = await this.client.send(
      new GetObjectCommand({ Bucket: this.bucket, Key: key }),
    )
    if (!result.Body) throw new Error("Object body is missing")
    return {
      bytes: await result.Body.transformToByteArray(),
      contentType: result.ContentType,
    }
  }

  async delete(key: string): Promise<void> {
    await this.client.send(
      new DeleteObjectCommand({ Bucket: this.bucket, Key: key }),
    )
  }

  async put(
    key: string,
    bytes: Uint8Array,
    contentType: "application/pdf",
  ): Promise<void> {
    await this.client.send(
      new PutObjectCommand({
        Bucket: this.bucket,
        Key: key,
        Body: bytes,
        ContentType: contentType,
      }),
    )
  }
}

export function submissionObjectKey(
  sessionId: string,
  submissionId: string,
): string {
  return `sessions/${sessionId}/submissions/${submissionId}/original.pdf`
}
