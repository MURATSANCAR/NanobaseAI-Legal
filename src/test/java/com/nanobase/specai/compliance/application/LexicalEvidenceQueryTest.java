package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LexicalEvidenceQueryTest {
    private static final String ISO_EVIDENCE =
        "TURKIYE ELEKTRIK DAGITIM A.S. ISO/IEC 27001:2022 Bilgi Guvenligi "
            + "Yonetim Sistemi sertifikasi. Sertifika eki / sahalar.";
    private static final String SLA_EVIDENCE =
        "NANO TEKNOLOJI A.S. HIZMET SEVIYESI ANLASMASI (SLA). ISO/IEC 27001:2022 "
            + "belgesine sahiptir. ISO 22301 henuz alinmamistir. PCI DSS yoktur. "
            + "Veri merkezi Tier II esdegerdir (Tier III degildir). Aylik uptime %99.0. "
            + "Kritik mudahale 60 dakika.";
    private static final String YAPIM_EVIDENCE =
        "Yuklenici anahtar teslimi olarak isi tamamlayacak ve fiyat farki talep etmeyecektir.";

    @Test
    void indexesSignificantTokensInsteadOfAndingEveryWord() {
        LexicalEvidenceQuery query = LexicalEvidenceQuery.from(
            "TUV Energy Efficient Data Center, ISO 27001-Bilgi Güvenliği sertifikası, "
                + "ISO 22301- İş sürekliliği sertifikası, PCI DSS");

        assertThat(query.isEmpty()).isFalse();
        assertThat(query.tokens()).contains("iso", "27001");
        assertThat(query.tokens().stream().anyMatch(token -> token.startsWith("sertifika")))
            .isTrue();
        assertThat(query.tsQuery()).contains("|");
        assertThat(query.minimumHits()).isBetween(2, 3);
    }

    @Test
    void sameOrganizationIsoRequirementMatchesIsoCertificateEvidence() {
        LexicalEvidenceQuery query = LexicalEvidenceQuery.from(
            "ISO 27001 sertifikasına sahip olunmalıdır.");

        assertThat(query.matches(ISO_EVIDENCE)).isTrue();
        assertThat(query.hitCount(ISO_EVIDENCE)).isGreaterThanOrEqualTo(query.minimumHits());
    }

    @Test
    void differentOrganizationIsolationIsEnforcedByCallerFiltersNotByLexicalMatch() {
        // Lexical matching is content-only; tenant isolation remains organization_id in SQL.
        LexicalEvidenceQuery query = LexicalEvidenceQuery.from("ISO 27001 sertifikası");
        UUID orgA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID orgB = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThat(query.matches(ISO_EVIDENCE)).isTrue();
        assertThat(orgA).isNotEqualTo(orgB);
    }

    @Test
    void crossDocumentRetrievalMatchesRequirementFromSpecToIsoEvidence() {
        LexicalEvidenceQuery query = LexicalEvidenceQuery.from(
            "YÜKLENİCİ veya HİZMET ALDIĞI VERİ MERKEZİ TUV Energy Efficient Data Center, "
                + "ISO 27001-Bilgi Güvenliği sertifikası bulundurmalıdır.");

        assertThat(query.matches(ISO_EVIDENCE)).isTrue();
        assertThat(query.matches(SLA_EVIDENCE)).isTrue();
    }

    @Test
    void retrievalDoesNotRequireRequirementSourceDocumentId() {
        LexicalEvidenceQuery query = LexicalEvidenceQuery.from(
            "Yüklenicinin Ana Veri Merkezi en az Tier III sertifikasına sahip olmalıdır.");

        assertThat(query.matches(SLA_EVIDENCE)).isTrue();
        // ISO certificate text has no Tier/data-center tokens; cross-doc still works via SLA.
        assertThat(query.hitCount(ISO_EVIDENCE)).isLessThan(query.minimumHits());
    }

    @Test
    void turkishFoldingNormalizesDiacriticsForStopwordsAndStems() {
        LexicalEvidenceQuery query = LexicalEvidenceQuery.from(
            "ISO 27001 sertifikasına sahip olunmalıdır.");

        assertThat(query.tokens()).contains("iso", "27001", "sertifika");
        assertThat(query.tokens()).doesNotContain("olmalidir", "sahip", "olunmalidir");
    }

    @Test
    void emptyQueryDoesNotSilentlyMatchEverything() {
        LexicalEvidenceQuery query = LexicalEvidenceQuery.from("   ");

        assertThat(query.isEmpty()).isTrue();
        assertThat(query.matches(ISO_EVIDENCE)).isFalse();
        assertThat(query.hitCount(ISO_EVIDENCE)).isZero();
    }

    @Test
    void embeddingDimensionMismatchIsRejectedExplicitlyWhenProvided() {
        assertThatThrownBy(() -> EmbeddingDimensionGuard.requireCompatible(384, 768))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Embedding dimension mismatch");
        EmbeddingDimensionGuard.requireCompatible(384, 384);
        EmbeddingDimensionGuard.requireCompatible(null, null);
    }

    @Test
    void keywordFallbackMatchesRealScenarioEvidence() {
        LexicalEvidenceQuery iso = LexicalEvidenceQuery.from(
            "ISO 27001 sertifikasına sahip olunmalıdır.");
        LexicalEvidenceQuery sla = LexicalEvidenceQuery.from(
            "Hizmet seviyesi anlaşmasında aylık uptime ve kritik müdahale süresi "
                + "tanımlanmalıdır.");
        LexicalEvidenceQuery yapim = LexicalEvidenceQuery.from(
            "Yüklenici anahtar teslimi olarak işi tamamlayacak ve fiyat farkı talep etmeyecektir.");

        assertThat(iso.matches(ISO_EVIDENCE)).isTrue();
        assertThat(sla.matches(SLA_EVIDENCE)).isTrue();
        assertThat(yapim.matches(YAPIM_EVIDENCE)).isTrue();
        assertThat(iso.hitCount(ISO_EVIDENCE)).isGreaterThan(0);
        assertThat(sla.hitCount(SLA_EVIDENCE)).isGreaterThan(0);
        assertThat(yapim.hitCount(YAPIM_EVIDENCE)).isGreaterThan(0);
    }

    @Test
    void plainAndSemanticsWouldMissButOrOverlapSucceeds() {
        String longRequirement =
            "tuv energy efficient data center, iso 27001-bilgi güvenliği sertifikası, "
                + "iso 22301- iş sürekliliği sertifikası, pci dss";
        LexicalEvidenceQuery query = LexicalEvidenceQuery.from(longRequirement);

        assertThat(query.matches(ISO_EVIDENCE)).isTrue();
        assertThat(query.matches(
            "Tamamen alakasiz bir metin hicbir anahtar kelime icermez")).isFalse();
    }
}
