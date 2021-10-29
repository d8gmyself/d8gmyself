package com.d8gmyself.common.util;

import com.d8gmyself.common.PageResult;
import com.d8gmyself.common.Result;
import com.d8gmyself.common.error.CodeMessageError;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author yousheng.zd
 */
public final class ResultUtils {

    private ResultUtils() {
    }

    public static Result<Void> successVoid() {
        return newResult(true, null, null, null);
    }

    public static Result<Void> successVoid(String message) {
        return newResult(true, null, null, message);
    }

    public static Result<Void> failVoid(String errorCode, String errorMessage) {
        return newResult(false, null, errorCode, errorMessage);
    }

    public static Result<Void> failVoid(CodeMessageError error) {
        error = Optional.ofNullable(error).orElse(CodeMessageError.systemError());
        return newResult(false, null, error.getCode(), error.getMessage());
    }

    public static <T> Result<T> successResult(T data) {
        return newResult(true, data, null, null);
    }

    public static <T> Result<T> successResult(T data, String message) {
        return newResult(true, data, null, message);
    }

    public static <T> Result<T> failResult(String code, String message) {
        return newResult(false, null, code, message);
    }

    public static <T> Result<T> failResult(CodeMessageError error) {
        error = Optional.ofNullable(error).orElse(CodeMessageError.systemError());
        return newResult(false, null, error.getCode(), error.getMessage());
    }

    public static <T> Result<T> failResult(T data, String code, String message) {
        return newResult(false, data, code, message);
    }

    public static <T> Result<T> failResult(T data, CodeMessageError error) {
        error = Optional.ofNullable(error).orElse(CodeMessageError.systemError());
        return newResult(false, data, error.getCode(), error.getMessage());
    }

    public static <T> Result<T> convert(Result<?> result) {
        if (result == null) {
            CodeMessageError systemError = CodeMessageError.systemError();
            return newResult(false, null, systemError.getCode(), systemError.getMessage());
        }
        if (result.isSuccess()) {
            return newResult(true, null, null, null);
        }
        return newResult(false, null, result.getErrorCode(), result.getMessage());
    }

    public static <T, E> T accept(Result<E> result, Function<E, T> resultConvert, String action) {
        CheckUtils.checkNotNull(result, CodeMessageError.rpcError(action));
        CheckUtils.checkResult(result);
        if (resultConvert == null) {
            return null;
        }
        return resultConvert.apply(result.getResult());
    }

    public static void acceptVoid(Result<?> result, String action) {
        accept(result, null, action);
    }

    public static <T> T accept(Result<T> result, String action) {
        return accept(result, Function.identity(), action);
    }

    public static <T, E> T accept(Supplier<Result<E>> supplier, Function<E, T> resultConvert, String action) {
        Result<E> result = supplier.get();
        return accept(result, resultConvert, action);
    }

    public static void acceptVoid(Supplier<Result<?>> supplier, String action) {
        Result<?> result = supplier.get();
        accept(result, null, action);
    }

    public static <T> T accept(Supplier<Result<T>> supplier, String action) {
        Result<T> result = supplier.get();
        return accept(result, Function.identity(), action);
    }

    /**
     * 避免栈过深，全部使用该方法
     */
    private static <T> Result<T> newResult(boolean success, T data, String code, String message) {
        Result<T> result = new Result<>();
        result.setSuccess(success);
        result.setResult(data);
        result.setErrorCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> PageResult<T> emptyPageResult() {
        return newPageResult(true, Collections.emptyList(), 1, 10, 0L, null, null);
    }

    public static <T> PageResult<T> successPageResult(List<T> datas, int page, int size, long totalCount) {
        return newPageResult(true, datas, page, size, totalCount, null, null);
    }

    public static <T> PageResult<T> memoryPageResult(List<T> datas, int page, int size) {
        return memoryPageResult(datas, page, size, null);
    }

    public static <T> PageResult<T> memoryPageResult(List<T> datas, int page, int size, Comparator<T> comparator) {
        if (CollectionUtils.isEmpty(datas)) {
            return newPageResult(true, Collections.emptyList(), 1, 10, 0L, null, null);
        }
        Preconditions.checkArgument(page >= 1, "pageNum必须大于0");
        Preconditions.checkArgument(size >= 1, "pageSize必须大于0");
        if (comparator != null) {
            datas.sort(comparator);
        }
        List<List<T>> partition = Lists.partition(datas, size);
        if (page > partition.size()) {
            return newPageResult(true, Collections.emptyList(), page, size, (long) datas.size(), null, null);
        }
        List<T> pageDatas = partition.get(page - 1);
        return newPageResult(true, pageDatas, page, size, (long) datas.size(), null, null);
    }

    public static <T> PageResult<T> failPageResult(String code, String message) {
        return newPageResult(false, Collections.emptyList(), 1, 10, 0L, code, message);
    }

    public static <T> PageResult<T> failPageResult(CodeMessageError error) {
        error = Optional.ofNullable(error).orElse(CodeMessageError.systemError());
        return newPageResult(false, Collections.emptyList(), 1, 10, 0L, error.getCode(), error.getMessage());
    }

    /**
     * 避免栈过深，尽量使用该方法
     */
    private static <T> PageResult<T> newPageResult(boolean success, List<T> data, Integer page, Integer size, Long totalCount, String code, String message) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setSuccess(success);
        pageResult.setResult(data);
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setTotal(totalCount);
        pageResult.setErrorCode(code);
        pageResult.setMessage(message);
        return pageResult;
    }

}
