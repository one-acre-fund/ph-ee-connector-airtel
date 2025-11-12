package org.mifos.connector.airtel.auth;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.AirtelMoneyConnectorApplicationTests;
import org.mifos.connector.airtel.store.AccessTokenStore;

import java.time.LocalDateTime;

class AccessTokenStoreTest extends AirtelMoneyConnectorApplicationTests {

    private static final String COUNTRY = "UG";

    @DisplayName("Store and retrieve access token string value")
    @Test
    void store_and_retrieve_access_token() {
        AccessTokenStore tokenStore = new AccessTokenStore();

        String testToken = "test-access-token-123";
        tokenStore.setAccessToken(COUNTRY, testToken, 3600);

        String retrievedToken = tokenStore.getAccessToken(COUNTRY).getToken();

        Assertions.assertEquals(testToken, retrievedToken);
    }

    @DisplayName("Check token validity with null datetime parameter")
    @Test
    void check_token_validity_with_null_datetime() {
        AccessTokenStore tokenStore = new AccessTokenStore();
        boolean isValid = tokenStore.isValid(COUNTRY, null);
        Assertions.assertFalse(isValid, "isValid should return false when datetime is null");
    }

    @DisplayName("Return true when input datetime is before expiration time")
    @Test
    void test_valid_token_before_expiry() {
        AccessTokenStore tokenStore = new AccessTokenStore();
        tokenStore.setAccessToken(COUNTRY, "token", 3600);
        LocalDateTime testTime = LocalDateTime.now();

        boolean isValid = tokenStore.isValid(COUNTRY, testTime);

        Assertions.assertTrue(isValid);
        Assertions.assertNotNull(tokenStore.getExpiresOn(COUNTRY), "Expiration time should be set");
        Assertions.assertTrue(tokenStore.getExpiresOn(COUNTRY).isAfter(LocalDateTime.now()),
                "Expiration time should be in the future");
    }

    @DisplayName("Return false when input datetime is after expiration time")
    @Test
    void test_expired_token_after_expiry() {
        AccessTokenStore tokenStore = new AccessTokenStore();
        tokenStore.setAccessToken(COUNTRY, "token", -3600);
        LocalDateTime testTime = LocalDateTime.now();

        boolean isValid = tokenStore.isValid(COUNTRY, testTime);

        Assertions.assertFalse(isValid);
    }
}
