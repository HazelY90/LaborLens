package com.hazely.laborlens.jobs.policyExtraction;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Checks file and response structure; quoted evidence still requires source review. */
public final class PolicyCheck {
    private PolicyCheck() {}

    /** Checks transport limits and the PDF signature without parsing document contents. */
    public static void validatePdf(byte[] pdf) {
        if (pdf == null || pdf.length < 5 || pdf.length >= 50_000_000
                || !new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new IllegalArgumentException("Expected a PDF file smaller than 50 MB");
        }
    }

    public static List<PolicyDraft> validate(PdfSource source, List<PolicyDraft> policies) {
        if (policies == null) throw new IllegalArgumentException("Missing policy list");
        Map<String, PolicyDraft> unique = new LinkedHashMap<>();
        for (int i = 0; i < policies.size(); i++) {
            PolicyDraft policy = policies.get(i);
            String field = null;
            if (policy == null) field = "policy record";
            else if (!source.fileName().equals(policy.sourceFile())) field = "source_file";
            else if (policy.periodStart() != source.start()) field = "period_start";
            else if (policy.periodEnd() != source.end()) field = "period_end";
            else if (policy.type() == null) field = "type";
            else if (policy.policy() == null || policy.policy().isBlank()
                    || policy.policy().length() > 10_000) field = "policy";
            else if (policy.page() < 1) field = "page";
            else if (policy.quote() == null || normalize(policy.quote()).length() < 20) field = "quote";
            if (field != null) {
                throw new IllegalArgumentException(source.fileName() + ": policy " + (i + 1)
                        + " has invalid " + field);
            }
            // Keep evidence for review; no local text conversion or quote matching is performed.
            String key = policy.page() + "|" + policy.type() + "|" + normalize(policy.policy());
            unique.putIfAbsent(key, policy);
        }
        return List.copyOf(unique.values());
    }

    static String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
