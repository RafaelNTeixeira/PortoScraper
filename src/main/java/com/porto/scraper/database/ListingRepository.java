package com.porto.scraper.database;

import com.porto.scraper.model.Listing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;

/**
 * Manages a local SQLite database of every listing the scraper has ever seen.
 *
 * -----------------------------------------------------------------
 *  DATABASE FILE
 * -----------------------------------------------------------------
 *  Created automatically at:  ./scraper.db  (next to the JAR)
 *  You can open it with:       DB Browser for SQLite (free, GUI)
 *                              or any SQLite CLI tool
 *
 * -----------------------------------------------------------------
 *  TABLE SCHEMA
 * -----------------------------------------------------------------
 *  seen_listings
 *  ┌-------------┬-------------------------------------------------┐
 *  │ id          │ TEXT PRIMARY KEY  - listing ID from Imovirtual  │
 *  │ title       │ TEXT              - stored for your reference   │
 *  │ price       │ TEXT              - stored for your reference   │
 *  │ location    │ TEXT              - stored for your reference   │
 *  │ url         │ TEXT              - stored for your reference   │
 *  │ source      │ TEXT              - e.g. "imovirtual"           │
 *  │ seen_at     │ TEXT              - ISO-8601 timestamp          │
 *  └-------------┴-------------------------------------------------┘
 *
 */
public class ListingRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ListingRepository.class);

    private static final String DB_FILE = "scraper.db";
    private static final String DB_URL  = "jdbc:sqlite:" + DB_FILE;

    private final Connection conn;

    // -- Constructor: open DB and create table if needed -------------------

    public ListingRepository() throws SQLException {
        conn = DriverManager.getConnection(DB_URL);
        createTableIfAbsent();
        log.info("Database ready: {}", DB_FILE);
    }

    // -- Public API --------------------------------------------------------

    /**
     * Returns true if this listing ID has never been seen before.
     * This is the core deduplication check called for every scraped listing.
     */
    public boolean isNew(String listingId) throws SQLException {
        String sql = "SELECT 1 FROM seen_listings WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next(); // true = not found = it's new
            }
        }
    }

    /**
     * Saves a listing to the database so future runs skip it.
     * Call this only AFTER you've already acted on the listing
     * (printed it / sent a Discord notification), so a crash mid-run
     * doesn't silently swallow a listing.
     */
    public void markSeen(Listing listing, String source) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO seen_listings
              (id, title, price, location, url, source, seen_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, listing.getId());
            ps.setString(2, listing.getTitle());
            ps.setString(3, listing.getPrice());
            ps.setString(4, listing.getLocation());
            ps.setString(5, listing.getUrl());
            ps.setString(6, source);
            ps.setString(7, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    /**
     * Returns the total number of listings stored in the database.
     * Useful for logging - lets you see the DB growing over time.
     */
    public int totalSeen() throws SQLException {
        String sql = "SELECT COUNT(*) FROM seen_listings";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Removes all records from the database.
     * Useful if you want to re-process all current listings from scratch
     * (e.g. after changing your search filters significantly).
     */
    public void clearAll() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM seen_listings");
            log.warn("Database cleared - all listings will be treated as new on next run.");
        }
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    // -- Private -----------------------------------------------------------

    private void createTableIfAbsent() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS seen_listings (
                id        TEXT PRIMARY KEY,
                title     TEXT,
                price     TEXT,
                location  TEXT,
                url       TEXT,
                source    TEXT,
                seen_at   TEXT NOT NULL
            )
            """;
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }
}