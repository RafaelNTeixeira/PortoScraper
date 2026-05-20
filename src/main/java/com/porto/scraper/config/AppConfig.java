package com.porto.scraper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Loads configuration from scraper.properties.
 *
 * -----------------------------------------------------------------
 *  FILE LOOKUP ORDER
 * -----------------------------------------------------------------
 *  1. ./scraper.properties  (same folder as the JAR)
 *  2. classpath:/scraper.properties                   (bundled fallback)
 *
 *  The external file always wins, so you can override bundled
 *  defaults by placing a scraper.properties next to the JAR.
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static final String CONFIG_FILE    = "scraper.properties";
    private static final int    DEFAULT_POLL   = 5;

    private final Properties props;

    // -- Load --------------------------------------------------------------

    public AppConfig() {
        props = new Properties();

        // 1. Try loading from the directory the JAR was launched from
        Path external = Paths.get(CONFIG_FILE);
        if (Files.exists(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                props.load(in);
                log.info("Config loaded from: {}", external.toAbsolutePath());
                return;
            } catch (IOException e) {
                log.warn("Found {} but failed to read it: {}", external.toAbsolutePath(), e.getMessage());
            }
        }

        // 2. Fall back to the bundled file inside the JAR
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                props.load(in);
                log.info("Config loaded from classpath (bundled defaults).");
            } else {
                log.warn("No {} found — using built-in defaults.", CONFIG_FILE);
            }
        } catch (IOException e) {
            log.warn("Failed to load bundled config: {}", e.getMessage());
        }
    }

    // -- Scheduler ---------------------------------------------------------

    public int getPollIntervalMinutes() {
        String val = props.getProperty("poll.interval.minutes", String.valueOf(DEFAULT_POLL)).trim();
        try {
            int minutes = Integer.parseInt(val);
            if (minutes < 1) {
                log.warn("poll.interval.minutes must be >= 1, defaulting to {}", DEFAULT_POLL);
                return DEFAULT_POLL;
            }
            return minutes;
        } catch (NumberFormatException e) {
            log.warn("Invalid poll.interval.minutes '{}', defaulting to {}", val, DEFAULT_POLL);
            return DEFAULT_POLL;
        }
    }

    // -- Discord -----------------------------------------------------------

    /**
     * Returns the Discord webhook URL, or null if not configured.
     * Null = notifications disabled.
     */
    public String getDiscordWebhookUrl() {
        String val = props.getProperty("discord.webhook.url", "").trim();
        return val.isEmpty() ? null : val;
    }

    // -- Search targets ----------------------------------------------------

    /**
     * Parses all target.N.* properties and returns a list of SearchTargets.
     * Skips any target that is missing required fields (transaction, property, zone)
     * and logs a clear warning so the user knows which entry to fix.
     */
    public List<SearchTarget> getSearchTargets() {
        // Collect all target indices (e.g. 1, 2, 3 from target.1.zone etc.)
        Set<Integer> indices = new TreeSet<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("target.")) {
                String[] parts = key.split("\\.", 3);
                if (parts.length >= 2) {
                    try { indices.add(Integer.parseInt(parts[1])); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }

        List<SearchTarget> targets = new ArrayList<>();

        for (int i : indices) {
            String prefix = "target." + i + ".";

            String transaction = get(prefix + "transaction");
            String property    = get(prefix + "property");
            String zone        = get(prefix + "zone");

            // Validate required fields
            if (transaction == null || property == null || zone == null) {
                log.warn("Target {} is missing required fields (transaction/property/zone) — skipping.", i);
                continue;
            }

            // Optional filters
            Integer minPrice = getInt(prefix + "minPrice");
            Integer maxPrice = getInt(prefix + "maxPrice");
            Integer minRooms = getInt(prefix + "minRooms");
            Integer maxRooms = getInt(prefix + "maxRooms");
            Integer minArea  = getInt(prefix + "minArea");
            Integer maxArea  = getInt(prefix + "maxArea");

            try {
                UrlBuilder builder = new UrlBuilder();

                // Transaction
                switch (transaction.toUpperCase()) {
                    case "BUY"  -> builder.buy();
                    case "RENT" -> builder.rent();
                    default     -> throw new IllegalArgumentException(
                            "Unknown transaction '" + transaction + "' — use BUY or RENT");
                }

                // Property type
                switch (property.toUpperCase()) {
                    case "APARTMENT" -> builder.apartments();
                    case "HOUSE"     -> builder.houses();
                    case "LAND"      -> builder.land();
                    default          -> throw new IllegalArgumentException(
                            "Unknown property '" + property + "' — use APARTMENT, HOUSE, or LAND");
                }

                // Zone
                UrlBuilder.Zone z;
                try {
                    z = UrlBuilder.Zone.valueOf(zone.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Unknown zone '" + zone + "' — see scraper.properties for the full list");
                }
                builder.inZone(z);

                // Optional filters
                if (minPrice != null) builder.minPrice(minPrice);
                if (maxPrice != null) builder.maxPrice(maxPrice);
                if (minRooms != null) builder.minRooms(minRooms);
                if (maxRooms != null) builder.maxRooms(maxRooms);
                if (minArea  != null) builder.minArea(minArea);
                if (maxArea  != null) builder.maxArea(maxArea);

                targets.add(builder.build());

            } catch (IllegalArgumentException e) {
                log.warn("Target {} is invalid — skipping. Reason: {}", i, e.getMessage());
            }
        }

        if (targets.isEmpty()) {
            log.warn("No valid search targets found in config! Check scraper.properties.");
        } else {
            log.info("Loaded {} search target(s) from config.", targets.size());
        }

        return targets;
    }

    // -- Helpers -----------------------------------------------------------

    private String get(String key) {
        String val = props.getProperty(key, "").trim();
        return val.isEmpty() ? null : val;
    }

    private Integer getInt(String key) {
        String val = get(key);
        if (val == null) return null;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            log.warn("Config key '{}' has non-integer value '{}' — ignoring.", key, val);
            return null;
        }
    }
}