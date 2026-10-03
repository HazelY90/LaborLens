package com.hazely.laborlens.jobs.metricsExtraction;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CsvParserTests {
    private final CsvParser parser = new CsvParser();
    private static final String HEADER = "STATISTIC,TLIST(A1),Age Group,Sex,Education Attainment Level,NUTS 2 Region,UNIT,VALUE\n";

    @Test
    void parsesLocalReferenceFiles() throws Exception {
        // These counts describe the checked-in fixtures, not production acceptance thresholds.
        int[] expected = {
            2838, 756, 3423, 359
        };
        int[] missing = {
            522, 0, 273, 201
        };
        for (CsvSource source : CsvSource.values()) {
            var result = parser.parse(source, Files.readAllBytes(Path.of("../data", source.path())));
            assertEquals(expected[source.ordinal()], result.rows().size(), source.name());
            assertEquals(missing[source.ordinal()], result.missing(), source.name());
        }
    }

    @Test
    void handlesQuotedCellsAndBom() throws Exception {
        try (var csv = new CsvReader(new StringReader("\uFEFFa,b\r\n\"one,\"\"two\"\"\",\"line\nnext\"\r\n"))) {
            assertEquals(List.of("a", "b"), csv.next());
            assertEquals(List.of("one,\"two\"", "line\nnext"), csv.next());
            assertNull(csv.next());
        }
        for (String invalid : List.of("\"unfinished", "\"done\"bad", "unquoted\"quote")) {
            try (var csv = new CsvReader(new StringReader(invalid))) {
                assertThrows(IllegalArgumentException.class, csv::next);
            }
        }
    }

    @Test
    void preservesZeroAndSkipsOnlyMissingValues() throws Exception {
        String csv = sample().replace(row(2020, "10"), row(2020, ""))
                .replace(row(2019, "10"), row(2019, "0"));
        var result = parse("\uFEFF" + csv);
        assertEquals(6, result.rows().size());
        assertEquals(1, result.missing());
        assertEquals(0.0, result.rows().get(0).get(5));
    }

    @Test
    void rejectsInvalidMeasuresDimensionsAndPeriods() {
        for (String value : List.of("NaN", "Infinity", "-1", "101", "unknown", "0x1.0p0", "1e999")) {
            assertThrows(IllegalArgumentException.class,
                    () -> parse(sample().replace(row(2019, "10"), row(2019, value))),
                    value);
        }
        for (String csv : List.of(sample().replace("Ireland", "Unknown"),
                sample().replace("All ages", "15 - 74 years"), sample().replace(",%,",
                ",Thousand,"),
                sample().replace("2019,", "2019Q1,"), sample().replace("STATISTIC,",
                "UNKNOWN,"),
                sample().replace("ALF01C01", "ALF01C99"))) {
            assertThrows(IllegalArgumentException.class, () -> parse(csv));
        }
    }

    @Test
    void rejectsDuplicatesAfterMappingIncludingMissingMeasures() {
        assertThrows(IllegalArgumentException.class, () -> parse(sample() + row(2019, "")));
        assertThrows(IllegalArgumentException.class, () -> parse(sample()
                + row(2019, "10").replace("Levels  0-8", "Levels 0-8")));
    }

    @Test
    void rejectsIncompleteOrEmptySnapshots() {
        assertThrows(IllegalArgumentException.class, () -> parse(sample().replace(row(2025, "10"),
                "")));
        assertThrows(IllegalArgumentException.class, () -> parse(sample().replace(",%,10", ",%,")));
    }

    @Test
    void excludesEducationEvenWhenItHasAValue() throws Exception {
        var result = parse(sample() + row(2019, "10")
                .replace("Levels of Education (Levels  0-8)", "Less than primary (Level 0)"));
        assertEquals(7, result.rows().size());
        assertEquals(1, result.excluded());
    }

    private CsvParser.Result parse(String value) throws Exception {
        return parser.parse(CsvSource.ALF01, value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sample() {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int year = 2019; year <= 2025; year++) csv.append(row(year, "10"));
        return csv.toString();
    }

    private static String row(int year, String value) {
        return "ALF01C01," + year + ",All ages,Both sexes,Levels of Education (Levels  0-8),Ireland,%,"
                + value + "\n";
    }
}
