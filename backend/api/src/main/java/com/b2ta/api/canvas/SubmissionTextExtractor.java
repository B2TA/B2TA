package com.b2ta.api.canvas;

import com.b2ta.api.canvas.dto.CanvasAttachment;
import com.b2ta.api.canvas.dto.CanvasSubmission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Pulls readable text out of a Canvas submission.
 *
 * <p>Dispatches on {@code submission_type}: text entries have their HTML stripped,
 * uploads are downloaded and parsed by extension.
 */
@Component
@Slf4j
public class SubmissionTextExtractor {

    /** Below this, a PDF is almost certainly a scan with no text layer. */
    private static final int SCANNED_DOCUMENT_THRESHOLD = 200;

    private static final long MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024;

    private final HttpClient httpClient;

    public SubmissionTextExtractor() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Extracts the submission's text.
     *
     * @return the text, or a failure carrying a message the TA can act on
     */
    public Extraction extract(CanvasSubmission submission) {
        String type = submission.getSubmissionType();
        if (type == null) {
            return Extraction.failure("This submission has no content to display.");
        }

        return switch (type) {
            case "online_text_entry" -> extractTextEntry(submission);
            case "online_upload" -> extractUpload(submission);
            case "online_url" -> Extraction.failure(
                    "This is a URL submission. Open it in Canvas to review it.");
            default -> Extraction.failure(
                    "Submissions of type '" + type + "' cannot be displayed here.");
        };
    }

    private Extraction extractTextEntry(CanvasSubmission submission) {
        String body = submission.getBody();
        if (body == null || body.isBlank()) {
            return Extraction.failure("This text submission is empty.");
        }
        return Extraction.success(stripHtml(body));
    }

    private Extraction extractUpload(CanvasSubmission submission) {
        if (submission.getAttachments() == null || submission.getAttachments().isEmpty()) {
            return Extraction.failure("This upload has no attached file.");
        }

        CanvasAttachment attachment = submission.getAttachments().get(0);
        if (attachment.getSize() != null && attachment.getSize() > MAX_ATTACHMENT_BYTES) {
            return Extraction.failure("This file is too large to display (over 25 MB).");
        }

        byte[] bytes;
        try {
            bytes = download(attachment.getUrl());
        } catch (Exception e) {
            log.warn("Could not download attachment {}", attachment.getId(), e);
            return Extraction.failure("Could not download this submission from Canvas.");
        }

        String filename = attachment.getFilename() == null ? "" : attachment.getFilename();
        String lower = filename.toLowerCase(java.util.Locale.ROOT);

        try {
            String text;
            if (lower.endsWith(".txt") || lower.endsWith(".md")) {
                text = new String(bytes, StandardCharsets.UTF_8);
            } else if (lower.endsWith(".pdf")) {
                text = PdfText.extract(bytes);
            } else if (lower.endsWith(".docx")) {
                text = DocxText.extract(bytes);
            } else {
                return Extraction.failure(
                        "Files of type '" + filename + "' cannot be displayed here.");
            }

            if (text.strip().length() < SCANNED_DOCUMENT_THRESHOLD) {
                // A near-empty extraction from a real file means a scan with no text
                // layer. Say so rather than showing a blank document.
                return Extraction.failure(
                        "Almost no text could be read from this file — it may be a scan.");
            }
            return Extraction.success(text);

        } catch (Exception e) {
            log.warn("Could not extract text from {}", filename, e);
            return Extraction.failure("Could not read the text of this submission.");
        }
    }

    /**
     * Downloads an attachment.
     *
     * <p>Attachment URLs carry a {@code verifier} query parameter and are
     * pre-authorized — no bearer token is sent. Attaching one would leak the Canvas
     * token to whatever host the download redirects to.
     */
    private byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<byte[]> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Attachment download returned " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Strips HTML to readable text, preserving paragraph breaks so the document splits
     * into paragraphs the way the student wrote it.
     */
    static String stripHtml(String html) {
        String text = html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
                // Block-level boundaries become blank lines before tags are dropped,
                // otherwise the whole essay collapses into one paragraph.
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li|blockquote|tr)\\s*>", "\n\n")
                .replaceAll("<[^>]+>", "");

