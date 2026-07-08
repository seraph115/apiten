package cn.iocoder.yudao.module.org.service.auth;

public interface NonceStore {
    /** 首次出现返回 true 并记录；窗口内重复返回 false */
    boolean tryAcquire(String key, long ttlMs);
}
