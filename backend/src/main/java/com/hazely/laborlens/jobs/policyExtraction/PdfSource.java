package com.hazely.laborlens.jobs.policyExtraction;

import com.hazely.laborlens.jobs.SourceSpec;

/** Report editions remain distinct even when their strategy periods overlap. */
public enum PdfSource implements SourceSpec {
    STRATEGY_2018(2018, 2021), STRATEGY_2021(2021, 2023), STRATEGY_2023(2023, 2025),
    STRATEGY_2024(2024, 2025), STRATEGY_2025(2025, 2028);

    private final int start;
    private final int end;

    PdfSource(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public String fileName() {
        return "statement-of-strategy-" + start + "-" + end + ".pdf";
    }

    public String table() {
        return "policy";
    }

    public String path() {
        return "policies/" + fileName();
    }

    public String url() {
        return "https://enterprise.gov.ie/en/publications/publication-files/" + fileName();
    }
}
