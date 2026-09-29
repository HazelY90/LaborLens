package com.hazely.laborlens.jobs;

/** Approved file identity shared by preparation and processing stages. */
public interface SourceSpec {
    String fileName();
    String table();
    String path();
    String url();
}
