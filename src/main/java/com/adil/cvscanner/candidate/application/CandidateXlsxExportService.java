package com.adil.cvscanner.candidate.application;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CandidateXlsxExportService {

    




    private static final int ROW_ACCESS_WINDOW_SIZE =
            100;

    private static final String SHEET_NAME =
            "Candidates";

    private final CandidateExportQueryService
            candidateExportQueryService;

    public CandidateXlsxExportService(
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
    public void writeXlsx(
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

        SXSSFWorkbook workbook =
                new SXSSFWorkbook(
                        ROW_ACCESS_WINDOW_SIZE
                );

        





        workbook.setCompressTempFiles(
                true
        );

        try {

            Sheet sheet =
                    workbook.createSheet(
                            SHEET_NAME
                    );

            CellStyle headerStyle =
                    createHeaderStyle(
                            workbook
                    );

            configureSheet(
                    sheet
            );

            int excelRowIndex =
                    0;

            createHeaderRow(
                    sheet,
                    excelRowIndex++,
                    headerStyle
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
                        CandidateExportRow candidate : rows
                ) {

                    createCandidateRow(
                            sheet,
                            excelRowIndex++,
                            candidate
                    );
                }

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

            



            workbook.write(
                    outputStream
            );

            outputStream.flush();

        } finally {

            




            workbook.close();

            








            workbook.dispose();
        }
    }

    





    private void configureSheet(
            Sheet sheet
    ) {

        


        sheet.createFreezePane(
                0,
                1
        );

        




        sheet.setAutoFilter(
                new CellRangeAddress(
                        0,
                        0,
                        0,
                        7
                )
        );

        






        sheet.setColumnWidth(
                0,
                38 * 256
        );

        sheet.setColumnWidth(
                1,
                38 * 256
        );

        sheet.setColumnWidth(
                2,
                30 * 256
        );

        sheet.setColumnWidth(
                3,
                20 * 256
        );

        sheet.setColumnWidth(
                4,
                25 * 256
        );

        sheet.setColumnWidth(
                5,
                22 * 256
        );

        sheet.setColumnWidth(
                6,
                45 * 256
        );

        sheet.setColumnWidth(
                7,
                35 * 256
        );
    }

    





    private CellStyle createHeaderStyle(
            SXSSFWorkbook workbook
    ) {

        Font font =
                workbook.createFont();

        font.setBold(
                true
        );

        CellStyle style =
                workbook.createCellStyle();

        style.setFont(
                font
        );

        return style;
    }

    





    private void createHeaderRow(
            Sheet sheet,
            int rowIndex,
            CellStyle headerStyle
    ) {

        Row row =
                sheet.createRow(
                        rowIndex
                );

        String[] headers = {
                "candidateId",
                "uploadId",
                "fullName",
                "yearsOfExperience",
                "preferredLocation",
                "preferredJobType",
                "skills",
                "sourceFilename"
        };

        for (
                int columnIndex = 0;
                columnIndex < headers.length;
                columnIndex++
        ) {

            Cell cell =
                    row.createCell(
                            columnIndex
                    );

            cell.setCellValue(
                    headers[columnIndex]
            );

            cell.setCellStyle(
                    headerStyle
            );
        }
    }

    





    private void createCandidateRow(
            Sheet sheet,
            int rowIndex,
            CandidateExportRow candidate
    ) {

        Row row =
                sheet.createRow(
                        rowIndex
                );

        writeStringCell(
                row,
                0,
                nullable(
                        candidate.candidateId()
                )
        );

        writeStringCell(
                row,
                1,
                nullable(
                        candidate.uploadId()
                )
        );

        writeStringCell(
                row,
                2,
                candidate.fullName()
        );

        




        if (
                candidate.yearsOfExperience()
                        != null
        ) {

            Cell experienceCell =
                    row.createCell(
                            3
                    );

            experienceCell.setCellValue(
                    candidate
                            .yearsOfExperience()
            );
        }

        writeStringCell(
                row,
                4,
                candidate.preferredLocation()
        );

        writeStringCell(
                row,
                5,
                candidate.preferredJobType()
                        == null
                        ? ""
                        : candidate
                        .preferredJobType()
                        .name()
        );

        String skills =
                candidate
                        .skills()
                        .stream()
                        .collect(
                                Collectors.joining(
                                        " | "
                                )
                        );

        writeStringCell(
                row,
                6,
                skills
        );

        writeStringCell(
                row,
                7,
                candidate.sourceFilename()
        );
    }

    























    private void writeStringCell(
            Row row,
            int columnIndex,
            String value
    ) {

        Cell cell =
                row.createCell(
                        columnIndex
                );

        cell.setCellValue(
                value == null
                        ? ""
                        : value
        );
    }

    private String nullable(
            Object value
    ) {

        return value == null
                ? ""
                : value.toString();
    }
}