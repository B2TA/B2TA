import type { ObjectStore, StoredObject } from "../src/object-store.js"

export class FakeObjectStore implements ObjectStore {
  readonly objects = new Map<string, StoredObject>()

  async createUploadUrl(key: string): Promise<string> {
    return `https://uploads.example.test/${encodeURIComponent(key)}`
  }

  async createDownloadUrl(key: string): Promise<string> {
    return `https://downloads.example.test/${encodeURIComponent(key)}`
  }

  async get(key: string): Promise<StoredObject> {
    const object = this.objects.get(key)
    if (!object) throw new Error("Object not found")
    return object
  }

  async delete(key: string): Promise<void> {
    this.objects.delete(key)
  }

  async put(
    key: string,
    bytes: Uint8Array,
    contentType: "application/pdf",
  ): Promise<void> {
    this.objects.set(key, { bytes, contentType })
  }
}
