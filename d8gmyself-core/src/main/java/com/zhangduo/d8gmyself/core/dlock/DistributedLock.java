package com.zhangduo.d8gmyself.core.dlock;

import java.util.concurrent.TimeUnit;

/**
 * Created by ZhangDuo on 2016-11-19 16:25.
 * <p>
 * 简单实现分布式锁，只提供阻塞的lock、unLock方法
 */
public interface DistributedLock {

    /**
     * 获取锁，阻塞，直到成功
     *
     * @param key 要加锁的key
     */
    void lock(String key);

    /**
     * 获取锁，阻塞，直到成功，响应中断
     *
     * @param key 要加锁的key
     * @throws InterruptedException 中断
     */
    void lockInterruptibly(String key) throws InterruptedException;

    /**
     * 尝试一次获取锁，成功返回true，失败返回false
     * @param key 要加锁的key
     * @return 锁成功返回true，失败返回false
     */
    boolean tryLock(String key);

    /**
     * 指定timeout时间内获取锁，获取成功返回true，失败返回false
     * @param key 要加锁的key
     * @param timeout 超时时间
     * @param timeoutUnit 超时时间单位
     * @return 获取成功返回true，失败返回false
     * @throws InterruptedException 中断
     */
    boolean tryLock(String key, int timeout, TimeUnit timeoutUnit) throws InterruptedException;

    /**
     * 释放锁
     * @param key 要释放的key
     */
    void unLock(String key);

}
