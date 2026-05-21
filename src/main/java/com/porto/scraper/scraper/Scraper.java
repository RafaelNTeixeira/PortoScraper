package com.porto.scraper.scraper;

import com.porto.scraper.model.Listing;
import java.util.List;

/**
 * Common contract for all real-estate scrapers.
 * Every source (Imovirtual, Idealista, ...) implements this.
 */
public interface Scraper {

    /**
     * Fetches the search page and returns all listings found on it.
     * Never throws - returns an empty list on failure.
     */
    List<Listing> scrape();
}