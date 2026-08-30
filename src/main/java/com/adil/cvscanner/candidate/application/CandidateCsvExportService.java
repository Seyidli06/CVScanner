package com.adil.cvscanner.candidate.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CandidateCsvExportService {

    private final CandidateExportQueryService
            candidateExportQueryService;

    public CandidateCsvExportService(
            CandidateExportQueryService candidateExportQueryService
    ) {

        this.candidateExportQueryService =
                candidateExportQueryService;
    }

    





    public void validateRequest(
            CandidateSearchCriteria criteria,
            String sortBy,
            String direction
    ) {

        candidateExportQueryService
                .validateRequest(
                        criteria,
                        sortBy,
                        direction
                );
    }

    





    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public void writeCsv(
            OutputStream outputStream,
            CandidateSearchCriteria criteria,
            String sortBy,
            String direction
    ) throws IOException {

        validateRequest(
                criteria,
                sortBy,
                direction
        );

        BufferedWriter writer =
                new BufferedWriter(
                        new OutputStreamWriter(
                                outputStream,
                                StandardCharsets.UTF_8
                        )
                );

        


        writer.write(
                '\uFEFF'
        );

        writeHeader(
                writer
        );

        int offset =
                0;

        while (
                true
        ) {

            List<CandidateExportRow> rows =
                    candidateExportQueryService
                            .readBatch(
                                    criteria,
                                    sortBy,
                                    direction,
                                    offset,
                                    CandidateExportQueryService
                                            .EXPORT_BATCH_SIZE
                            );

            if (
                    rows.isEmpty()
            ) {

                break;
            }

            for (
                    CandidateExportRow row : rows
            ) {

                writeRow(
                        writer,
                        row
                );
            }

            writer.flush();

            offset +=
                    rows.size();

            if (
                    rows.size()
                            <
                            CandidateExportQueryService
                                    .EXPORT_BATCH_SIZE
            ) {

                break;
            }

            candidateExportQueryService
                    .clearPersistenceContext();

            if (
                    offset
                            >
                            Integer.MAX_VALUE
                                    -
                                    CandidateExportQueryService
                                            .EXPORT_BATCH_SIZE
            ) {

                throw new IllegalStateException(
                        "Candidate export exceeds supported result size"
                );
            }
        }

        writer.flush();
    }

    





    private void writeHeader(
            BufferedWriter writer
    ) throws IOException {

        writeCsvCells(
                writer,
                List.of(
                        "candidateId",
                        "uploadId",
                        "fullName",
                        "yearsOfExperience",
                        "preferredLocation",
                        "preferredJobType",
                        "skills",
                        "sourceFilename"
                )
        );
    }

    





    private void writeRow(
            BufferedWriter writer,
            CandidateExportRow row
    ) throws IOException {

        String skills =
                row.skills()
                        .stream()
                        .collect(
                                Collectors.joining(
                                        " | "
                                )
                        );

        writeCsvCells(
                writer,
                List.of(
                        nullable(
                                row.candidateId()
                        ),
                        nullable(
                                row.uploadId()
                        ),
                        nullable(
                                row.fullName()
                        ),
                        nullable(
                                row.yearsOfExperience()
                        ),
                        nullable(
                                row.preferredLocation()
                        ),
                        nullable(
                                row.preferredJobType()
                        ),
                        skills,
                        nullable(
                                row.sourceFilename()
                        )
                )
        );
    }

    





    private void writeCsvCells(
            BufferedWriter writer,
            List<String> cells
    ) throws IOException {

        for (
                int index = 0;
                index < cells.size();
                index++
        ) {

            if (
                    index > 0
            ) {

                writer.write(
                        ','
                );
            }

            writer.write(
                    escapeCsvCell(
                            cells.get(
                                    index
                            )
                    )
            );
        }

        writer.newLine();
    }

    private String escapeCsvCell(
            String value
    ) {

        String safeValue =
                protectSpreadsheetFormula(
                        value == null
                                ? ""
                                : value
                );

        return "\""
                +
                safeValue.replace(
                        "\"",
                        "\"\""
                )
                +
                "\"";
    }

    


    private String protectSpreadsheetFormula(
            String value
    ) {

        if (
                value.isEmpty()
        ) {

            return value;
        }

        String leadingTrimmed =
                value.stripLeading();

        if (
                leadingTrimmed.isEmpty()
        ) {

            return value;
        }

        char firstCharacter =
                leadingTrimmed.charAt(
                        0
                );

        if (
                firstCharacter == '='
                        ||
                        firstCharacter == '+'
                        ||
                        firstCharacter == '-'
                        ||
                        firstCharacter == '@'
        ) {

            return "'"
                    + value;
        }

        return value;
    }

    private String nullable(
            Object value
    ) {

        return value == null
                ? ""
                : value.toString();
    }
}