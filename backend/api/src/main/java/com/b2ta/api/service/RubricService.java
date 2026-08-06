package com.b2ta.api.service;

import com.b2ta.api.repository.GradingSessionRepository;
import com.b2ta.api.repository.RubricRepository;
import com.b2ta.api.security.SecurityContextHelper;
import com.b2ta.api.util.ColorPalette;
import com.b2ta.common.dto.rubric.CriterionDto;
import com.b2ta.common.dto.rubric.PerformanceLevelDto;
import com.b2ta.common.dto.rubric.RubricResponse;
import com.b2ta.common.dto.rubric.SaveRubricRequest;
import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.PerformanceLevel;
import com.b2ta.common.entity.Rubric;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RubricService {

    private final RubricRepository rubricRepository;
    private final GradingSessionRepository sessionRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Loads the rubric for a session with all criteria and performance levels.
     * Returns 404 if no rubric exists for the session.
     */
    @Transactional(readOnly = true)
    public RubricResponse getRubric(UUID sessionId) {
        GradingSession session = getSessionForCurrentTa(sessionId);

        Rubric rubric = rubricRepository.findBySessionId(session.getId())
                .orElseThrow(() -> new EntityNotFoundException("Rubric not found for session"));

        return toResponse(rubric);
    }

    /**
     * Saves (creates or fully replaces) the rubric for a session.
     * Performs validation beyond what Jakarta annotations cover:
     * - description length (0-2000 chars)
     * - max_points at most 2 decimal places
     * - performance level points <= criterion max_points
     * - performance level label length (1-100 chars)
     * Assigns display colors from the fixed palette.
     */
    @Transactional
    public RubricResponse saveRubric(UUID sessionId, SaveRubricRequest request) {
        GradingSession session = getSessionForCurrentTa(sessionId);

        validateCriteria(request.getCriteria());

        // Find or create rubric
        Rubric rubric = rubricRepository.findBySessionId(session.getId())
                .orElseGet(() -> Rubric.builder()
                        .session(session)
                        .sourceFormat("manual")
                        .criteria(new ArrayList<>())
                        .build());

        // Clear existing criteria (full replacement)
        rubric.getCriteria().clear();

        // Build new criteria from the request
        List<CriterionDto> criteriaDto = request.getCriteria();
        for (int i = 0; i < criteriaDto.size(); i++) {
            CriterionDto dto = criteriaDto.get(i);

            Criterion criterion = Criterion.builder()
                    .rubric(rubric)
                    .title(dto.getTitle())
                    .description(dto.getDescription() != null ? dto.getDescription() : "")
                    .maxPoints(dto.getMaxPoints())
                    .displayColor(ColorPalette.getColor(i))
                    .position(i)
                    .requiresCompletion(dto.getMaxPoints() == null)
                    .performanceLevels(new ArrayList<>())
                    .build();

            if (dto.getPerformanceLevels() != null) {
                for (int j = 0; j < dto.getPerformanceLevels().size(); j++) {
                    PerformanceLevelDto levelDto = dto.getPerformanceLevels().get(j);

                    PerformanceLevel level = PerformanceLevel.builder()
                            .criterion(criterion)
                            .label(levelDto.getLabel())
                            .description(levelDto.getDescription() != null ? levelDto.getDescription() : "")
                            .points(levelDto.getPoints())
                            .position(j)
                            .build();

                    criterion.getPerformanceLevels().add(level);
                }
            }

            rubric.getCriteria().add(criterion);
        }

        Rubric saved = rubricRepository.save(rubric);
        return toResponse(saved);
    }

    /**
     * Additional validation beyond Jakarta Bean Validation annotations.
     */
    private void validateCriteria(List<CriterionDto> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one criterion is required");
        }
        if (criteria.size() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 30 criteria allowed");
        }

        for (int i = 0; i < criteria.size(); i++) {
            CriterionDto dto = criteria.get(i);
            String prefix = "Criterion " + (i + 1) + ": ";

            // Title validation (1-200 chars)
            if (dto.getTitle() == null || dto.getTitle().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "title is required");
            }
            if (dto.getTitle().length() > 200) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "title must be 200 characters or fewer");
            }

            // Description validation (0-2000 chars)
            if (dto.getDescription() != null && dto.getDescription().length() > 2000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "description must be 2000 characters or fewer");
            }

            // Max points validation (0.01-1000, at most 2 decimal places, or null for unresolved)
            if (dto.getMaxPoints() != null) {
                BigDecimal maxPoints = dto.getMaxPoints();
                if (maxPoints.compareTo(new BigDecimal("0.01")) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "max points must be at least 0.01");
                }
                if (maxPoints.compareTo(new BigDecimal("1000")) > 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "max points must be at most 1000");
                }
                if (maxPoints.stripTrailingZeros().scale() > 2) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "max points must have at most 2 decimal places");
                }
            }

            // Performance levels validation (1-10 levels)
            if (dto.getPerformanceLevels() == null || dto.getPerformanceLevels().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "must have at least 1 performance level");
            }
            if (dto.getPerformanceLevels().size() > 10) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "must have at most 10 performance levels");
            }

            for (int j = 0; j < dto.getPerformanceLevels().size(); j++) {
                PerformanceLevelDto levelDto = dto.getPerformanceLevels().get(j);
                String levelPrefix = prefix + "Level " + (j + 1) + ": ";

                // Label validation (1-100 chars)
                if (levelDto.getLabel() == null || levelDto.getLabel().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, levelPrefix + "label is required");
                }
                if (levelDto.getLabel().length() > 100) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, levelPrefix + "label must be 100 characters or fewer");
                }

                // Points validation (0 to criterion max, if criterion max is set)
                if (levelDto.getPoints() != null && dto.getMaxPoints() != null) {
                    if (levelDto.getPoints().compareTo(BigDecimal.ZERO) < 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, levelPrefix + "points must be non-negative");
                    }
                    if (levelDto.getPoints().compareTo(dto.getMaxPoints()) > 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                levelPrefix + "points must not exceed criterion max points (" + dto.getMaxPoints() + ")");
                    }
                }
            }
        }
    }

    private GradingSession getSessionForCurrentTa(UUID sessionId) {
        UUID taId = securityContextHelper.getCurrentTaId();
        return sessionRepository.findByIdAndTaId(sessionId, taId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found"));
    }

    private RubricResponse toResponse(Rubric rubric) {
        List<CriterionDto> criteriaDtos = rubric.getCriteria().stream()
                .map(this::toCriterionDto)
                .toList();

        return RubricResponse.builder()
                .id(rubric.getId())
                .sessionId(rubric.getSession().getId())
                .sourceFormat(rubric.getSourceFormat())
                .createdAt(rubric.getCreatedAt())
                .updatedAt(rubric.getUpdatedAt())
                .criteria(criteriaDtos)
                .build();
    }

    private CriterionDto toCriterionDto(Criterion criterion) {
        List<PerformanceLevelDto> levelDtos = criterion.getPerformanceLevels().stream()
                .map(this::toPerformanceLevelDto)
                .toList();

        return CriterionDto.builder()
                .id(criterion.getId())
                .title(criterion.getTitle())
                .description(criterion.getDescription())
                .maxPoints(criterion.getMaxPoints())
                .displayColor(criterion.getDisplayColor())
                .position(criterion.getPosition())
                .requiresCompletion(criterion.getRequiresCompletion())
                .performanceLevels(levelDtos)
                .build();
    }

    private PerformanceLevelDto toPerformanceLevelDto(PerformanceLevel level) {
        return PerformanceLevelDto.builder()
                .id(level.getId())
                .label(level.getLabel())
                .description(level.getDescription())
                .points(level.getPoints())
                .position(level.getPosition())
                .build();
    }
}
