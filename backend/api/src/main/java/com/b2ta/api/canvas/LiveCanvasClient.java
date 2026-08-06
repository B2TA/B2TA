package com.b2ta.api.canvas;

import com.b2ta.api.canvas.dto.CanvasAssignment;
import com.b2ta.api.canvas.dto.CanvasRubricAssessmentEntry;
import com.b2ta.api.canvas.dto.CanvasSubmission;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link CanvasClient} backed by a real Canvas instance.
 *
 * <p>Every call carries {@code Authorization: Bearer <token>}, resolved from Secrets
 * Manager. This class is the only place the token is used; nothing here returns it to a
 * caller (Requirement 6.2).
 */
@Slf4j
public class LiveCanvasClient implements CanvasClient {

    /**
     * Guards against a malformed Link chain looping forever. At per_page=100 this allows
     * 50,000 submissions, far beyond any real course.
     */
    private static final int MAX_PAGES = 500;

    private final RestClient restClient;
    private final CanvasTokenProvider tokenProvider;
    private final CanvasProperties properties;
    private final ObjectMapper objectMapper;

    public LiveCanvasClient(RestClient restClient,
                            CanvasTokenProvider tokenProvider,
                            CanvasProperties properties,
                            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public CanvasAssignment getAssignment(String courseId, String assignmentId) {
        String url = apiBase() + "/courses/" + courseId + "/assignments/" + assignmentId;
        Page page = fetch(url, "assignment " + assignmentId);
        return read(page.body(), CanvasAssignment.class);
    }

    @Override
    public List<CanvasSubmission> listSubmissions(String courseId, String assignmentId) {
        String url = apiBase() + "/courses/" + courseId + "/assignments/" + assignmentId
                + "/submissions?include[]=user&include[]=rubric_assessment"
                + "&include[]=submission_comments&per_page=" + properties.getPerPage();

        List<CanvasSubmission> all = new ArrayList<>();
        String nextUrl = url;
        int pages = 0;

        while (nextUrl != null) {
            if (++pages > MAX_PAGES) {
                throw new CanvasException(
                        "Canvas pagination exceeded " + MAX_PAGES + " pages; aborting to avoid a loop.",
                        0, false);
            }
            Page page = fetch(nextUrl, "submissions for assignment " + assignmentId);
            all.addAll(readList(page.body()));
            nextUrl = LinkHeaderParser.next(page.linkHeader()).orElse(null);
        }

        log.info("Fetched {} submissions for assignment {} across {} page(s)",
                all.size(), assignmentId, pages);
        return all;
    }

    @Override
    public CanvasSubmission getSubmission(String courseId, String assignmentId, String userId) {
        String url = apiBase() + "/courses/" + courseId + "/assignments/" + assignmentId
                + "/submissions/" + userId + "?include[]=user&include[]=rubric_assessment";
        Page page = fetch(url, "submission for user " + userId);
        return read(page.body(), CanvasSubmission.class);
    }

    @Override
    public CanvasSubmission submitAssessment(String courseId,
                                             String assignmentId,
                                             String userId,
                                             Map<String, CanvasRubricAssessmentEntry> assessment,
                                             String comment) {
        String url = apiBase() + "/courses/" + courseId + "/assignments/" + assignmentId
                + "/submissions/" + userId;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rubric_assessment", assessment);
        if (comment != null && !comment.isBlank()) {
            payload.put("comment", Map.of("text_comment", comment));
        }

        // Criterion count only — never the scores or the comment text, which are
        // student-identifying content (Requirement 6.3).
        log.info("Writing rubric assessment for user {} on assignment {} ({} criteria)",
                userId, assignmentId, assessment.size());

        try {
            String body = restClient.put()
                    .uri(URI.create(url))
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> status.value() == 401,
                            (req, res) -> { throw CanvasException.unauthorized(""); })
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> { throw CanvasException.notFound("submission " + userId); })
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw new CanvasException(
                                "Canvas rejected the grade write (" + res.getStatusCode().value() + "): "
                                        + readBody(res.getBody().readAllBytes()),
                                res.getStatusCode().value(),
                                res.getStatusCode().is5xxServerError());
                    })
                    .body(String.class);

            return read(body, CanvasSubmission.class);

        } catch (CanvasException e) {
            throw e;
        } catch (Exception e) {
            throw new CanvasException("Could not reach Canvas to write the grade.", 0, true, e);
        }
    }

    /**
     * Issues one GET and returns the body plus the Link header, which is the only place
     * Canvas reports whether another page exists.
     */
    private Page fetch(String url, String what) {
        try {
            return restClient.get()
                    .uri(URI.create(url))
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        String body = readBody(response.getBody().readAllBytes());

                        if (status == 401) {
                            throw CanvasException.unauthorized(body);
                        }
                        if (status == 404) {
                            throw CanvasException.notFound(what);
                        }
                        if (response.getStatusCode().isError()) {
                            throw new CanvasException(
                                    "Canvas returned " + status + " for " + what + ": " + body,
                                    status,
                                    response.getStatusCode().is5xxServerError());
                        }
                        return new Page(body, response.getHeaders().getFirst(HttpHeaders.LINK));
                    });
        } catch (CanvasException e) {
            throw e;
        } catch (Exception e) {
            throw new CanvasException("Could not reach Canvas for " + what + ".", 0, true, e);
        }
    }

    private String bearer() {
        return "Bearer " + tokenProvider.get().token();
    }

    private String apiBase() {
        return tokenProvider.get().baseUrl() + "/api/v1";
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new CanvasException("Could not parse the Canvas response.", 0, false, e);
        }
    }

    private List<CanvasSubmission> readList(String body) {
        try {
            return objectMapper.readValue(body, new TypeReference<List<CanvasSubmission>>() {
            });
        } catch (Exception e) {
            throw new CanvasException("Could not parse the Canvas submissions response.", 0, false, e);
        }
    }

    private static String readBody(byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    /** One HTTP response: JSON body plus the raw Link header (null on the last page). */
    private record Page(String body, String linkHeader) {
        Page {
            Optional.ofNullable(body).orElseThrow(
                    () -> new CanvasException("Canvas returned an empty body.", 0, true));
        }
    }
}
