package com.zhangduo.d8gmyself.core.dlock.impl;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.zhangduo.d8gmyself.core.dlock.DistributedLock;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ZhangDuo on 2016-11-19 14:59.
 */
public class ZKDistributedLockImpl implements DistributedLock {

    private static final int DEFAULT_SESSION_TIMEOUT_MILLIS = 10 * 1000;
    private static final int DEFAULT_CONNECTION_TIMEOUT_MILLIS = 8 * 1000;
    private final CuratorFramework client;
    private final ThreadLocal<Map<String, AtomicInteger>> count = new ThreadLocal<Map<String, AtomicInteger>>() {
        @Override
        protected Map<String, AtomicInteger> initialValue() {
            return new ConcurrentHashMap<String, AtomicInteger>();
        }
    };
    private final Interner<String> interner = Interners.newWeakInterner();
    private final Watcher myWatcher = new MyWatcher();

    /**
     * 创建zk锁，默认连接本机zk {@code "127.0.0.1:2181"}，
     * {@code sessionTimeoutMillis = 10 * 1000}，{@code connectionTimeoutMillis = 8 * 1000}，{@code retryPolicy = new ExponentialBackoffRetry(1000, 3)}
     */
    public ZKDistributedLockImpl() {
        this("127.0.0.1:2181",
                DEFAULT_SESSION_TIMEOUT_MILLIS, DEFAULT_CONNECTION_TIMEOUT_MILLIS, new ExponentialBackoffRetry(1000, 3));
    }

    /**
     * 创建zk锁
     *
     * @param client zk客户端
     */
    public ZKDistributedLockImpl(CuratorFramework client) {
        this.client = client;
        if (client.getState() != CuratorFrameworkState.STARTED) {
            this.client.start();

        }
    }

    /**
     * 创建zk锁
     *
     * @param connectString           连接字符串，可指定多台zk服务器，eg:"server1:2181,server2:2181,server3:2181"
     * @param sessionTimeoutMillis    会话超时时间
     * @param connectionTimeoutMillis 连接超时时间
     * @param retryPolicy             重连政策
     */
    public ZKDistributedLockImpl(String connectString, int sessionTimeoutMillis, int connectionTimeoutMillis, RetryPolicy retryPolicy) {
        client = CuratorFrameworkFactory.newClient(connectString, sessionTimeoutMillis, connectionTimeoutMillis, retryPolicy);
        client.start();
    }

    @Override
    public void lock(final String key) {
        while (true) {
            try {
                if (tryLock(key, 0)) {
                    return;
                }
            } catch (InterruptedException ignoredExp) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void lockInterruptibly(final String key) throws InterruptedException {
        tryLock(key, 0);
    }

    @Override
    public boolean tryLock(final String key) {
        if (count.get().get(key) != null) { //单线程重入
            count.get().get(key).incrementAndGet();
            return true;
        } else {
            try {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(key); //创建锁
                count.get().put(key, new AtomicInteger(1));
                return true;
            } catch (KeeperException.NodeExistsException ignoredExp) { //锁已存在,watch && wait
                //ignore exp
                return false;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean tryLock(String key, int timeout, TimeUnit timeoutUnit) throws InterruptedException {
        Preconditions.checkArgument(timeout > 0);
        return tryLock(key, timeoutUnit.toMillis(timeout));
    }

    private boolean tryLock(final String path, final long millisToWait) throws InterruptedException {
        Long waitMillis = millisToWait == 0 ? null : millisToWait;
        long start = System.currentTimeMillis();
        if (count.get().get(path) != null) { //单线程重入
            count.get().get(path).incrementAndGet();
            return true;
        } else {
            while (true) {
                try {
                    client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path); //创建锁
                    count.get().put(path, new AtomicInteger(1));
                    return true;
                } catch (KeeperException.NodeExistsException e) { //锁已存在,watch && wait
                    try {
                        synchronized (interner.intern(path)) {
                            client.getChildren().usingWatcher(myWatcher).forPath(path);
                            if (waitMillis != null) {
                                waitMillis -= System.currentTimeMillis() - start;
                                start = System.currentTimeMillis();
                                if (waitMillis <= 0) {
                                    return false;
                                } else {
                                    interner.intern(path).wait(waitMillis / 2);
                                }
                            } else {
                                //防止并发的情况下睡死过去，所以每隔50毫秒尝试一下
                                interner.intern(path).wait(TimeUnit.MILLISECONDS.toMillis(50));
                            }
                        }
                    } catch (KeeperException.NoNodeException ex) {
                        //ignore
                    } catch (Exception exp) {
                        Throwables.propagateIfPossible(exp, InterruptedException.class);
                    }
                } catch (Exception e) {
                    Throwables.propagateIfPossible(e, null);
                }
            }
        }
    }

    @Override
    public void unLock(final String path) {
        try {
            synchronized (interner.intern(path)) {
                if (count.get().get(path) != null && count.get().get(path).addAndGet(-1) == 0) {
                    client.delete().forPath(path);
                    count.get().remove(path);
                }
            }
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    /**
     * 关闭zkClient连接
     */
    @PreDestroy
    public void beforeDestory() {
        if (client != null) {
            client.close();
        }
    }

    private class MyWatcher implements Watcher {
        @Override
        public void process(WatchedEvent event) {
            if (event.getType() == Event.EventType.NodeDeleted) {
                synchronized (interner.intern(event.getPath())) {
                    interner.intern(event.getPath()).notifyAll();
                }
            }
        }
    }


}
