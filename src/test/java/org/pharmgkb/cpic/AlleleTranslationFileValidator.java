package org.pharmgkb.cpic;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Test to make sure TSV translation tables are in the expected format.
 *
 * @author Ryan Whaley
 */
public class AlleleTranslationFileValidator {
  private static final Logger sf_logger = LoggerFactory.getLogger(AlleleTranslationFileValidator.class);

  private static final int sf_minLineCount = 7;
  private static final String sf_separator = "\t";
  private static final Pattern sf_geneFieldPattern = Pattern.compile("^GENE:\\s*(\\w+)$");
  private static final Pattern sf_refSeqPattern = Pattern.compile("^.*(N\\w_(\\d+)\\.\\d+).*$");
  private static final Pattern sf_genomeBuildPattern = Pattern.compile("^.*(GRCh\\d+(?:\\.p\\d+)?).*$");
  private static final Pattern sf_populationTitle = Pattern.compile("^(.*) Allele Frequency$");
  private static final Pattern sf_basePattern = Pattern.compile("^(del[ATCG]*)|(ins[ATCG]*)|([ATCGMRWSYKVHDBN]+)$");
  private static final SimpleDateFormat sf_dateFormat = new SimpleDateFormat("MM/dd/yy");

  private static final int LINE_GENE = 0;
  private static final int LINE_NAMING = 1;
  private static final int LINE_PROTEIN = 2;
  private static final int LINE_CHROMO = 3;
  private static final int LINE_GENESEQ = 4;
  private static final int LINE_POPS = 6;
  private static final int OUTPUT_FORMAT_VERSION = 1;

  private static int lastVariantColumn;
  private static String geneName;
  private static String geneRefSeq;
  private static String versionDate;
  private static String versionTag; // TODO
  private static String genomeBuild;
  private static String chromosomeName;
  private static String chromosomeRefSeq;
  private static String proteinRefSeq;

  @Test
  public void testSheet() {
    Path translationsDirectory = Paths.get("translations");
    assertNotNull(translationsDirectory);

    assertTrue("Directory doesn't exist: " + translationsDirectory.toAbsolutePath().toString(), translationsDirectory.toFile().exists());
    assertTrue("Path isn't to a directory: " + translationsDirectory.toAbsolutePath().toString(), translationsDirectory.toFile().isDirectory());

    try {
      Files.newDirectoryStream(translationsDirectory, onlyTsvs).forEach(checkTranslationFile);
    } catch (IOException e) {
      fail("Exception while running test: " + e.getMessage());
    }
  }

  private static DirectoryStream.Filter<Path> onlyTsvs = p -> p.toString().endsWith(".tsv");

  private static Consumer<Path> checkTranslationFile = f -> {
    sf_logger.info("Checking {}", f.getFileName());
    try {
      List<String> lines = Files.readAllLines(f);

      assertTrue("Not enough lines in the file, expecting at least " + sf_minLineCount, lines.size()>sf_minLineCount);

      testGeneLine(lines.get(LINE_GENE).split(sf_separator));
      testNamingLine(lines.get(LINE_NAMING).split(sf_separator));
      testProteinLine(lines.get(LINE_PROTEIN).split(sf_separator));
      testChromoLine(lines.get(LINE_CHROMO).split(sf_separator));
      testGeneSeqLine(lines.get(LINE_GENESEQ).split(sf_separator));
      testPopLine(lines.get(LINE_POPS).split(sf_separator));
      testVariantLines(lines);

    } catch (Exception e) {
      e.printStackTrace();
      fail("Problem checking file "+e.getMessage());
    }
  };

  private static void testGeneLine(String[] fields) throws ParseException {
    assertNotNull(fields);
    assertTrue(fields.length >= 2);

    Matcher m = sf_geneFieldPattern.matcher(fields[0]);
    assertTrue("Gene field not in expected format: "+fields[0], m.matches());

    geneName = m.group(1);
    sf_logger.info("\tgene: " + geneName);

    Date date = sf_dateFormat.parse(fields[1]);
    assertNotNull(date);
    versionDate = fields[1];
  }

  private static void testNamingLine(String[] fields) {
    assertTrue("Row "+ LINE_NAMING +", Column 1: expected to be blank", StringUtils.isBlank(fields[0]));
  }

