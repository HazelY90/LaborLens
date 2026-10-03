package com.hazely.laborlens.jobs.metricsExtraction;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Reads quoted CSV records, including escaped quotes and embedded newlines. */
final class CsvReader implements AutoCloseable {
    private final PushbackReader reader;
    private boolean isFirst = true;

    CsvReader(Reader reader) {
        this.reader = new PushbackReader(reader, 1);
    }

    List<String> next() throws IOException {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean isQuoted = false;
        boolean isClosed = false;
        boolean isStarted = false;
        int ch;
        while ((ch = reader.read()) != -1) {
            if (isFirst) {
                isFirst = false;
                if (ch == '\uFEFF') continue;
            }
            isStarted = true;
            if (isQuoted) {
                if (ch == '"') {
                    int next = reader.read();
                    // Two quotes inside a quoted field represent one literal quote.
                    if (next == '"') cell.append('"');
                    else {
                        isQuoted = false;
                        isClosed = true;
                        if (next != -1) reader.unread(next);
                    }
                } else cell.append((char) ch);
            } else if (ch == ',') {
                cells.add(cell.toString().trim());
                cell.setLength(0);
                isClosed = false;
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) reader.unread(next);
                }
                cells.add(cell.toString().trim());
                return cells;
            } else if (ch == '"') {
                if (isClosed || !cell.toString().isBlank()) {
                    throw new IllegalArgumentException("Unexpected CSV quote");
                }
                cell.setLength(0);
                isQuoted = true;
            } else {
                if (isClosed && !Character.isWhitespace(ch)) {
                    throw new IllegalArgumentException("Unexpected text after CSV quote");
                }
                cell.append((char) ch);
            }
        }
        // EOF may finish a record, but it cannot finish an open quoted field.
        if (isQuoted) throw new IllegalArgumentException("Unclosed CSV quote");
        if (!isStarted) return null;
        cells.add(cell.toString().trim());
        return cells;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
