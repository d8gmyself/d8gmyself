package com.d8gmyself.concurrency;

import com.google.common.annotations.Beta;
import com.google.common.cache.Cache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 函数执行辅助类，主要提供一些忽略异常执行、带缓存执行等辅助
 * <br />
 * 使用的时候注意因为线程数导致的死锁
 */
@Beta
public class ExecuteUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecuteUtils.class);

    private static final int MIN_RETRY_TIMES = 0;
    private static final int MAX_RETRY_TIMES = 5;

    private static final String DEFAULT_THREAD_POOL_NAME_PREFIX = "ExecuteUtils-DEFAULT-";
    private static final String DEFAULT_SCHEDULED_THREAD_POOL_NAME_PREFIX = "ExecuteUtils-DELAY-";

    private static final ExecutorService DEFAULT_EXECUTE_SERVICE;

    private static final ScheduledThreadPoolExecutor DELAY_EXECUTE_SERVICE;

    private static ExecuteContextSPI executeContextSPI;

    static {
        loadInitialTraceContextSPI();
        if (executeContextSPI == null) {
            executeContextSPI = new ExecuteContextSPI() {
            };
        }
        //线程池配置可以从配置或者启动参数中获取，暂时写死
        DEFAULT_EXECUTE_SERVICE = new ThreadPoolExecutor(
                32,
                64,
                5,
                TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(8192),
                new ThreadFactoryBuilder().setNameFormat(DEFAULT_THREAD_POOL_NAME_PREFIX + "%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        DELAY_EXECUTE_SERVICE = new ScheduledThreadPoolExecutor(
                32,
                new ThreadFactoryBuilder().setNameFormat(DEFAULT_SCHEDULED_THREAD_POOL_NAME_PREFIX + "%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private ExecuteUtils() {

    }

    private static void loadInitialTraceContextSPI() {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            ServiceLoader<ExecuteContextSPI> traceContextSPILoader = ServiceLoader.load(ExecuteContextSPI.class);
            Iterator<ExecuteContextSPI> traceContextSPIIterator = traceContextSPILoader.iterator();
            try {
                if (traceContextSPIIterator.hasNext()) {
                    executeContextSPI = traceContextSPIIterator.next();
                }
            } catch (Throwable ignore) {
                // Do nothing
            }
            return null;
        });
    }

    /**
     * 执行方法
     *
     * @param runnable         要执行的内容
     * @param exceptionHandler 异常处理
     */
    public static void execute(Runnable runnable, Consumer<Throwable> exceptionHandler) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            exceptionHandler.accept(throwable);
        }
    }

    /**
     * 忽略异常执行，异常直接吞掉，只记录日志
     *
     * @param runnable 要执行的内容
     * @param clazz    执行的类
     * @param method   执行的方法
     * @param args     执行的参数
     */
    public static void executeIngoreException(Runnable runnable, Class<?> clazz, String method, Object... args) {
        execute(runnable, throwable ->
                LOGGER.error("{}#{}({}) occurs exception", Optional.ofNullable(clazz).map(Class::getName).orElse(null), Arrays.toString(args), method, throwable)
        );
    }

    /**
     * 忽略异常执行，异常直接吞掉
     *
     * @param runnable 要执行的内容
     */
    public static void executeIngoreException(Runnable runnable) {
        execute(runnable, throwable ->
                LOGGER.error("executeIngoreException occurs exception", throwable)
        );
    }


    /**
     * 执行
     *
     * @param callable         要执行的内容
     * @param defaultValue     异常时的返回值
     * @param exceptionHandler 异常处理
     */
    public static <R> R execute(Callable<? extends R> callable, R defaultValue, Consumer<Throwable> exceptionHandler) {
        try {
            return callable.call();
        } catch (Throwable throwable) {
            exceptionHandler.accept(throwable);
        }
        return defaultValue;
    }

    /**
     * 忽略异常执行，异常直接吞掉，并返回默认值，只记录日志
     *
     * @param callable 要执行的内容
     * @param clazz    执行的类
     * @param method   执行的方法
     * @param args     执行的参数
     */
    public static <R> R executeIngoreException(Callable<? extends R> callable, R defaultValue, Class<?> clazz, String method, Object... args) {
        return execute(callable, defaultValue, throwable ->
                LOGGER.error("{}#{}({}) occurs exception", Optional.ofNullable(clazz).map(Class::getName).orElse(null), Arrays.toString(args), method, throwable)
        );
    }

    /**
     * 带重试的执行
     *
     * @param callable         要执行的内容
     * @param defaultValue     兜底返回值
     * @param maxRetryTimes    最大重试次数
     * @param predicate        结果check，当返回false时，callable会重试
     * @param exceptionHandler 异常处理
     */
    public static <R> R executeWithRetry(Callable<R> callable, R defaultValue, int maxRetryTimes, Function<R, Boolean> predicate, BiConsumer<Throwable, Integer> exceptionHandler) {
        return ExecuteUtils.<R>newRetryCallableBuilder().setOriginalCallable(callable).setDefaultValue(defaultValue).setRetryTimes(maxRetryTimes)
                .setPredicate(predicate).setExceptionHandler(exceptionHandler).buildAndCall();
    }

    /**
     * 带重试的执行
     */
    public static <R> RetryCallableBuilder<R> newRetryCallableBuilder() {
        return new RetryCallableBuilder<>();
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
     * @param <T>      返回时类型
     * @return 结果
     */
    @SuppressWarnings("rawtypes")
    public static <T> T cacheExecute(Object cacheKey, Cache cache, Supplier<T> supplier) {
        return CacheExecuteUtils.cacheExecute(cacheKey, cache, supplier);
    }

    /**
     * 带缓存执行callable
     *
     * @param cacheKey 缓存的key，如果是非string类型，注意equals方法，否则无法控制并发
     * @param supplier 缓存miss时的回调
     * @param <T>      返回时类型
     * @return 结果
     */
    public static <T> T cacheExecute(Object cacheKey, Supplier<T> supplier) {
        return CacheExecuteUtils.cacheExecute(cacheKey, supplier);
    }

    /**
     * 异步执行，会自动处理EagleEye逻辑
     *
     * @param command          要执行的task
     * @param executorService  线程池
     * @param exceptionHandler 异常处理
     */
    public static void asyncExecute(Runnable command, ExecutorService executorService, Consumer<Throwable> exceptionHandler) {
        executorService.execute(new ContextRunnable(command, exceptionHandler));
    }

    /**
     * 异步执行，会自动处理EagleEye逻辑
     *
     * @param command         要执行的task
     * @param executorService 线程池
     */
    public static void asyncExecute(Runnable command, ExecutorService executorService) {
        asyncExecute(command, executorService, null);
    }

    /**
     * 异步执行，使用默认线程池
     *
     * @param command 要执行的command
     */
    public static void asyncExecute(Runnable command) {
        asyncExecute(command, DEFAULT_EXECUTE_SERVICE);
    }

    /**
     * 异步执行，会自动处理EagleEye逻辑
     *
     * @param task            要执行的task
     * @param executorService 线程池
     * @return Future
     */
    public static Future<?> asyncSubmit(Runnable task, ExecutorService executorService) {
        return executorService.submit(new ContextRunnable(task));
    }

    /**
     * 异步执行，使用默认线程池
     *
     * @param task 要执行的task
     * @return Future
     */
    public static Future<?> asyncSubmit(Runnable task) {
        //异步执行的过程中禁止向线程中再次提交任务，防止死锁
        if (Thread.currentThread().getName().startsWith(DEFAULT_THREAD_POOL_NAME_PREFIX)) {
            throw new UnsupportedOperationException("forbid submit async task in async task");
        }
        return asyncSubmit(task, DEFAULT_EXECUTE_SERVICE);
    }

    /**
     * 延迟执行command
     * <p>
     * 注意：默认采用的ScheduledThreadPoolExecutor方式，
     * ScheduledThreadPoolExecutor为无界队列，要在入口处预估好量或者做限流
     * </p>
     *
     * @param command          要执行的command
     * @param delayTime        延迟时间
     * @param delayTimeUnit    延迟时间单位
     * @param exceptionHandler 异常处理逻辑
     */
    public static void delayExecute(Runnable command, int delayTime, TimeUnit delayTimeUnit, Consumer<Throwable> exceptionHandler) {
        DELAY_EXECUTE_SERVICE.schedule(new ContextRunnable(command, exceptionHandler), delayTime, delayTimeUnit);
    }

    /**
     * 周期调度
     * <p>
     * 注意：默认采用的ScheduledThreadPoolExecutor方式，
     * ScheduledThreadPoolExecutor为无界队列，要在入口处预估好量或者做限流
     * </p>
     *
     * @param command          要执行的command
     * @param initialDelay     延迟时间
     * @param period           周期
     * @param timeUnit         上面两项的时间单位
     * @param exceptionHandler 异常处理逻辑
     */
    public static void scheduleAtFixedRate(Runnable command, long initialDelay,
                                           long period,
                                           TimeUnit timeUnit, Consumer<Throwable> exceptionHandler) {
        DELAY_EXECUTE_SERVICE.scheduleAtFixedRate(new ContextRunnable(command, exceptionHandler), initialDelay, period, timeUnit);
    }

    /**
     * 周期调度
     * <p>
     * 注意：默认采用的ScheduledThreadPoolExecutor方式，
     * ScheduledThreadPoolExecutor为无界队列，要在入口处预估好量或者做限流
     * </p>
     *
     * @param command          要执行的command
     * @param initialDelay     延迟时间
     * @param delay            延迟时间
     * @param timeUnit         上面两项的时间单位
     * @param exceptionHandler 异常处理逻辑
     */
    public static void scheduleWithFixedDelay(Runnable command, long initialDelay,
                                              long delay,
                                              TimeUnit timeUnit, Consumer<Throwable> exceptionHandler) {
        DELAY_EXECUTE_SERVICE.scheduleWithFixedDelay(new ContextRunnable(command, exceptionHandler), initialDelay, delay, timeUnit);
    }

    /**
     * 批量异步执行，并获取执行结果
     *
     * @param tasks 要批量执行的任务
     * @param <T>   任务返回值类型
     * @return 执行结果
     * @throws InterruptedException exp
     */
    public static <T> List<? extends Future<T>> parallelInvokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (Thread.currentThread().getName().startsWith(DEFAULT_THREAD_POOL_NAME_PREFIX)) {
            throw new UnsupportedOperationException("forbid submit async task in async task");
        }
        List<ContextCallable<T>> wrappedTasks = tasks.stream().map(ContextCallable::new).collect(Collectors.toList());
        return DEFAULT_EXECUTE_SERVICE.invokeAll(wrappedTasks);
    }

    /**
     * 批量异步执行，并获取执行结果
     *
     * @param tasks 要批量执行的任务
     * @param <T>   任务返回值类型
     * @return 执行结果
     * @throws InterruptedException exp
     */
    public static <T> List<? extends Future<T>> parallelInvokeAll(Collection<? extends Callable<T>> tasks, Consumer<Throwable> exceptionHandler) throws InterruptedException {
        if (Thread.currentThread().getName().startsWith(DEFAULT_THREAD_POOL_NAME_PREFIX)) {
            throw new UnsupportedOperationException("forbid submit async task in async task");
        }
        List<ContextCallable<T>> wrappedTasks = tasks.stream().map(call -> new ContextCallable<>(call, exceptionHandler)).collect(Collectors.toList());
        return DEFAULT_EXECUTE_SERVICE.invokeAll(wrappedTasks);
    }


    public static <T> List<? extends Future<T>> parallelInvokeAllWithThreadPool(final ExecutorService threadPool, Collection<? extends Callable<T>> tasks) throws InterruptedException {
        List<ContextCallable<T>> wrappedTasks = tasks.stream().map(ContextCallable::new).collect(Collectors.toList());
        return threadPool.invokeAll(wrappedTasks);
    }

    /**
     * 销毁
     */
    public static void destory() {
        executeIngoreException(DEFAULT_EXECUTE_SERVICE::shutdown);
        executeIngoreException(DELAY_EXECUTE_SERVICE::shutdown);
        LOGGER.warn("ExecuteUtils DEFAULT_EXECUTE_SERVICE and DELAY_EXECUTE_SERVICE shutdown...");
    }

    /**
     * Runnable，可以自动透传context以及自定义异常处理方式
     */
    private static class ContextRunnable implements Runnable {
        private final Runnable originalRunnable;
        private final Thread originalThread = Thread.currentThread();
        private final Map<String, Object> executeContext = executeContextSPI.getContext();
        private final Consumer<Throwable> exceptionHandler;

        public ContextRunnable(Runnable originalRunnable) {
            this(originalRunnable, null);
        }

        public ContextRunnable(Runnable originalRunnable, Consumer<Throwable> exceptionHandler) {
            this.originalRunnable = originalRunnable;
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public void run() {
            try {
                executeContextSPI.setContext(executeContext);
                originalRunnable.run();
            } catch (Throwable throwable) {
                if (exceptionHandler != null) {
                    exceptionHandler.accept(throwable);
                } else {
                    throw throwable;
                }
            } finally {
                if (Thread.currentThread() != originalThread) {
                    executeContextSPI.clearContext(executeContext);
                }
            }
        }
    }

    /**
     * Callable，可以自动透传context以及自定义异常处理方式
     * 如果发生异常且{@code exceptionHandler}不为null，{@code call()}默认返回null
     */
    private static class ContextCallable<V> implements Callable<V> {
        private final Callable<V> originalCallable;
        private final Thread originalThread = Thread.currentThread();
        private final Map<String, Object> executeContext = executeContextSPI.getContext();

        private final Consumer<Throwable> exceptionHandler;

        public ContextCallable(Callable<V> originalCallable) {
            this(originalCallable, null);
        }

        public ContextCallable(Callable<V> originalCallable, Consumer<Throwable> exceptionHandler) {
            this.originalCallable = originalCallable;
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public V call() throws Exception {
            try {
                executeContextSPI.setContext(executeContext);
                return originalCallable.call();
            } catch (Throwable throwable) {
                if (exceptionHandler != null) {
                    exceptionHandler.accept(throwable);
                    return null;
                } else {
                    throw throwable;
                }
            } finally {
                if (Thread.currentThread() != originalThread) {
                    executeContextSPI.clearContext(executeContext);
                }
            }
        }
    }

    /**
     * Callable，带重试
     */
    private static class RetryCallable<V> implements Callable<V> {
        private final Callable<V> originalCallable;
        private final Function<V, Boolean> predicate;
        private final Integer retryTimes;
        private final V defaultValue;
        private final BiConsumer<Throwable, Integer> exceptionHandler;

        private RetryCallable(RetryCallableBuilder<V> builder) {
            this.originalCallable = builder.getOriginalCallable();
            this.predicate = builder.getPredicate();
            this.retryTimes = builder.getRetryTimes();
            this.defaultValue = builder.getDefaultValue();
            this.exceptionHandler = builder.getExceptionHandler();
        }

        @Override
        public V call() {
            for (int retry = 0; retry <= retryTimes; retry++) {
                try {
                    V callResult = originalCallable.call();
                    if (BooleanUtils.isTrue(predicate.apply(callResult))) {
                        return callResult;
                    }
                } catch (Throwable throwable) {
                    exceptionHandler.accept(throwable, retry);
                }
                Thread.yield();
            }
            return defaultValue;
        }
    }

    public final static class RetryCallableBuilder<V> {
        private Callable<V> originalCallable;
        private Function<V, Boolean> predicate;
        private Integer retryTimes;
        private V defaultValue;
        private BiConsumer<Throwable, Integer> exceptionHandler;

        private RetryCallableBuilder() {

        }

        public Callable<V> getOriginalCallable() {
            if (this.originalCallable == null) {
                this.originalCallable = () -> null;
            }
            return this.originalCallable;
        }

        public RetryCallableBuilder<V> setOriginalCallable(Callable<V> originalCallable) {
            this.originalCallable = originalCallable;
            return this;
        }

        public Function<V, Boolean> getPredicate() {
            if (this.predicate == null) {
                this.predicate = (V) -> true;
            }
            return this.predicate;
        }

        public RetryCallableBuilder<V> setPredicate(Function<V, Boolean> predicate) {
            this.predicate = predicate;
            return this;
        }

        public Integer getRetryTimes() {
            if (retryTimes == null) {
                retryTimes = MIN_RETRY_TIMES;
            }
            return retryTimes;
        }

        public RetryCallableBuilder<V> setRetryTimes(Integer retryTimes) {
            int fixedRetryTimes = NumberUtils.max(MIN_RETRY_TIMES, retryTimes);
            fixedRetryTimes = NumberUtils.min(MAX_RETRY_TIMES, fixedRetryTimes);
            this.retryTimes = fixedRetryTimes;
            return this;
        }

        public V getDefaultValue() {
            return defaultValue;
        }

        public RetryCallableBuilder<V> setDefaultValue(V defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public BiConsumer<Throwable, Integer> getExceptionHandler() {
            if (exceptionHandler == null) {
                exceptionHandler = (throwable, retryTime) -> LOGGER.error("RetryCallable.call occurs exception", throwable);
            }
            return exceptionHandler;
        }

        public RetryCallableBuilder<V> setExceptionHandler(BiConsumer<Throwable, Integer> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public Callable<V> build() {
            return new RetryCallable<>(this);
        }

        public V buildAndCall() {
            return new RetryCallable<>(this).call();
        }
    }

}
