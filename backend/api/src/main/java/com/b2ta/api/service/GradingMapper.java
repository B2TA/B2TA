package com.b2ta.api.service;

import com.b2ta.common.dto.grading.CriterionAnalysisDto;
import com.b2ta.common.dto.grading.CriterionScoreDto;
import com.b2ta.common.dto.match.ConfirmedMatchDto;
import com.b2ta.common.dto.match.SuggestedMatchDto;
import com.b2ta.common.entity.ConfirmedMatch;
import com.b2ta.common.entity.CriterionAnalysis;
import com.b2ta.common.entity.CriterionScore;
import com.b2ta.common.entity.SuggestedMatch;
import org.springframework.stereotype.Component;

/** Entity to DTO conversions for the grading and match endpoints. */
@Component
public class GradingMapper {

    public CriterionScoreDto toDto(CriterionScore score) {
        return CriterionScoreDto.builder()
                .id(score.getId())
                .criterionId(score.getCriterion().getId())
                .selectedLevelId(score.getSelectedLevel() == null
                        ? null : score.getSelectedLevel().getId())
                .overridePoints(score.getOverridePoints())
                .criterionFeedback(score.getCriterionFeedback() == null
                        ? "" : score.getCriterionFeedback())
                .build();
    }

    public SuggestedMatchDto toDto(SuggestedMatch match) {
        return SuggestedMatchDto.builder()
                .id(match.getId())
                .submissionId(match.getSubmission().getId())
                .criterionId(match.getCriterion().getId())
                .passageStart(match.getPassageStart())
                .passageEnd(match.getPassageEnd())
                .rationale(match.getRationale())
                .confidence(match.getConfidence())
                .matchState(match.getMatchState())
                .isStale(match.getIsStale())
                .createdAt(match.getCreatedAt())
                .build();
    }

    public ConfirmedMatchDto toDto(ConfirmedMatch match) {
        return ConfirmedMatchDto.builder()
                .id(match.getId())
                .submissionId(match.getSubmission().getId())
                .criterionId(match.getCriterion().getId())
                .passageStart(match.getPassageStart())
                .passageEnd(match.getPassageEnd())
                .rationale(match.getRationale())
                .confidence(match.getConfidence())
                .origin(match.getOrigin())
                .sourceMatchId(match.getSourceMatchId())
                .createdAt(match.getCreatedAt())
                .build();
    }

    public CriterionAnalysisDto toDto(CriterionAnalysis analysis) {
        return CriterionAnalysisDto.builder()
                .criterionId(analysis.getCriterion().getId())
                .state(analysis.getState())
                .failureReason(analysis.getFailureReason())
                .analyzedCharCount(analysis.getAnalyzedCharCount())
                .build();
    }
}