        return unescapeEntities(text)
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static String unescapeEntities(String value) {
        return value
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&rsquo;", "'")
                .replace("&lsquo;", "'")
                .replace("&ldquo;", "\"")
                .replace("&rdquo;", "\"")
                .replace("&mdash;", "-")
                .replace("&ndash;", "-")
                // Ampersand last, so an escaped entity is not double-unescaped.
                .replace("&amp;", "&");
    }

    /** The outcome of an extraction attempt. */
    public record Extraction(String text, String error) {

        public static Extraction success(String text) {
            return new Extraction(text, null);
        }

        public static Extraction failure(String error) {
            return new Extraction(null, error);
        }

        public boolean failed() {
            return error != null;
        }
    }

    /** PDF text extraction, isolated so the dependency stays in one place. */
    static final class PdfText {
        static String extract(byte[] bytes) throws IOException {
            try (org.apache.pdfbox.pdmodel.PDDocument document =
                         org.apache.pdfbox.Loader.loadPDF(bytes)) {
                ParagraphAwareStripper stripper = new ParagraphAwareStripper();
                stripper.setSortByPosition(true);
                return stripper.getText(document);
            }
        }
    }

    /**
     * A {@link org.apache.pdfbox.text.PDFTextStripper} that recovers paragraph breaks
     * from line spacing.
     *
     * <p>PDFBox emits a single newline between every line, so the blank lines a reader
     * sees between paragraphs are lost. Without recovering them the whole essay
     * collapses into one paragraph, every {@code ¶} label is wrong, and every highlight
     * offset is measured against a single blob.
     *
     * <p>PDFBox's own {@code setParagraphEnd} is not usable here — it treats every line
     * as a paragraph and inserts a break after each one.
     *
     * <p>The threshold is self-calibrating: the smallest gap seen so far is taken as the
     * document's normal leading, and a gap meaningfully larger than that is a paragraph
     * break. This adapts to font size and leading instead of assuming them.
     */
    static final class ParagraphAwareStripper extends org.apache.pdfbox.text.PDFTextStripper {

        /** A gap this much larger than the normal leading indicates a paragraph break. */
        private static final float PARAGRAPH_GAP_RATIO = 1.5f;

        private Float previousY;
        private Float normalGap;

        ParagraphAwareStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text,
                                   java.util.List<org.apache.pdfbox.text.TextPosition> positions)
                throws IOException {

            if (!positions.isEmpty()) {
                float y = positions.get(0).getYDirAdj();
                if (previousY != null) {
                    float gap = y - previousY;
                    if (gap > 0) {
                        if (normalGap != null && gap > normalGap * PARAGRAPH_GAP_RATIO) {
                            // Blank line, which is what the paragraph splitter looks for.
                            super.writeString(System.lineSeparator(), java.util.List.of());
                        }
                        // Normal leading is the smallest gap in the document; ordinary
                        // lines vastly outnumber paragraph breaks, so the running
                        // minimum converges after the first couple of lines.
                        normalGap = normalGap == null ? gap : Math.min(normalGap, gap);
                    }
                }
                previousY = y;
            }

            super.writeString(text, positions);
        }
    }

    /** DOCX text extraction. */
    static final class DocxText {
        static String extract(byte[] bytes) throws IOException {
            try (InputStream in = new java.io.ByteArrayInputStream(bytes);
                 org.apache.poi.xwpf.usermodel.XWPFDocument document =
                         new org.apache.poi.xwpf.usermodel.XWPFDocument(in)) {
                StringBuilder text = new StringBuilder();
                document.getParagraphs().forEach(paragraph -> {
                    String value = paragraph.getText();
                    if (value != null && !value.isBlank()) {
                        text.append(value).append("\n\n");
                    }
                });
                return text.toString();
            }
        }
    }
}
