package com.zhangduo.d8gmyself.core.eventbus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * 订阅者
 *
 * @author zhangduo -- 2018/1/28
 */
public class Subscriber {

    private final EventBus bus;
    private final Method method;
    private final Object target;
    private final int order;
    private final boolean allowConccurrency;
    private final boolean async;
    private final ExecutorService executor;

    Subscriber(EventBus bus, Method method, Object target, int order, boolean allowConccurrency, boolean async, ExecutorService executor) {
        this.bus = bus;
        this.method = method;
        this.target = target;
        this.order = order;
        this.allowConccurrency = allowConccurrency;
        this.async = async;
        this.executor = executor;
    }

    /**
     * 处理事件
     *
     * @param event 要处理的事件信息
     */
    void processEvent(Object event) {
        if (async && executor != null) {
            executor.execute(() -> {
                invokeSubscriberMethod(event);
            });
        } else {
            invokeSubscriberMethod(event);
        }
    }

    /**
     * 执行事件处理方法
     *
     * @param event 要处理的事件
     */
    private void invokeSubscriberMethod(Object event) {
        try {
            if (allowConccurrency) {
                invokeSubscriberMethodConcurrency(event);
            } else {
                invokeSubscriberMethodSync(event);
            }
        } catch (Exception e) {
            bus.handleSubscriberException(e, target, method, event);
        }
    }

    /**
     * 执行事件处理方法
     *
     * @param event 要处理的事件
     * @throws InvocationTargetException
     */
    private void invokeSubscriberMethodConcurrency(Object event) throws InvocationTargetException {
        try {
            method.invoke(target, checkNotNull(event));
        } catch (IllegalArgumentException e) {
            throw new Error("Method rejected target/argument: " + event, e);
        } catch (IllegalAccessException e) {
            throw new Error("Method became inaccessible: " + event, e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            throw e;
        }
    }

    /**
     * 执行事件处理方法
     *
     * @param event 要处理的事件
     * @throws InvocationTargetException
     */
    private void invokeSubscriberMethodSync(Object event) throws InvocationTargetException {
        synchronized (this) {
            invokeSubscriberMethodConcurrency(event);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subscriber that = (Subscriber) o;
        return Objects.equals(method, that.method) &&
                Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, target);
    }

    int getOrder() {
        return order;
    }

}
