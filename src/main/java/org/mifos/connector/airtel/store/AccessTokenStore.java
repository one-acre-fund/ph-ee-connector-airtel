package org.mifos.connector.airtel.store;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class that holds the access tokens by country.
 */
@Component
public class AccessTokenStore {
    private final ConcurrentHashMap<String, TokenEntry> tokens = new ConcurrentHashMap<>();

    public void setAccessToken(String country, String accessToken, int expiresIn) {
        tokens.put(country, new TokenEntry(accessToken, LocalDateTime.now().plusSeconds(expiresIn)));
    }

    public TokenEntry getAccessToken(String country) {
        return tokens.get(country);
    }

    public LocalDateTime getExpiresOn(String country) {
        return getAccessToken(country).getExpiresOn();
    }

    /**
     * Checks if the token is still valid.
     *
     * @param dateTime
     *            the date to check time against
     * @return boolean
     */
    public boolean isValid(String country, LocalDateTime dateTime) {
        TokenEntry expiry = getAccessToken(country);
        return expiry != null && dateTime != null && dateTime.isBefore(expiry.getExpiresOn());
    }
}
