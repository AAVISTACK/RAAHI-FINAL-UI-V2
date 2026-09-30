package in.raahi.backend.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Fixed-window counter: INCR then EXPIRE-if-first-hit. Not perfectly atomic against a
     * pathological race on the very first request in a window (two parallel requests could
     * both see count==1 and both set the TTL), but that only ever affects the window's exact
     * boundary by a few milliseconds — entirely acceptable for abuse mitigation, not a
     * security boundary that needs to be exact. If Redis itself is unreachable, this fails
     * OPEN (allows the request) rather than taking the endpoint down — a rate limiter should
     * never become an availability outage for the feature it's protecting.
     */
    public boolean allow(String key, int maxRequests, Duration window) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) return true;
            if (count == 1L) {
                redisTemplate.expire(key, window);
            }
            return count <= maxRequests;
        } catch (Exception e) {
            return true;
        }
    }
}
