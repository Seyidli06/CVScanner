package com.adil.cvscanner.processing.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvFileItemReaderTest {

    @Test
    void shouldContinueFromSavedIndexAfterRestart() {

        List<Path> files =
                List.of(
                        Path.of("cv1.pdf"),
                        Path.of("cv2.docx"),
                        Path.of("cv3.pdf")
                );

        ExecutionContext executionContext =
                new ExecutionContext();

        /*
         * First execution
         */
        CvFileItemReader firstReader =
                new CvFileItemReader(
                        files
                );

        firstReader.open(
                executionContext
        );

        assertThat(
                firstReader.read()
        ).isEqualTo(
                Path.of("cv1.pdf")
        );

        assertThat(
                firstReader.read()
        ).isEqualTo(
                Path.of("cv2.docx")
        );

        /*
         * Spring Batch normalda bunu
         * checkpoint zamanı edir.
         */
        firstReader.update(
                executionContext
        );

        firstReader.close();

        /*
         * Simulyasiya edirik:
         *
         * application/job restart oldu və
         * yeni reader instance yaradıldı.
         */
        CvFileItemReader restartedReader =
                new CvFileItemReader(
                        files
                );

        restartedReader.open(
                executionContext
        );

        /*
         * cv1 və cv2 yenidən oxunmamalıdır.
         */
        assertThat(
                restartedReader.read()
        ).isEqualTo(
                Path.of("cv3.pdf")
        );

        /*
         * Input bitib.
         */
        assertThat(
                restartedReader.read()
        ).isNull();

        restartedReader.close();
    }
}