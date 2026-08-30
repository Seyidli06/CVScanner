package com.adil.cvscanner.processing.batch;

import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

import java.nio.file.Path;
import java.util.List;

public class CvFileItemReader
        implements ItemStreamReader<Path> {

    private static final String CURRENT_INDEX_KEY =
            "cvFileItemReader.currentIndex";

    private final List<Path> files;

    private int currentIndex;

    public CvFileItemReader(
            List<Path> files
    ) {

        this.files =
                List.copyOf(files);

        this.currentIndex = 0;
    }

    @Override
    public Path read() {

        if (currentIndex >= files.size()) {
            return null;
        }

        Path nextFile =
                files.get(currentIndex);

        currentIndex++;

        return nextFile;
    }

    @Override
    public void open(
            ExecutionContext executionContext
    ) {

        currentIndex =
                executionContext.getInt(
                        CURRENT_INDEX_KEY,
                        0
                );

        if (
                currentIndex < 0
                        || currentIndex > files.size()
        ) {
            throw new IllegalStateException(
                    "Invalid CV reader restart index: "
                            + currentIndex
            );
        }
    }

    @Override
    public void update(
            ExecutionContext executionContext
    ) {

        executionContext.putInt(
                CURRENT_INDEX_KEY,
                currentIndex
        );
    }

    @Override
    public void close() {
        
    }
}