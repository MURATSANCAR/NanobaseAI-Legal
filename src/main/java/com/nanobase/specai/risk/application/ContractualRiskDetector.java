package com.nanobase.specai.risk.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Detects common contractual risk patterns from draft-contract clause text.
 * Scoring is deterministic; narrative text can later be enriched by LLM.
 */
@Service
public class ContractualRiskDetector {

    public record DetectedRisk(
        String riskType,
        String title,
        String description,
        String severity,
        String negotiationPoint,
        boolean requiresManagementApproval
    ) {
    }

    private record Pattern(String riskType, String title, String severity, String[] cues,
                           String negotiationPoint) {
    }

    private static final List<Pattern> PATTERNS = List.of(
        new Pattern("UNLIMITED_LIABILITY", "Sınırsız sorumluluk", "CRITICAL",
            new String[]{"sınırsız sorumluluk", "unlimited liability", "her türlü zarar"},
            "Sorumluluğu sözleşme bedeli veya makul bir tavan ile sınırlayın"),
        new Pattern("HIGH_PENALTY", "Yüksek cezai şart", "HIGH",
            new String[]{"cezai şart", "penalty", "gecikme cezası", "%"},
            "Ceza oranını ve tavanını müzakere edin"),
        new Pattern("UNILATERAL_TERMINATION", "Tek taraflı fesih", "HIGH",
            new String[]{"tek taraflı fesih", "unilateral termination", "önel olmaksızın fesih"},
            "Karşılıklı fesih ve makul bildirim süresi talep edin"),
        new Pattern("BROAD_INDEMNITY", "Geniş kapsamlı tazminat", "HIGH",
            new String[]{"tazmin eder", "indemnify", "zararları karşılar"},
            "Tazminat kapsamını doğrudan kusur ile sınırlayın"),
        new Pattern("AMBIGUOUS_ACCEPTANCE", "Belirsiz kabul kriterleri", "MEDIUM",
            new String[]{"uygun görüldüğünde", "kabul kriterleri", "acceptance criteria"},
            "Objektif kabul kriterleri tanımlayın"),
        new Pattern("LONG_PAYMENT_TERM", "Uzun ödeme vadesi", "MEDIUM",
            new String[]{"120 gün", "180 gün", "ödeme vadesi"},
            "Ödeme vadesini kısaltın veya ara ödemeler ekleyin"),
        new Pattern("PAYMENT_ON_APPROVAL", "Ödeme müşteri onayına bağlı", "HIGH",
            new String[]{"onayına bağlı", "subject to approval", "kabul edilmeden ödeme"},
            "Objektif teslimat koşullarına bağlayın"),
        new Pattern("SLA_PENALTY", "SLA cezası", "HIGH",
            new String[]{"sla", "hizmet seviyesi", "uptime", "erişilebilirlik cezası"},
            "SLA cezasına tavan ve service credit modeli ekleyin"),
        new Pattern("RETENTION_BOND", "Teminatın uzun süre tutulması", "MEDIUM",
            new String[]{"teminat", "performance bond", "garanti mektubu"},
            "Teminat çözümü için net tarihler tanımlayın"),
        new Pattern("IP_ASSIGNMENT", "Fikri mülkiyet devri", "CRITICAL",
            new String[]{"fikri mülkiyet", "intellectual property", "tüm haklar devredilir"},
            "Önceki IP ve arka plan teknolojisini hariç tutun"),
        new Pattern("EXCLUSIVITY", "Münhasırlık", "HIGH",
            new String[]{"münhasır", "exclusive", "tek tedarikçi"},
            "Münhasırlığı süre ve kapsam ile sınırlayın"),
        new Pattern("NON_COMPETE", "Rekabet yasağı", "HIGH",
            new String[]{"rekabet yasağı", "non-compete", "rakip"},
            "Coğrafya, süre ve sektör sınırları ekleyin"),
        new Pattern("SUBCONTRACTOR_RESTRICTION", "Alt yüklenici kısıtı", "MEDIUM",
            new String[]{"alt yüklenici", "subcontractor", "onaysız devir"},
            "Makul alt yüklenici onay sürecini netleştirin"),
        new Pattern("FX_RISK", "Kur değişimi riski", "MEDIUM",
            new String[]{"döviz", "foreign exchange", "kur farkı"},
            "Kur endeksleme veya yeniden fiyat mekanizması ekleyin"),
        new Pattern("PRICE_FIXING", "Fiyat sabitleme", "MEDIUM",
            new String[]{"sabit fiyat", "fixed price", "fiyat değişmez"},
            "Enflasyon/endeks farkı maddesi ekleyin"),
        new Pattern("NO_INFLATION", "Enflasyon farkı bulunmaması", "MEDIUM",
            new String[]{"enflasyon farkı ödenmez", "no inflation", "fiyat artışı yok"},
            "Çok yıllı sözleşmelerde endeksleme talep edin"),
        new Pattern("DATA_PROTECTION", "Veri koruma yükümlülükleri", "HIGH",
            new String[]{"kişisel veri", "kvkk", "gdpr", "data protection"},
            "Sorumluluk paylaşımını ve DPA kapsamını netleştirin"),
        new Pattern("CROSS_BORDER_TRANSFER", "Yurt dışı veri aktarımı", "HIGH",
            new String[]{"yurt dışı", "cross-border", "abroad transfer"},
            "Aktarım mekanizmalarını ve lokasyon kısıtlarını tanımlayın"),
        new Pattern("AUDIT_RIGHTS", "Denetim hakkı", "MEDIUM",
            new String[]{"denetim hakkı", "audit right", "yerinde denetim"},
            "Bildirim süresi ve kapsam sınırları ekleyin"),
        new Pattern("SOURCE_CODE_ESCROW", "Kaynak kod teslimi", "CRITICAL",
            new String[]{"kaynak kod", "source code", "escrow"},
            "Escrow koşullarını ve tetikleyicileri sınırlayın"),
        new Pattern("PERSONNEL_LOCK", "Personel değiştirme kısıtı", "MEDIUM",
            new String[]{"personel değişikliği", "key personnel", "onaysız değişiklik"},
            "Makul değişiklik hakkı ve yedek personel modeli ekleyin")
    );

    public List<DetectedRisk> detect(String clauseText) {
        if (clauseText == null || clauseText.isBlank()) {
            return List.of();
        }
        String haystack = clauseText.toLowerCase(Locale.ROOT);
        List<DetectedRisk> risks = new ArrayList<>();
        for (Pattern pattern : PATTERNS) {
            for (String cue : pattern.cues()) {
                if (haystack.contains(cue.toLowerCase(Locale.ROOT))) {
                    risks.add(new DetectedRisk(
                        pattern.riskType(),
                        pattern.title(),
                        "Clause matches contractual risk cue: " + cue,
                        pattern.severity(),
                        pattern.negotiationPoint(),
                        "CRITICAL".equals(pattern.severity()) || "HIGH".equals(pattern.severity())));
                    break;
                }
            }
        }
        return risks;
    }
}
