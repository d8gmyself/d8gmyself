package com.d8gmyself.core.eventbus;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;

/**
 * 事件总线
 *
 * @author zhangduo -- 2018/1/28
 */
public class EventBus {

    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);
    private final String identifier;
    private final ExecutorService executor;
    private final Dispatcher dispatcher;
    private final SubscriberRegistry subscriberRegistry = new SubscriberRegistry(this);
    private final SubscriberExceptionHandler exceptionHandler;

    /**
     * 创建事件总线
     *
     * @param identifier                 总线标识
     * @param executor                   线程池，用于异步执行订阅者行为
     * @param subscriberExceptionHandler 订阅者异常处理
     */
    private EventBus(String identifier, ExecutorService executor, SubscriberExceptionHandler subscriberExceptionHandler) {
        this(identifier, executor, new OrderedDispatcher(), subscriberExceptionHandler);
    }

    /**
     * 创建时间总线
     * <p>
     * 若{@code identifier}为空，会默认命名为“default”
     * 若{@code exceptionHandler}为空，会默认指定{@link DefaultSubscriberExceptionHandler}
     * </p>
     *
     * @param identifier       总线标识
     * @param executor         线程池，用于异步执行订阅者行为
     * @param dispatcher       事件分发器
     * @param exceptionHandler 订阅者异常处理
     */
    private EventBus(String identifier, ExecutorService executor, Dispatcher dispatcher, SubscriberExceptionHandler exceptionHandler) {
        if (StringUtils.isBlank(identifier)) {
            identifier = "default";
        }
        if (exceptionHandler == null) {
            exceptionHandler = new DefaultSubscriberExceptionHandler();
        }
        this.identifier = identifier;
        this.executor = executor;
        this.dispatcher = dispatcher;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * 获取EventBus的builder
     *
     * @return EventBusBuilder
     */
    public static EventBusBuilder builder() {
        return new EventBusBuilder();
    }

    /**
     * 订阅者异常处理
     *
     * @param exp              异常信息
     * @param subscriber       订阅者所在对象
     * @param subscriberMethod 事件处理方法
     * @param args             入参（事件）
     */
    void handleSubscriberException(Throwable exp, Object subscriber, Method subscriberMethod, Object... args) {
        exceptionHandler.handleException(exp, subscriber, subscriberMethod, args);
    }

    /**
     * 发布事件
     *
     * @param event 要发布的事件
     */
    public void post(Object event) {
        Iterator<Subscriber> subscribers = subscriberRegistry.getSubscribers(event);
        if (subscribers.hasNext()) {
            dispatcher.dispatch(event, subscribers);
        } else if (!(event instanceof DeadEvent)) {
            post(new DeadEvent(this, event));
        } else {
            logger.warn("事件:{}未被任何listener处理，系统未配置DeadEventListener", event);
        }
    }

    /**
     * 注册订阅者
     *
     * @param object 订阅者所在对象
     */
    public void register(Object object) {
        subscriberRegistry.register(object);
    }

    /**
     * 移除订阅者
     *
     * @param object 订阅者所在对象
     */
    public void unregister(Object object) {
        subscriberRegistry.unregister(object);
    }

    /**
     * 获取事件总线标识
     *
     * @return 标识
     */
    public final String identifier() {
        return this.identifier;
    }

    /**
     * 获取线程池
     *
     * @return 线程池
     */
    final ExecutorService getExecutor() {
        return this.executor;
    }

    @Override
    public String toString() {
        return "EventBus{" +
                "identifier='" + identifier + '\'' +
                '}';
    }

    /**
     * 默认订阅者异常处理方式（log）
     */
    static class DefaultSubscriberExceptionHandler implements SubscriberExceptionHandler {

        private static final Logger logger = LoggerFactory.getLogger(EventBus.class);

        @Override
        public void handleException(Throwable exp, Object subscriber, Method subscriberMethod, Object... args) {
            logger.error("process event error, subscriber:{}, method:{}, event:{}", subscriber.getClass().getName(), subscriberMethod.getName(),
                    Arrays.toString(args), exp);
        }
    }

    public static class EventBusBuilder {

        private String identifier;
        private ExecutorService executor;
        private SubscriberExceptionHandler subscriberExceptionHandler;

        private EventBusBuilder() {
        }

        public EventBus build() {
            return new EventBus(this.identifier, this.executor, this.subscriberExceptionHandler);
        }

        public EventBusBuilder setIdentifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public EventBusBuilder setExecutor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public EventBusBuilder setSubscriberExceptionHandler(SubscriberExceptionHandler subscriberExceptionHandler) {
            this.subscriberExceptionHandler = subscriberExceptionHandler;
            return this;
        }
    }

}
