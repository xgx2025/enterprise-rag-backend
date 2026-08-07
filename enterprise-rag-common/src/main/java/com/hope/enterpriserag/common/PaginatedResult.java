package com.hope.enterpriserag.common;

import java.util.List;

/**
 * 通用分页结果。
 *
 * @param items    当前页数据
 * @param total    符合条件的总记录数
 * @param page     当前页码，从 1 开始
 * @param pageSize 每页记录数
 * @param <T>      列表元素类型
 */
public record PaginatedResult<T>(List<T> items, long total, long page, long pageSize) {
}
