package com.d8gmyself.common.error;

import org.apache.commons.lang3.ClassUtils;

import javax.validation.ConstraintViolationException;
import java.util.Optional;

/**
 * 简单的ErrorCodeException
 */
public class CodeMessageErrorException extends RuntimeException implements CodeMessageError {
    private static final long serialVersionUID = 3974181365985991540L;

    private final CodeMessageError error;

    public CodeMessageErrorException(String code, String message) {
        this(CodeMessageError.of(code, message, (Object[]) null));
    }

    public CodeMessageErrorException(CodeMessageError error) {
        super(error.getMessage());
        this.error = error;
    }

    public CodeMessageErrorException(Throwable cause, CodeMessageError error) {
        super(error.getMessage(), cause);
        this.error = error;
    }

    public CodeMessageErrorException(Throwable cause, boolean enableSuppression, boolean writableStackTrace, CodeMessageError error) {
        super(error.getMessage(), cause, enableSuppression, writableStackTrace);
        this.error = error;
    }

    @Override
    public String getCode() {
        return Optional.ofNullable(error).map(CodeMessageError::getCode).orElse(null);
    }

    public CodeMessageError getError() {
        return error;
    }

    public static CodeMessageErrorException wrap(Throwable cause) {
        return wrap(cause, false);
    }

    public static CodeMessageErrorException wrap(Throwable cause, boolean ignoreMallErrorStack) {
        if (cause instanceof CodeMessageErrorException) {
            if (ignoreMallErrorStack) {
                return (CodeMessageErrorException) cause;
            }
            return new CodeMessageErrorException(cause, (CodeMessageErrorException) cause);
        }
        try {
            Class<?> violationExpClass = Class.forName("javax.validation.ConstraintViolationException");
            if (ClassUtils.isAssignable(cause.getClass(), violationExpClass)) {
                ConstraintViolationException constraintViolationException = (ConstraintViolationException) cause;
                return new CodeMessageErrorException(cause, CodeMessageError.violationError(constraintViolationException.getMessage()));
            }
        } catch (ClassNotFoundException ignore) {
        }
        return new CodeMessageErrorException(cause, CodeMessageError.systemError());
    }

}
