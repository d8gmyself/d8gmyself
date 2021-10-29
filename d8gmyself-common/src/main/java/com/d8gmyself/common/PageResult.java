package com.d8gmyself.common;

import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * 分页结果
 */
public class PageResult<T> extends Result<List<T>> {

    private static final long serialVersionUID = -3599960956275581793L;
    private Long total;
    private Integer page;
    private Integer size;
    private Integer totalPage;
    private Long start;

    public Integer calculateTotalPage() {
        if (total == null || total == 0 || size == null) {
            this.setTotalPage(0);
            return 0;
        }
        int totalPage = (int) ((total - 1) / size + 1);
        this.setTotalPage(totalPage);
        return totalPage;
    }

    public Long getStart() {
        if (start != null || !ObjectUtils.allNotNull(page, size)) {
            return start;
        }
        return (long) (page - 1) * size;
    }

    public void setStart(Long start) {
        this.start = start;
    }

    public Integer getTotalPage() {
        return this.totalPage;
    }

    public void setTotalPage(Integer totalPage) {
        this.totalPage = totalPage;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

}
