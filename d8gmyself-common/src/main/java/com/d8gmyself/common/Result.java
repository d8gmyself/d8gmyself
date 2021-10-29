package com.d8gmyself.common;

import org.apache.commons.lang3.BooleanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 通过结果类，主要用于RPC调用
 */
public class Result<T> implements Serializable {
    private static final long serialVersionUID = -1649898147903950001L;

    private Boolean success;
    private String errorCode;
    private String message;
    private T result;
    private final Map<String, Object> ext = new HashMap<>();
    private Header header;

    public void putExt(String key, Object value) {
        this.ext.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <V> V getExt(String key) {
        return (V) this.ext.get(key);
    }

    public Header newHeader(String traceId, String rpcId) {
        this.header = new Header(traceId, rpcId, new Date());
        return this.header;
    }

    public boolean isSuccess() {
        return BooleanUtils.isTrue(this.success);
    }

    public boolean isFail() {
        return !isSuccess();
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public Map<String, Object> getExt() {
        return ext;
    }

    public Header getHeader() {
        return header;
    }

    public void setHeader(Header header) {
        this.header = header;
    }

    static class Header implements Serializable {

        private static final long serialVersionUID = -4872091133904729359L;
        private final String traceId;
        private final String rpcId;
        private final Date date;

        public Header(String traceId, String rpcId, Date date) {
            this.traceId = traceId;
            this.rpcId = rpcId;
            this.date = date;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getRpcId() {
            return rpcId;
        }

        public Date getDate() {
            return date;
        }
    }

}
