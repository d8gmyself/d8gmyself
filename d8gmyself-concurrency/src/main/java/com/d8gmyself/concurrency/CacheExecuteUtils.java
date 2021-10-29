package com.d8gmyself.concurrency;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 缓存工具类 <br />
 * 后续可升级为Caffeine或者自己wrap一层
 */
public class CacheExecuteUtils {

    /**
     * 用于对cacheKey加锁
     */
    private static final Interner<Object> INTERNER = Interners.newWeakInterner();
    /**
     * 空对象，防击穿
     */
    public static final Object NULL = new Object();
    /**
     * 公共缓存，使用本地缓存，只缓存1分钟，主要用于解决短时间内相同api的重复调用
     */
    private static final Cache<Object, Object> COMMON_CACHE = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).softValues().build();

    /**
     * <p>
     * 带缓存执行callable
     * null值默认会被缓存
     * </p>
     *
     * @param cacheKey 缓存的key，如果是非string类型，注意equals方法，否则无法控制并发
     * @param cache    使用的缓存
     * @param supplier 缓存miss时的回调
     * @param <T>      返回时类型
     * @return 结果
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> T cacheExecute(Object cacheKey, Cache cache, Supplier<T> supplier) {
        return cacheExecute(cacheKey, cache, supplier, t -> false);
    }

    /**
     * <p>
     * 带缓存执行callable
     * null值默认会被缓存
     * </p>
     *
     * @param cacheKey 缓存的key，如果是非string类型，注意equals方法，否则无法控制并发
     * @param cache    使用的缓存
     * @param supplier 缓存miss时的回调
     * @param notCache 特定的不需要缓存的结果
     * @param <T>      返回时类型
     * @return 结果
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> T cacheExecute(Object cacheKey, Cache cache, Supplier<T> supplier, Predicate<T> notCache) {
        Object obj = cache.getIfPresent(cacheKey);
        if (obj == null) {
            synchronized (INTERNER.intern(cacheKey)) {
                obj = cache.getIfPresent(cacheKey);
                if (obj != null) {
                    if (obj == NULL) {
                        return null;
                    }
                    return (T) obj;
                }
                obj = supplier.get();
                if (notCache.test((T) obj)) {
                    return (T) obj;
                }
                if (obj == null) {
                    cache.put(cacheKey, NULL);
                    return null;
                }
                cache.put(cacheKey, obj);
                return (T) obj;
            }
        } else {
            if (obj == NULL) {
                return null;
            }
            return (T) obj;
        }
    }

    /**
     * 带缓存执行
     *
     * @param cacheKey 缓存的key，如果是非string类型，注意equals方法，否则无法控制并发
     * @param supplier 缓存miss时的回调
     * @param <T>      返回时类型
     * @return 结果
     */
    public static <T> T cacheExecute(Object cacheKey, Supplier<T> supplier) {
        return cacheExecute(cacheKey, COMMON_CACHE, supplier);
    }

    /**
     * 带缓存执行
     *
     * @param cacheKey 缓存的key，如果是非string类型，注意equals方法，否则无法控制并发
     * @param supplier 缓存miss时的回调
     * @param notCache 特定的不需要缓存的结果
     * @param <T>      返回时类型
     * @return 结果
     */
    public static <T> T cacheExecute(Object cacheKey, Supplier<T> supplier, Predicate<T> notCache) {
        return cacheExecute(cacheKey, COMMON_CACHE, supplier, notCache);
    }

    /**
     * 失效所有缓存
     */
    public static void invalidateAll() {
        COMMON_CACHE.invalidateAll();
    }

    /**
     * 失效指定缓存
     *
     * @param cacheKey 缓存key
     */
    public static void invalidate(Object cacheKey) {
        COMMON_CACHE.invalidate(cacheKey);
    }

    /**
     * 新增缓存
     *
     * @param cache cache
     * @param key   key
     * @param value value
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addCache(Cache cache, String key, Object value) {
        synchronized (INTERNER.intern(key)) {
            if (value == null) {
                cache.put(key, NULL);
            } else {
                cache.put(key, value);
            }
        }
    }

    /**
     * 获取缓存中的值
     *
     * @param cache cache
     * @param key   key
     * @return 缓存值
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> Optional<T> getFromCache(Cache cache, String key) {
        Object present = cache.getIfPresent(key);
        if (present == null || present == NULL) {
            return Optional.empty();
        }
        return Optional.of((T) present);
    }

}
