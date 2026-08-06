package com.b2ta.api.canvas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An uploaded file attached to a submission.
 *
 * <p>The {@code url} carries a {@code verifier} query parameter and is pre-authorized:
 * downloads must be issued <em>without</em> the bearer token. Sending the token is
 * unnecessary and leaks it to whatever host Canvas redirects to.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CanvasAttachment {

    private Long id;

    private String filename;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("content-type")
    private String contentType;

    private String url;

    private Long size;
}
