package com.d8gmyself.concurrency;

import com.google.common.annotations.Beta;

import java.util.Map;

/**
 * @author yousheng.zd
 */
@Beta
public interface ExecuteContextSPI {

    default Map<String, Object> getContext() {
        return null;
    }

    default void setContext(Map<String, Object> context) {

    }

    default void clearContext(Map<String, Object> context) {

    }

}
