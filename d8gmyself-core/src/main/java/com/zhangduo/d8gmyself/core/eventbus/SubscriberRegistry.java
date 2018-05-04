package com.zhangduo.d8gmyself.core.eventbus;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.*;
import com.google.common.reflect.TypeToken;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * 订阅者注册管理
 *
 * @author zhangduo -- 2018/1/28
 */
class SubscriberRegistry {

    private EventBus bus;
    /**
     * 事件类型-处理方式映射
     */
    private final Map<Class<?>, Set<Subscriber>> subscribers = Maps.newConcurrentMap();
    private final LoadingCache<Class<?>, List<Method>> classMethodsCache = CacheBuilder.newBuilder().weakKeys()
            .build(new CacheLoader<Class<?>, List<Method>>() {
                @Override
                public List<Method> load(Class<?> key) {
                    return ImmutableList.copyOf(key.getDeclaredMethods());
                }
            });
    private final LoadingCache<Class<?>, List<Method>> subscriberMethodsCache = CacheBuilder.newBuilder().weakKeys()
            .build(new CacheLoader<Class<?>, List<Method>>() {
                @Override
                public List<Method> load(Class<?> key) {
                    return findAllMethodAnnotatedSubscribeNotCached(key);
                }
            });
    private final LoadingCache<Class<?>, Set<Class<?>>> flattenHierarchyCache = CacheBuilder.newBuilder().weakKeys()
            .build(new CacheLoader<Class<?>, Set<Class<?>>>() {
                @Override
                @SuppressWarnings("unchecked")
                public Set<Class<?>> load(Class<?> key) {
                    return (Set<Class<?>>) TypeToken.of(key).getTypes().rawTypes();
                }
            });

    SubscriberRegistry(EventBus bus) {
        this.bus = bus;
    }

    @SuppressWarnings("unchecked")
    Iterator<Subscriber> getSubscribers(Object event) {
        Set<Class<?>> eventTypes = flattenHierarchyCache.getUnchecked(event.getClass());
        List<Iterator<Subscriber>> subscriberIterators = Lists.newArrayListWithCapacity(eventTypes.size());
        for (Class eventType : eventTypes) {
            Set<Subscriber> eventSubscribers = subscribers.get(eventType);
            if (eventSubscribers != null) {
                subscriberIterators.add(eventSubscribers.iterator());
            }
        }
        return Iterators.concat(subscriberIterators.iterator());
    }

    void register(Object listener) {
        Map<Class<?>, Set<Subscriber>> listenerMethods = findAllSubscribers(listener);
        for (Map.Entry<Class<?>, Set<Subscriber>> entry : listenerMethods.entrySet()) {
            Class<?> eventType = entry.getKey();
            Set<Subscriber> eventMethodsInListener = entry.getValue();
            Set<Subscriber> eventSubscribers = subscribers.get(eventType);
            if (eventSubscribers == null) {
                Set<Subscriber> newSet = new CopyOnWriteArraySet<>();
                eventSubscribers = MoreObjects.firstNonNull(subscribers.putIfAbsent(eventType, newSet), newSet);
            }
            eventSubscribers.addAll(eventMethodsInListener);
        }
    }

    void unregister(Object listener) {
        Map<Class<?>, Set<Subscriber>> listenerMethods = findAllSubscribers(listener);

        for (Map.Entry<Class<?>, Set<Subscriber>> entry : listenerMethods.entrySet()) {
            Class<?> eventType = entry.getKey();
            Set<Subscriber> listenerMethodsForType = entry.getValue();
            Set<Subscriber> currentSubscribers = subscribers.get(eventType);
            if (currentSubscribers == null || !currentSubscribers.removeAll(listenerMethodsForType)) {
                // if removeAll returns true, all we really know is that at least one subscriber was
                // removed... however, barring something very strange we can assume that if at least one
                // subscriber was removed, all subscribers on listener for that event type were... after
                // all, the definition of subscribers on a particular class is totally static
                throw new IllegalArgumentException(
                        "missing event subscriber for an annotated method. Is " + listener + " registered?");
            }
            // don't try to remove the set if it's empty; that can't be done safely without a lock
            // anyway, if the set is empty it'll just be wrapping an array of length 0
        }
    }

    private Map<Class<?>, Set<Subscriber>> findAllSubscribers(Object listener) {
        Map<Class<?>, Set<Subscriber>> allSubscribers = Maps.newHashMap();
        List<Method> annotatedMethods = findAnnotatedMethods(listener);
        for (Method method : annotatedMethods) {
            Class<?> eventType = method.getParameterTypes()[0];
            Subscribe subscribe = method.getAnnotation(Subscribe.class);
            Subscriber subscriber = new Subscriber(bus, method, listener, subscribe.order(), subscribe.allowConcurrency(), subscribe.async(), bus.getExecutor());
            if (allSubscribers.containsKey(eventType)) {
                allSubscribers.get(eventType).add(subscriber);
            } else {
                allSubscribers.put(eventType, Sets.newHashSet(subscriber));
            }
        }
        return allSubscribers;
    }

    private List<Method> findAnnotatedMethods(Object object) {
        return subscriberMethodsCache.getUnchecked(object.getClass());
    }

    private List<Method> findAllMethodAnnotatedSubscribeNotCached(Class<?> clazz) {
        TypeToken<?> typeToken = TypeToken.of(clazz);
        Set<? extends Class<?>> supertypes = typeToken.getTypes().rawTypes();
        Set<MethodIdentifier> identifiers = Sets.newHashSet();
        for (Class<?> clzz : supertypes) {
            List<Method> methods = classMethodsCache.getUnchecked(clzz);
            methods.stream().filter(method -> method.isAnnotationPresent(Subscribe.class) && !method.isSynthetic())
                    .map(MethodIdentifier::new).filter(methodIdentifier -> !identifiers.contains(methodIdentifier))
                    .forEach(methodIdentifier -> {
                        if (methodIdentifier.source.getParameterCount() != 1) {
                            throw new IllegalArgumentException("Subscribe方法只能有一个参数，该参数为要处理的事件类型");
                        }
                        identifiers.add(methodIdentifier);
                    });
        }
        return ImmutableList.copyOf(identifiers.stream().map(MethodIdentifier::source).collect(Collectors.toList()));
    }

    private static final class MethodIdentifier {
        private final String name;
        private final List<Class<?>> parameterTypes;
        private final Method source;

        MethodIdentifier(Method method) {
            this.source = method;
            this.name = method.getName();
            this.parameterTypes = Arrays.asList(method.getParameterTypes());
        }

        private Method source() {
            return this.source;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name, parameterTypes);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof SubscriberRegistry.MethodIdentifier) {
                SubscriberRegistry.MethodIdentifier ident = (SubscriberRegistry.MethodIdentifier) o;
                return name.equals(ident.name) && parameterTypes.equals(ident.parameterTypes);
            }
            return false;
        }
    }

}
