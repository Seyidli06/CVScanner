package com.adil.cvscanner.processing.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record ProcessingFailurePageResponse(

        List<ProcessingFailureResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages,

        boolean first,

        boolean last

) {

    public static ProcessingFailurePageResponse from(
            Page<com.adil.cvscanner.processing.domain.ProcessingFailure>
                    source
    ) {

        List<ProcessingFailureResponse> content =
                source
                        .getContent()
                        .stream()
                        .map(
                                ProcessingFailureResponse::from
                        )
                        .toList();

        return new ProcessingFailurePageResponse(
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
