package com.ddmo.app.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单内存限流：按 key（IP / 用户名）在时间窗口内限制失败次数。
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 10;
    private static final long WINDOW_MS = 15 * 60 * 1000L;

    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public void assertAllowed(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String k = key.trim().toLowerCase();
        long now = System.currentTimeMillis();
        Deque<Long> q = failures.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, now);
            if (q.size() >= MAX_FAILURES) {
                throw new IllegalArgumentException("尝试过于频繁，请 " + (WINDOW_MS / 60000) + " 分钟后再试");
            }
        }
    }

    public void recordFailure(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String k = key.trim().toLowerCase();
        long now = System.currentTimeMillis();
        Deque<Long> q = failures.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, now);
            q.addLast(now);
        }
    }

    public void clear(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        failures.remove(key.trim().toLowerCase());
    }

    private static void prune(Deque<Long> q, long now) {
        long cutoff = now - WINDOW_MS;
        Iterator<Long> it = q.iterator();
        while (it.hasNext()) {
            if (it.next() < cutoff) {
                it.remove();
            } else {
                break;
            }
        }
    }
}
