package com.porto.scraper.config;

public class SearchTarget {

    private final String            label;
    private final String            url;
    private final UrlBuilder.Source source;

    public SearchTarget(String label, String url, UrlBuilder.Source source) {
        this.label  = label;
        this.url    = url;
        this.source = source;
    }

    public String            getLabel()  { return label;  }
    public String            getUrl()    { return url;    }
    public UrlBuilder.Source getSource() { return source; }

    @Override
    public String toString() { return label + " -> " + url; }
}