package com.porto.scraper.model;

/**
 * Represents one real-estate listing scraped from a portal.
 *
 * Future phases will add: bedrooms, area (m²), floor, photos, etc.
 */
public class Listing {

    private final String id;       // Unique listing ID extracted from the URL or DOM
    private final String title;    // e.g. "T2 no Porto, Bonfim"
    private final String price;    // e.g. "950 €/mês"  (raw string, we parse later)
    private final String location; // e.g. "Bonfim, Porto"
    private final String url;      // Direct link to the listing page

    public Listing(String id, String title, String price, String location, String url) {
        this.id       = id;
        this.title    = title;
        this.price    = price;
        this.location = location;
        this.url      = url;
    }

    // -- Getters -------------------------------------------------------------

    public String getId()       { return id; }
    public String getTitle()    { return title; }
    public String getPrice()    { return price; }
    public String getLocation() { return location; }
    public String getUrl()      { return url; }

    // -- Pretty print ---------------------------------------------------------

    @Override
    public String toString() {
        return String.format(
                "[%s] %s | %s | %s%n  -> %s",
                id, title, price, location, url
        );
    }
}