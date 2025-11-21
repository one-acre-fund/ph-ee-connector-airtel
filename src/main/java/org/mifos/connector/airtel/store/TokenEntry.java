package org.mifos.connector.airtel.store;


import java.time.LocalDateTime;

public class TokenEntry {

    public final String token;
    public final LocalDateTime expiresOn;

    public TokenEntry(String token, LocalDateTime expiresOn) {
        this.token = token;
        this.expiresOn = expiresOn;
    }

    public String getToken() {
        return token;
    }

    public LocalDateTime getExpiresOn() {
        return expiresOn;
    }

}
