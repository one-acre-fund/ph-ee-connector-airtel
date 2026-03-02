package org.mifos.connector.airtel.util;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Models the mtn values.
 */
@Component
@Configuration
@ConfigurationProperties(prefix = "country")
public class CountryProps {

    private Map<String, String> currency;

    public Map<String, String> getCurrency() {
        return currency;
    }

    public void setCurrency(Map<String, String> currency) {
        this.currency = currency;
    }
}
