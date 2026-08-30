package com.adil.cvscanner.candidate.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(

        List<T> content,

        int page,

        int size,

        long totalElements,

        int totalPages,

        boolean first,

        boolean last

) {

    public static <S, T> PageResponse<T> from(
            Page<S> source,
            Function<S, T> mapper
    ) {

        List<T> content =
                source
                        .getContent()
                        .stream()
                        .map(
                                mapper
                        )
                        .toList();

        return new PageResponse<>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast()
        );
    }
}
