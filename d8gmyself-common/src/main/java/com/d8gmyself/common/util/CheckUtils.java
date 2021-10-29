package com.d8gmyself.common.util;

import com.d8gmyself.common.Result;
import com.d8gmyself.common.error.CodeMessageError;
import com.d8gmyself.common.error.CodeMessageErrorException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.PropertyKey;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author yousheng.zd
 */
public final class CheckUtils {

    private CheckUtils() {

    }

    public static void check(boolean test, CodeMessageError error) {
        if (test) {
            return;
        }
        error = Optional.ofNullable(error).orElse(CodeMessageError.systemError());
        throw new CodeMessageErrorException(error);
    }

    public static void check(Supplier<Boolean> test, CodeMessageError error) {
        Boolean result = test.get();
        check(result, error);
    }

    public static void check(Supplier<Boolean> test, @PropertyKey(resourceBundle = CodeMessageError.ERRORS_BUNDLE) String errorCode, Object... args) {
        Boolean result = test.get();
        check(result, CodeMessageError.of(errorCode, args));
    }

    public static void check(boolean test, @PropertyKey(resourceBundle = CodeMessageError.ERRORS_BUNDLE) String errorCode, Object... args) {
        check(test, CodeMessageError.of(errorCode, args));
    }

    public static void checkNotNull(Object obj, CodeMessageError mallError) {
        check(obj != null, mallError);
    }

    public static void checkNotNull(Object obj, @PropertyKey(resourceBundle = CodeMessageError.ERRORS_BUNDLE) String errorCode, Object... args) {
        check(obj != null, CodeMessageError.of(errorCode, args));
    }

    public static void checkNotBlank(String str, CodeMessageError CodeMessageError) {
        check(StringUtils.isNotBlank(str), CodeMessageError);
    }

    public static void checkNotBlank(String str, @PropertyKey(resourceBundle = CodeMessageError.ERRORS_BUNDLE) String errorCode, Object... args) {
        check(StringUtils.isNotBlank(str), CodeMessageError.of(errorCode, args));
    }

    public static void checkEquals(Object a, Object b, CodeMessageError CodeMessageError) {
        check(Objects.equals(a, b), CodeMessageError);
    }

    public static void checkEquals(Object a, Object b, @PropertyKey(resourceBundle = CodeMessageError.ERRORS_BUNDLE) String errorCode, Object... args) {
        check(Objects.equals(a, b), CodeMessageError.of(errorCode, args));
    }

    public static void checkResult(Result<?> result) {
        check(result.isSuccess(), CodeMessageError.of(result.getErrorCode(), result.getMessage()));
    }

}
