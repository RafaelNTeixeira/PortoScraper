package com.porto.scraper.config;

/**
 * One search job: a human-readable label + the Imovirtual URL to scrape.
 *
 * Build instances via {@link UrlBuilder} — don't construct URLs by hand.
 *
 * Example:
 *   SearchTarget t = new UrlBuilder()
 *       .buy()
 *       .houses()
 *       .inZone(Zone.MATOSINHOS)
 *       .maxPrice(250_000)
 *       .build();
 */
public class SearchTarget {

    private final String label;  // shown in console output, e.g. "BUY | House | Matosinhos"
    private final String url;    // full Imovirtual search URL

    public SearchTarget(String label, String url) {
        this.label = label;
        this.url   = url;
    }

    public String getLabel() { return label; }
    public String getUrl()   { return url; }

    @Override
    public String toString() {
        return label + " -> " + url;
    }
}