  private static void testProteinLine(String[] fields) {
    String title = fields[1];
    assertTrue("No protein description specified", StringUtils.isNotBlank(title));

    Matcher m = sf_refSeqPattern.matcher(title);
    assertTrue("No RefSeq identifier for protein line "+LINE_PROTEIN, m.matches());

    proteinRefSeq = m.group(1);
    sf_logger.info("\tprotein seq: "+proteinRefSeq);
  }

  private static void testChromoLine(String[] fields) throws IOException {
    String title = fields[1];
    assertTrue("No chromosomal position description specified", StringUtils.isNotBlank(title));

    Matcher m = sf_refSeqPattern.matcher(title);
    assertTrue("No RefSeq identifier for chromosomal line "+LINE_CHROMO, m.matches());

    chromosomeRefSeq = m.group(1);
    sf_logger.info("\tchromosome seq: " + chromosomeRefSeq);

    AssemblyMap assemblyMap = new AssemblyMap();
    String build = assemblyMap.get(chromosomeRefSeq);
    assertNotNull("Unrecognized chromosome identifier " + chromosomeRefSeq, build);
    assertEquals("Chromosome identifier not on GRCh38: " + chromosomeRefSeq, "b38", build);

    int chrNum = Integer.parseInt(m.group(2), 10); // a leading 0 sometimes indicates octal, but we know this is always base 10
    assertTrue("Unknown or unsupported chromosome number "+chrNum+" on chromosomal line "+LINE_CHROMO, (chrNum >= 1 && chrNum <= 24));
    if (chrNum == 23) {
		chromosomeName = "chrX";
	} else if (chrNum == 24) {
		chromosomeName = "chrY";
	} else {
		chromosomeName = "chr" + chrNum;
	}
    sf_logger.info("\tchromosome name: " + chromosomeName);

    m = sf_genomeBuildPattern.matcher(title);
    assertTrue("No genome build identifier for chromosomal line "+LINE_CHROMO, m.matches());

    genomeBuild = m.group(1);
    sf_logger.info("\tgenome build: " + genomeBuild);

    int lastVariantColumn = 2;
    for (int i=2; i<fields.length; i++) {
      if (StringUtils.isNotBlank(fields[i])) {
        lastVariantColumn = i;
      }
    }
    sf_logger.info("\t# variants specified: " + (lastVariantColumn-1));
  }

  private static void testGeneSeqLine(String[] fields) {
    String title = fields[1];
    assertTrue("No gene position description specified", StringUtils.isNotBlank(title));

    Matcher m = sf_refSeqPattern.matcher(title);
    assertTrue("No RefSeq identifier for gene sequence line "+LINE_GENESEQ, m.matches());

    geneRefSeq = m.group(1);
    sf_logger.info("\tgene seq: " + geneRefSeq);
  }

  private static void testPopLine(String[] fields) {
    assertEquals("Expected the title 'Allele' in first column of row " + (LINE_POPS+1), "Allele", fields[0]);
    assertEquals("Expected the title 'Allele Functional Status' in second column of row " + (LINE_POPS+1), "Allele Functional Status", fields[1]);

    assertTrue("No populations specified", fields.length>2);
    List<String> pops = Arrays.stream(Arrays.copyOfRange(fields, 2, fields.length))
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toList());
    assertNotNull(pops);
    assertTrue(pops.size()>0);

    pops.stream().forEach(p -> {
      Matcher m = sf_populationTitle.matcher(p);
      assertTrue("Allele frequency column title should end in 'Allele Frequency'", m.matches());
    });
  }

  private static void testVariantLines(List<String> lines) {
    boolean isVariantLine = false;
    String[] variantFields = lines.get(LINE_CHROMO).split(sf_separator);
    int lastVariantIndex = variantFields.length;

    for (String line : lines) {
      if (line.toLowerCase().startsWith("notes:")) {
        return;
      }
      else if (line.startsWith("Allele")) {
        isVariantLine = true;
      }
      else if (isVariantLine) {
        String[] fields = line.split(sf_separator);
        if (fields.length > 2) {
          Set<String> badAlleles = Arrays.stream(Arrays.copyOfRange(fields, 2, lastVariantIndex))
              .filter(f -> StringUtils.isNotBlank(f) && !sf_basePattern.matcher(f).matches())
              .collect(Collectors.toSet());
          assertFalse(fields[0] + " has bad base pair values " + badAlleles.stream().collect(Collectors.joining(";")), badAlleles.size()>0);
        }
      }
    }
  }
}
