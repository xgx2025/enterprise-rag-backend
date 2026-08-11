package com.hope.enterpriserag.chat.generation;

import com.hope.enterpriserag.knowledge.dto.RetrievalSourceResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationValidatorTest {
    private final CitationValidator validator = new CitationValidator();
    private final List<RetrievalSourceResponse> sources = List.of(
            new RetrievalSourceResponse("S1", "101", "差旅制度", "V2", LocalDate.of(2026, 1, 1),
                    "住宿标准", 3, "深圳住宿上限为 500 元。", 1, 0.92),
            new RetrievalSourceResponse("S2", "102", "报销制度", "V1", null,
                    "凭证要求", 5, "报销需要发票。", 1, 0.81));

    @Test
    void shouldAcceptOnlyCitationsFromCurrentEvidence() {
        CitationValidationResult result = validator.validate("深圳住宿上限为 500 元。[S1]", sources);

        assertThat(result.valid()).isTrue();
        assertThat(result.citedSources()).extracting(RetrievalSourceResponse::sourceId)
                .containsExactly("S1");
    }

    @Test
    void shouldRejectAnswerWithoutCitation() {
        CitationValidationResult result = validator.validate("深圳住宿上限为 500 元。", sources);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("CITATION_MISSING");
    }

    @Test
    void shouldRejectCitationNotPresentInCurrentEvidence() {
        CitationValidationResult result = validator.validate("深圳住宿上限为 500 元。[S9]", sources);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("CITATION_OUT_OF_SCOPE");
    }

    @Test
    void shouldRejectFabricatedClaimEvenWhenCitationIdExists() {
        CitationValidationResult result = validator.validate("深圳住宿上限为 1000000 元。[S1]", sources);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("CLAIM_NOT_SUPPORTED");
    }

    @Test
    void shouldRejectUncitedSecondClaim() {
        CitationValidationResult result = validator.validate(
                "深圳住宿上限为 500 元。[S1]\n所有员工还可领取额外补贴。", sources);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("CLAIM_CITATION_MISSING");
    }
}
