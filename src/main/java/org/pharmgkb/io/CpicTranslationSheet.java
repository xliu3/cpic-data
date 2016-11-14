package org.pharmgkb.io;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class represents a single translation sheet.
 *
 * @author Ryan Whaley
 */
public class CpicTranslationSheet {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String sf_translationsSheetName = "Translations";
  private static final int sf_excelCharWidth = 256;
  private static final int sf_firstColWidth = 15;
  private static final int sf_firstDataRowIndex = 7;

  private Path m_pathToSheet;

  /**
   * public constructor
   *
   * Takes a path to a TSV translation sheet as the argument
   *
   * @param pathToSheet path to a TSV translation sheet
   */
  public CpicTranslationSheet(Path pathToSheet) {
    if (!pathToSheet.toFile().exists()) {
      throw new RuntimeException(pathToSheet + " does not exist");
    }

    if (!pathToSheet.toFile().isFile()) {
      throw new RuntimeException(pathToSheet + " is not a file");
    }

    if (!pathToSheet.toString().endsWith(".tsv")) {
      throw new RuntimeException(pathToSheet + " is not a TSV file");
    }

    m_pathToSheet = pathToSheet;
  }

  /**
   * Creates a new Excel <code>.xlsx</code> file based on the contents of the translation TSV file. THere is some
   * default formatting that is applied.
   *
   * @param writePath the path to write the output to
   */
  public void exportExcel(Path writePath) {
    String outputFile = m_pathToSheet.getFileName().toString().replaceAll("\\.tsv$", ".xlsx");
    Path outPath = writePath.resolve(outputFile);

    sf_logger.debug("Will convert {} and write to {}", m_pathToSheet.toAbsolutePath(), outPath);

    Workbook workbook = new XSSFWorkbook();

    Font headFont = workbook.createFont();
    headFont.setBold(true);

    CellStyle headStyle = workbook.createCellStyle();
    headStyle.setFont(headFont);

    Sheet sheet = workbook.createSheet(sf_translationsSheetName);
    sheet.setColumnWidth(0, makeColWidth(sf_firstColWidth));

    sheet.createFreezePane(1, sf_firstDataRowIndex);

    try (BufferedReader reader = Files.newBufferedReader(m_pathToSheet)) {
      String line;
      int rowNum = 0;
      while ((line = reader.readLine()) != null) {
        Row row = sheet.createRow(rowNum++);
        String[] fields = line.split("\\t");

        for (int i=0; i<fields.length; i++) {
          Cell cell = row.createCell(i);
          cell.setCellValue(fields[i]);
          if (rowNum < sf_firstDataRowIndex || i==0) {
            cell.setCellStyle(headStyle);
          }
        }
      }
    }
    catch (IOException ex) {
      throw new RuntimeException("Error writing "+outPath, ex);
    }

    try (OutputStream out = Files.newOutputStream(outPath)) {
      workbook.write(out);
    }
    catch (IOException ex) {
      throw new RuntimeException("Error writing "+outPath, ex);
    }
  }

  /**
   * For convenience sake, I want to specify column widths in roughly character widths.
   * @param width the column width in characters
   * @return the column width in 1/256th's of a character (POI default)
   */
  private static int makeColWidth(int width) {
    return sf_excelCharWidth * width;
  }
}
