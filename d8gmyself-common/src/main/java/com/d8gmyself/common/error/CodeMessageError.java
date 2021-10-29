package com.d8gmyself.common.error;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.PropertyKey;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * code-message错误信息
 */
public interface CodeMessageError extends Serializable {

    String ERRORS_BUNDLE = "i18n.errors";
    String SYSTEM_ERROR_CODE = System.getProperty("E_APP_NAME", "X") + "-0-999-00-99-001";
    String RPC_ERROR_CODE = System.getProperty("E_APP_NAME", "X") + "-0-999-00-99-002";
    String VIOLATION_ERROR_CODE = System.getProperty("E_APP_NAME", "X") + "-0-999-00-99-003";
    CodeMessageError SYSTEM_ERROR = CodeMessageError.of(SYSTEM_ERROR_CODE);

    static CodeMessageError of(Integer code, String message, Object... params) {
        return of(String.valueOf(code), message, params);
    }

    static CodeMessageError of(String code, String message, Object... params) {
        if (ArrayUtils.isNotEmpty(params) && StringUtils.contains(message, "{0")) {
            message = MessageFormat.format(message, params);
        }
        return new DefaultCodeMessageError(code, message);
    }

    static CodeMessageError of(@PropertyKey(resourceBundle = ERRORS_BUNDLE) String code, Object... params) {
        return CodeMessageError.of(code, Locale.SIMPLIFIED_CHINESE, params);
    }

    static CodeMessageError of(@PropertyKey(resourceBundle = ERRORS_BUNDLE) String code, Locale locale, Object... params) {
        try {
            ResourceBundle resourceBundle = ResourceBundle.getBundle(ERRORS_BUNDLE, locale, AggregateResourceBundle.CONTROL);
            String message = resourceBundle.getString(code);
            if (ArrayUtils.isNotEmpty(params) && StringUtils.contains(message, "{0")) {
                message = MessageFormat.format(message, params);
            }
            return new DefaultCodeMessageError(code, message);
        } catch (MissingResourceException missingResourceException) {
            if (StringUtils.equals(SYSTEM_ERROR_CODE, code)) {
                return new DefaultCodeMessageError(code, "系统异常，请稍后再试");
            }
            if (StringUtils.equals(RPC_ERROR_CODE, code)) {
                return new DefaultCodeMessageError(code, Optional.ofNullable(params).filter(ArrayUtils::isNotEmpty).map(x -> x[0]).map(Object::toString).orElse("") + "RPC调用出错，请稍后再试");
            }
            return new DefaultCodeMessageError(code, "未知错误");
        }
    }

    static CodeMessageError systemError() {
        return SYSTEM_ERROR;
    }

    static CodeMessageError systemError(String message) {
        return CodeMessageError.of(SYSTEM_ERROR_CODE, message, (Object[]) null);
    }

    static CodeMessageError rpcError(String action) {
        return CodeMessageError.of(RPC_ERROR_CODE, new Object[]{action});
    }

    static CodeMessageError violationError(String message) {
        return CodeMessageError.of(VIOLATION_ERROR_CODE, message);
    }

    String getCode();

    String getMessage();

    class DefaultCodeMessageError implements CodeMessageError {
        private static final long serialVersionUID = -971463849897568430L;
        private final String code;
        private final String message;

        public DefaultCodeMessageError(String code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

}
