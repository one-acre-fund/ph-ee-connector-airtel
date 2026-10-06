package org.mifos.connector.airtel.store;

import org.mifos.connector.airtel.config.RedisStoreProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Class that holds the access tokens by country in Redis.
 */
@Component
public class AccessTokenStore {

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public AccessTokenStore(StringRedisTemplate redisTemplate, RedisStoreProperties props) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = props.getKeyPrefix();
    }

    /**
     * Atomically stores the token with TTL equal to {@code expiresIn} seconds.
     * Tokens with non-positive expiry are removed so callers see them as invalid.
     */
    public void setAccessToken(String country, String accessToken, int expiresIn) {
        String key = accessTokenKey(country);
        if (expiresIn <= 0) {
            redisTemplate.delete(key);
            return;
        }
        redisTemplate.opsForValue().set(key, accessToken, expiresIn, TimeUnit.SECONDS);
    }

    public TokenEntry getAccessToken(String country) {
        String key = accessTokenKey(country);
        String token = redisTemplate.opsForValue().get(key);
        if (token == null) {
            return null;
        }
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        LocalDateTime expiresOn = (ttlSeconds != null && ttlSeconds > 0)
                ? LocalDateTime.now().plusSeconds(ttlSeconds)
                : LocalDateTime.now();
        return new TokenEntry(token, expiresOn);
    }

    public LocalDateTime getExpiresOn(String country) {
        TokenEntry entry = getAccessToken(country);
        return entry != null ? entry.getExpiresOn() : null;
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

    private String accessTokenKey(String country) {
        return keyPrefix + ":access_token:" + country;
    }
}
