package org.pharmgkb;

import org.pharmgkb.io.CpicTranslationSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class will take all <code>.tsv</code> files in the translations directory and make Excel <code>.xlsx</code>
 * files out of them.
 *
 * @author Ryan Whaley
 */
public class TranslationSheetCLI {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String sf_outputDir = "out";
  private static final String sf_translationsDir = "translations";

  public static void main(String[] args) {
    Path input = Paths.get(sf_translationsDir);
    Path output = Paths.get(sf_outputDir);

    try {
      Files.list(input)
          .filter(p -> p.toString().endsWith(".tsv"))
          .forEach(p -> {
            CpicTranslationSheet sheet = new CpicTranslationSheet(p);
            sheet.exportExcel(output);
      });
    } catch (IOException e) {
      sf_logger.error("Error writing translation sheets");
    }
  }
}
