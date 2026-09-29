package com.hazely.laborlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(BackendApplication.class, args);
        // A manual ingestion launch is a one-shot process; normal server launches stay running.
        if (context.getEnvironment().getProperty("app.jobs.enabled", Boolean.class, false)) {
            context.close();
        }
    }

}
