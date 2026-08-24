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

    /*
     * SXSSF yalnız son 100 row-u memory-də saxlayır.
     *
     * Köhnə rows temporary XML file-a flush olunur.
     */
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

    /*
     * ============================================================
     * PRE-FLIGHT VALIDATION
     * ============================================================
     */

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

    /*
     * ============================================================
     * XLSX EXPORT
     * ============================================================
     */

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

        /*
         * SXSSF temp XML-ləri diskdə saxlayır.
         *
         * Compress etməklə temporary disk istifadəsini
         * azaldırıq.
         */
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

                /*
                 * Hibernate əvvəlki batch entity-lərini
                 * memory-də saxlamasın.
                 */
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

            /*
             * HTTP OutputStream-ə real XLSX ZIP
             * document yazılır.
             */
            workbook.write(
                    outputStream
            );

            outputStream.flush();

        } finally {

            /*
             * close:
             *
             * workbook resources.
             */
            workbook.close();

            /*
             * dispose:
             *
             * SXSSF temporary files.
             *
             * Bu vacibdir.
             * Əks halda export-lardan sonra temp directory
             * böyüyə bilər.
             */
            workbook.dispose();
        }
    }

    /*
     * ============================================================
     * SHEET CONFIGURATION
     * ============================================================
     */

    private void configureSheet(
            Sheet sheet
    ) {

        /*
         * Header scroll zamanı ekranda qalsın.
         */
        sheet.createFreezePane(
                0,
                1
        );

        /*
         * AutoFilter:
         *
         * A1:H1
         */
        sheet.setAutoFilter(
                new CellRangeAddress(
                        0,
                        0,
                        0,
                        7
                )
        );

        /*
         * autosizeColumn() SXSSF ilə böyük dataset-də
         * lazımsız memory/CPU xərci yarada bilər.
         *
         * Ona görə fixed sensible widths.
         */

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

    /*
     * ============================================================
     * HEADER STYLE
     * ============================================================
     */

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

    /*
     * ============================================================
     * HEADER
     * ============================================================
     */

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

    /*
     * ============================================================
     * CANDIDATE ROW
     * ============================================================
     */

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

        /*
         * Experience numeric cell kimi yazılır.
         *
         * Excel sorting/filtering daha düzgün işləyir.
         */
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

    /*
     * ============================================================
     * STRING CELL
     * ============================================================
     *
     * ÇOX VACİB:
     *
     * Burada setCellFormula istifadə etmirik.
     *
     * Hətta value:
     *
     * =2+2
     *
     * olsa belə:
     *
     * setCellValue(String)
     *
     * onu STRING cell kimi yazır.
     *
     * Buna görə CSV-dən fərqli olaraq
     * leading apostrophe ilə data-nı dəyişmək
     * lazım deyil.
     */

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