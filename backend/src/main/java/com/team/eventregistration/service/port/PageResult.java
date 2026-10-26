package com.team.eventregistration.service.port;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> content, long totalElements, int number, int size) {
    public PageResult { content = List.copyOf(content); }
    public int getTotalPages() { return (int) ((totalElements + size - 1) / size); }
    public boolean isFirst() { return number == 0; }
    public boolean isLast() { return number + 1 >= getTotalPages(); }
    public boolean isEmpty() { return content.isEmpty(); }
    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(content.stream().map(mapper).toList(), totalElements, number, size);
    }
}
