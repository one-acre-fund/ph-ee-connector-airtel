package org.mifos.connector.airtel.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mifos.connector.airtel.config.RedisStoreProperties;
import org.mifos.connector.airtel.store.AccessTokenStore;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessTokenStoreTest {

    private static final String COUNTRY = "UG";
    private static final String KEY_PREFIX = "test-prefix";
    private static final String ACCESS_TOKEN_KEY = KEY_PREFIX + ":access_token:" + COUNTRY;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AccessTokenStore accessTokenStore;

    @BeforeEach
    void setUp() {
        RedisStoreProperties properties = new RedisStoreProperties();
        properties.setKeyPrefix(KEY_PREFIX);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        accessTokenStore = new AccessTokenStore(redisTemplate, properties);
    }

    @DisplayName("Store and retrieve access token string value")
    @Test
    void store_and_retrieve_access_token() {
        accessTokenStore.setAccessToken(COUNTRY, "test-access-token-123", 3600);

        verify(valueOperations).set(ACCESS_TOKEN_KEY, "test-access-token-123", 3600, TimeUnit.SECONDS);

        when(valueOperations.get(ACCESS_TOKEN_KEY)).thenReturn("test-access-token-123");
        when(redisTemplate.getExpire(ACCESS_TOKEN_KEY, TimeUnit.SECONDS)).thenReturn(3600L);

        assertEquals("test-access-token-123", accessTokenStore.getAccessToken(COUNTRY).getToken());
    }

    @DisplayName("Check token validity with null datetime parameter")
    @Test
    void check_token_validity_with_null_datetime() {
        when(valueOperations.get(ACCESS_TOKEN_KEY)).thenReturn("token");
        when(redisTemplate.getExpire(ACCESS_TOKEN_KEY, TimeUnit.SECONDS)).thenReturn(3600L);

        boolean isValid = accessTokenStore.isValid(COUNTRY, null);
        assertFalse(isValid, "isValid should return false when datetime is null");
    }

    @DisplayName("Return true when input datetime is before expiration time")
    @Test
    void test_valid_token_before_expiry() {
        when(valueOperations.get(ACCESS_TOKEN_KEY)).thenReturn("token");
        when(redisTemplate.getExpire(ACCESS_TOKEN_KEY, TimeUnit.SECONDS)).thenReturn(3600L);
        LocalDateTime testTime = LocalDateTime.now();

        boolean isValid = accessTokenStore.isValid(COUNTRY, testTime);

        assertTrue(isValid);
        assertNotNull(accessTokenStore.getExpiresOn(COUNTRY), "Expiration time should be set");
        assertTrue(accessTokenStore.getExpiresOn(COUNTRY).isAfter(LocalDateTime.now()),
                "Expiration time should be in the future");
    }

    @DisplayName("Return false when token is missing after non-positive expiresIn")
    @Test
    void test_expired_token_after_expiry() {
        accessTokenStore.setAccessToken(COUNTRY, "token", -3600);

        verify(redisTemplate).delete(ACCESS_TOKEN_KEY);
        when(valueOperations.get(ACCESS_TOKEN_KEY)).thenReturn(null);

        assertFalse(accessTokenStore.isValid(COUNTRY, LocalDateTime.now()));
        assertNull(accessTokenStore.getAccessToken(COUNTRY));
    }
}
