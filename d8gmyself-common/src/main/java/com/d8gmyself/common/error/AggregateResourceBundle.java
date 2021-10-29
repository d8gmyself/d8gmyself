package com.d8gmyself.common.error;

import com.google.common.collect.Sets;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * UTF-8解码资源文件 & 支持同名文件合并
 */
public class AggregateResourceBundle extends ResourceBundle {

    protected static final Control CONTROL = new AggregateResourceBundleControl();
    private final Properties properties;

    protected AggregateResourceBundle(Properties properties) {
        this.properties = properties;
    }

    @Override
    protected Object handleGetObject(@Nonnull String key) {
        return properties.get(key);
    }

    @Override
    public Enumeration<String> getKeys() {
        Set<String> keySet = Sets.newHashSet();
        keySet.addAll(properties.stringPropertyNames());
        if (parent != null) {
            keySet.addAll(Collections.list(parent.getKeys()));
        }
        return Collections.enumeration(keySet);
    }

    private static class AggregateResourceBundleControl extends Control {

        @Override
        public ResourceBundle newBundle(
                String baseName,
                Locale locale,
                String format,
                ClassLoader loader,
                boolean reload)
                throws IllegalAccessException, InstantiationException, IOException {
            //只对properties格式的文件做合并
            if (!"java.properties".equals(format)) {
                return super.newBundle(baseName, locale, format, loader, reload);
            }
            String resourceName = super.toResourceName(super.toBundleName(baseName, locale), "properties");
            Properties properties = load(resourceName, loader);
            return properties.size() == 0 ? null : new AggregateResourceBundle(properties);
        }

        private Properties load(String resourceName, ClassLoader loader) throws IOException {
            Properties aggregatedProperties = new Properties();
            Enumeration<URL> urls = run(() -> {
                try {
                    return loader.getResources(resourceName);
                } catch (IOException e) {
                    return Collections.enumeration(Collections.emptyList());
                }
            });
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                Properties properties = new Properties();
                try (
                        Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)
                ) {
                    properties.load(reader);
                    aggregatedProperties.putAll(properties);
                }
            }
            return aggregatedProperties;
        }

        private static <T> T run(PrivilegedAction<T> action) {
            return System.getSecurityManager() != null ? AccessController.doPrivileged(action) : action.run();
        }
    }

}
