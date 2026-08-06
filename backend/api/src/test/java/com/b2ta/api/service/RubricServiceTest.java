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
import com.b2ta.common.entity.TaUser;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RubricServiceTest {

    @Mock
    private RubricRepository rubricRepository;

    @Mock
    private GradingSessionRepository sessionRepository;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private RubricService rubricService;

    private static final UUID TA_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RUBRIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private GradingSession session;

    @BeforeEach
    void setUp() {
        TaUser ta = new TaUser();
        ta.setId(TA_ID);

        session = GradingSession.builder()
                .id(SESSION_ID)
                .ta(ta)
                .name("Test Session")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // --- GET rubric tests ---

    @Test
    void getRubric_returnsRubricWithCriteria() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        Rubric rubric = buildRubric();
        when(rubricRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(rubric));

        RubricResponse response = rubricService.getRubric(SESSION_ID);

        assertThat(response.getId()).isEqualTo(RUBRIC_ID);
        assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(response.getCriteria()).hasSize(1);
        assertThat(response.getCriteria().get(0).getTitle()).isEqualTo("Thesis");
        assertThat(response.getCriteria().get(0).getPerformanceLevels()).hasSize(2);
    }

    @Test
    void getRubric_throws404_whenSessionNotFound() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rubricService.getRubric(SESSION_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Session not found");
    }

    @Test
    void getRubric_throws404_whenRubricNotFound() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));
        when(rubricRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rubricService.getRubric(SESSION_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Rubric not found for session");
    }

    // --- PUT (save) rubric tests ---

    @Test
    void saveRubric_createsNewRubric_whenNoneExists() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));
        when(rubricRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(rubricRepository.save(any(Rubric.class))).thenAnswer(inv -> {
            Rubric r = inv.getArgument(0);
            r.setId(RUBRIC_ID);
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });

        SaveRubricRequest request = buildValidRequest();
        RubricResponse response = rubricService.saveRubric(SESSION_ID, request);

        assertThat(response.getId()).isEqualTo(RUBRIC_ID);
        assertThat(response.getCriteria()).hasSize(1);
        assertThat(response.getCriteria().get(0).getDisplayColor()).isEqualTo(ColorPalette.getColor(0));
        assertThat(response.getCriteria().get(0).getPerformanceLevels()).hasSize(2);
        verify(rubricRepository).save(any(Rubric.class));
    }

    @Test
    void saveRubric_replacesExistingRubric() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        Rubric existing = buildRubric();
        when(rubricRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(existing));
        when(rubricRepository.save(any(Rubric.class))).thenAnswer(inv -> {
            Rubric r = inv.getArgument(0);
            r.setUpdatedAt(Instant.now());
            return r;
        });

        SaveRubricRequest request = buildValidRequest();
        RubricResponse response = rubricService.saveRubric(SESSION_ID, request);

        assertThat(response.getCriteria()).hasSize(1);
        assertThat(response.getCriteria().get(0).getTitle()).isEqualTo("Introduction");
    }

    @Test
    void saveRubric_assignsColorsSequentially() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));
        when(rubricRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(rubricRepository.save(any(Rubric.class))).thenAnswer(inv -> {
            Rubric r = inv.getArgument(0);
            r.setId(RUBRIC_ID);
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });

        List<CriterionDto> criteria = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            criteria.add(buildCriterionDto("Criterion " + i));
        }
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(criteria).build();

        RubricResponse response = rubricService.saveRubric(SESSION_ID, request);

        assertThat(response.getCriteria()).hasSize(3);
        assertThat(response.getCriteria().get(0).getDisplayColor()).isEqualTo(ColorPalette.getColor(0));
        assertThat(response.getCriteria().get(1).getDisplayColor()).isEqualTo(ColorPalette.getColor(1));
        assertThat(response.getCriteria().get(2).getDisplayColor()).isEqualTo(ColorPalette.getColor(2));
    }

    @Test
    void saveRubric_setsRequiresCompletion_whenMaxPointsNull() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));
        when(rubricRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(rubricRepository.save(any(Rubric.class))).thenAnswer(inv -> {
            Rubric r = inv.getArgument(0);
            r.setId(RUBRIC_ID);
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });

        CriterionDto dto = CriterionDto.builder()
                .title("Unresolved Criterion")
                .maxPoints(null) // unresolved
                .performanceLevels(List.of(
                        PerformanceLevelDto.builder().label("Pass").points(null).position(0).build()
                ))
                .build();
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        RubricResponse response = rubricService.saveRubric(SESSION_ID, request);

        assertThat(response.getCriteria().get(0).getRequiresCompletion()).isTrue();
    }

    // --- Validation tests ---

    @Test
    void saveRubric_rejectsEmptyCriteria() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of()).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("At least one criterion is required");
    }

    @Test
    void saveRubric_rejectsMoreThan30Criteria() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        List<CriterionDto> criteria = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            criteria.add(buildCriterionDto("C" + i));
        }
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(criteria).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Maximum 30 criteria allowed");
    }

    @Test
    void saveRubric_rejectsBlankTitle() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        CriterionDto dto = buildCriterionDto("  ");
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("title is required");
    }

    @Test
    void saveRubric_rejectsTitleOver200Chars() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        CriterionDto dto = buildCriterionDto("x".repeat(201));
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("title must be 200 characters or fewer");
    }

    @Test
    void saveRubric_rejectsDescriptionOver2000Chars() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        CriterionDto dto = CriterionDto.builder()
                .title("Valid Title")
                .description("x".repeat(2001))
                .maxPoints(new BigDecimal("10.00"))
                .performanceLevels(List.of(
                        PerformanceLevelDto.builder().label("Good").points(new BigDecimal("10.00")).position(0).build()
                ))
                .build();
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("description must be 2000 characters or fewer");
    }

    @Test
    void saveRubric_rejectsMaxPointsBelow001() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        CriterionDto dto = CriterionDto.builder()
                .title("Valid Title")
                .maxPoints(new BigDecimal("0.001"))
                .performanceLevels(List.of(
                        PerformanceLevelDto.builder().label("Good").points(BigDecimal.ZERO).position(0).build()
                ))
                .build();
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("max points must be at least 0.01");
    }

    @Test
    void saveRubric_rejectsMaxPointsAbove1000() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        CriterionDto dto = CriterionDto.builder()
                .title("Valid Title")
                .maxPoints(new BigDecimal("1000.01"))
                .performanceLevels(List.of(
                        PerformanceLevelDto.builder().label("Good").points(BigDecimal.ZERO).position(0).build()
                ))
                .build();
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("max points must be at most 1000");
    }

    @Test
    void saveRubric_rejectsMaxPointsWithMoreThan2DecimalPlaces() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        CriterionDto dto = CriterionDto.builder()
                .title("Valid Title")
                .maxPoints(new BigDecimal("10.123"))
                .performanceLevels(List.of(
                        PerformanceLevelDto.builder().label("Good").points(BigDecimal.ZERO).position(0).build()
                ))
                .build();
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("max points must have at most 2 decimal places");
    }

    @Test
    void saveRubric_rejectsNoPerformanceLevels() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        CriterionDto dto = CriterionDto.builder()
                .title("Valid Title")
                .maxPoints(new BigDecimal("10.00"))
                .performanceLevels(List.of())
                .build();
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must have at least 1 performance level");
    }

    @Test
    void saveRubric_rejectsMoreThan10PerformanceLevels() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        List<PerformanceLevelDto> levels = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            levels.add(PerformanceLevelDto.builder().label("Level " + i).points(BigDecimal.valueOf(i)).position(i).build());
        }
        CriterionDto dto = CriterionDto.builder()
                .title("Valid Title")
                .maxPoints(new BigDecimal("100.00"))
                .performanceLevels(levels)
                .build();
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must have at most 10 performance levels");
    }

    @Test
    void saveRubric_rejectsLevelLabelOver100Chars() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        CriterionDto dto = CriterionDto.builder()
                .title("Valid Title")
                .maxPoints(new BigDecimal("10.00"))
                .performanceLevels(List.of(
                        PerformanceLevelDto.builder().label("x".repeat(101)).points(BigDecimal.ZERO).position(0).build()
                ))
                .build();
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("label must be 100 characters or fewer");
    }

    @Test
    void saveRubric_rejectsLevelPointsExceedingCriterionMax() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        CriterionDto dto = CriterionDto.builder()
                .title("Valid Title")
                .maxPoints(new BigDecimal("10.00"))
                .performanceLevels(List.of(
                        PerformanceLevelDto.builder().label("Exceeds").points(new BigDecimal("10.01")).position(0).build()
                ))
                .build();
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("points must not exceed criterion max points");
    }

    @Test
    void saveRubric_rejectsNegativeLevelPoints() {
        when(securityContextHelper.getCurrentTaId()).thenReturn(TA_ID);
        when(sessionRepository.findByIdAndTaId(SESSION_ID, TA_ID)).thenReturn(Optional.of(session));

        CriterionDto dto = CriterionDto.builder()
                .title("Valid Title")
                .maxPoints(new BigDecimal("10.00"))
                .performanceLevels(List.of(
                        PerformanceLevelDto.builder().label("Bad").points(new BigDecimal("-1")).position(0).build()
                ))
                .build();
        SaveRubricRequest request = SaveRubricRequest.builder().criteria(List.of(dto)).build();

        assertThatThrownBy(() -> rubricService.saveRubric(SESSION_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("points must be non-negative");
    }

    // --- Helper methods ---

    private SaveRubricRequest buildValidRequest() {
        return SaveRubricRequest.builder()
                .criteria(List.of(buildCriterionDto("Introduction")))
                .build();
    }

    private CriterionDto buildCriterionDto(String title) {
        return CriterionDto.builder()
                .title(title)
                .description("A test criterion")
                .maxPoints(new BigDecimal("10.00"))
                .performanceLevels(List.of(
                        PerformanceLevelDto.builder()
                                .label("Excellent")
                                .points(new BigDecimal("10.00"))
                                .position(0)
                                .build(),
                        PerformanceLevelDto.builder()
                                .label("Poor")
                                .points(new BigDecimal("2.00"))
                                .position(1)
                                .build()
                ))
                .build();
    }

    private Rubric buildRubric() {
        PerformanceLevel level1 = PerformanceLevel.builder()
                .id(UUID.randomUUID())
                .label("Excellent")
                .description("Outstanding work")
                .points(new BigDecimal("10.00"))
                .position(0)
                .build();

        PerformanceLevel level2 = PerformanceLevel.builder()
                .id(UUID.randomUUID())
                .label("Poor")
                .description("Needs improvement")
                .points(new BigDecimal("2.00"))
                .position(1)
                .build();

        Criterion criterion = Criterion.builder()
                .id(UUID.randomUUID())
                .title("Thesis")
                .description("Clear thesis statement")
                .maxPoints(new BigDecimal("10.00"))
                .displayColor("#D32F2F")
                .position(0)
                .requiresCompletion(false)
                .performanceLevels(new ArrayList<>(List.of(level1, level2)))
                .createdAt(Instant.now())
                .build();

        level1.setCriterion(criterion);
        level2.setCriterion(criterion);

        Rubric rubric = Rubric.builder()
                .id(RUBRIC_ID)
                .session(session)
                .sourceFormat("manual")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .criteria(new ArrayList<>(List.of(criterion)))
                .build();

        criterion.setRubric(rubric);

        return rubric;
    }
}
