/**
 * File upload helper that uploads files to S3 using pre-signed URLs.
 * Supports parallel uploads with concurrency limits and progress tracking.
 */

import type { UploadUrl } from "../types";

interface UploadProgress {
  completed: number;
  total: number;
  failed: string[];
}

/**
 * Upload a single file to S3 via pre-signed URL.
 */
export async function uploadFileToS3(file: File, uploadUrl: string): Promise<void> {
  const response = await fetch(uploadUrl, {
    method: "PUT",
    body: file,
    headers: {
      "Content-Type": file.type || "application/octet-stream",
    },
  });
  if (!response.ok) {
    throw new Error(`Upload failed for ${file.name}: ${response.status}`);
  }
}

/**
 * Upload multiple files in parallel with a concurrency limit.
 * Calls onProgress after each file completes.
 */
export async function uploadFilesParallel(
  files: File[],
  uploadUrls: UploadUrl[],
  options: {
    concurrency?: number;
    onProgress?: (progress: UploadProgress) => void;
  } = {}
): Promise<{ objectKeys: string[]; failed: string[] }> {
  const { concurrency = 5, onProgress } = options;
  const objectKeys: string[] = [];
  const failed: string[] = [];
  let completed = 0;

  // Match files to their upload URLs by filename
  const fileUrlMap = new Map<string, UploadUrl>();
  for (const u of uploadUrls) {
    fileUrlMap.set(u.filename, u);
  }

  // Process files in batches of `concurrency`
  const chunks: File[][] = [];
  for (let i = 0; i < files.length; i += concurrency) {
    chunks.push(files.slice(i, i + concurrency));
  }

  for (const chunk of chunks) {
    const results = await Promise.allSettled(
      chunk.map(async (file) => {
        const urlInfo = fileUrlMap.get(file.name);
        if (!urlInfo) {
          throw new Error(`No upload URL for ${file.name}`);
        }
        await uploadFileToS3(file, urlInfo.uploadUrl);
        return urlInfo.objectKey;
      })
    );

    for (let i = 0; i < results.length; i++) {
      const result = results[i];
      completed++;
      if (result.status === "fulfilled") {
        objectKeys.push(result.value);
      } else {
        failed.push(chunk[i].name);
      }
      onProgress?.({ completed, total: files.length, failed: [...failed] });
    }
  }

  return { objectKeys, failed };
}
