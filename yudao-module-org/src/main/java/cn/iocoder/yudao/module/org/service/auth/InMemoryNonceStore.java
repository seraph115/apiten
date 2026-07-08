package cn.iocoder.yudao.module.org.service.auth;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例/内存版 nonce 去重存储。多实例部署需换 Redis 实现。
 */
@Component
public class InMemoryNonceStore implements NonceStore {

    private final Map<String, Long> seen = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, long ttlMs) {
        long now = System.currentTimeMillis();
        seen.entrySet().removeIf(e -> e.getValue() < now); // 惰性清理过期
        return seen.putIfAbsent(key, now + ttlMs) == null;
    }
}
