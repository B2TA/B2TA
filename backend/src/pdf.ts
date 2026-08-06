import { getDocument, VerbosityLevel } from "pdfjs-dist/legacy/build/pdf.mjs"

export type PdfExtractionResult = {
  status: "success"
  text: string
  charCount: number
} | {
  status: "failed"
  reason: "password_protected" | "no_extractable_text" | "unreadable_file"
}

function normalizeText(pages: string[]): string {
  return pages
    .map((page) => page.replace(/[ \t]+/g, " ").trim())
    .filter(Boolean)
    .join("\n\n")
    .trim()
}

export async function extractPdfText(
  data: Buffer,
): Promise<PdfExtractionResult> {
  const loadingTask = getDocument({
    data: new Uint8Array(data),
    useSystemFonts: true,
    verbosity: VerbosityLevel.ERRORS,
  })

  try {
    const document = await loadingTask.promise
    const pages: string[] = []
    for (let pageNumber = 1; pageNumber <= document.numPages; pageNumber += 1) {
      const page = await document.getPage(pageNumber)
      const content = await page.getTextContent()
      pages.push(
        content.items
          .filter(
            (item): item is typeof item & { str: string } => "str" in item,
          )
          .map((item) => item.str)
          .join(" "),
      )
    }

    const text = normalizeText(pages)
    if (!text) return { status: "failed", reason: "no_extractable_text" }
    return { status: "success", text, charCount: text.length }
  } catch (error) {
    if (
      typeof error === "object" &&
      error !== null &&
      "name" in error &&
      error.name === "PasswordException"
    ) {
      return { status: "failed", reason: "password_protected" }
    }
    return { status: "failed", reason: "unreadable_file" }
  } finally {
    await loadingTask.destroy()
  }
}
