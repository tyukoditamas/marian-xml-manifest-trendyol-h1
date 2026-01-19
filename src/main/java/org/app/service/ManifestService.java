package org.app.service;

import org.app.logging.AppLogger;
import org.app.logging.NoOpLogger;
import org.app.model.GoodsItem;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ManifestService {
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("MMddHHmm");
    private final AppLogger logger;
    private final ProgressListener progressListener;
    private final ExcelGoodsItemReader excelReader;
    private final XmlManifestGenerator xmlGenerator;
    private final GoodsDescriptionResolver descriptionResolver;

    public ManifestService() {
        this(new NoOpLogger(), new NoOpProgressListener());
    }

    public ManifestService(AppLogger logger) {
        this(logger, new NoOpProgressListener());
    }

    public ManifestService(AppLogger logger, ProgressListener progressListener) {
        this.logger = logger == null ? new NoOpLogger() : logger;
        this.progressListener = progressListener == null ? new NoOpProgressListener() : progressListener;
        this.excelReader = new ExcelGoodsItemReader(this.logger);
        this.xmlGenerator = new XmlManifestGenerator(this.logger);
        this.descriptionResolver = new GoodsDescriptionResolver(this.logger, this.progressListener);
    }

    public File generate(File excelFile, String lrn) throws Exception {
        if (excelFile == null || !excelFile.isFile()) {
            throw new IllegalArgumentException("Excel file is missing.");
        }
        if (lrn == null || lrn.trim().isEmpty()) {
            throw new IllegalArgumentException("LRN is missing.");
        }

        try {
            logger.info("Citire Excel in curs...");
            List<GoodsItem> items = excelReader.read(excelFile);
            if (items.isEmpty()) {
                throw new IllegalStateException("No goods items found in Excel.");
            }
            logger.info("Total linii selectate: " + items.size());

            descriptionResolver.applyDescriptions(items);

            String timestamp = LocalDateTime.now().format(OUTPUT_FORMATTER);
            String fileName = "H1_" + lrn.trim() + "_" + timestamp + ".xml";
            File outputFile = new File(excelFile.getParentFile(), fileName);
            logger.info("Generare XML in curs...");
            return xmlGenerator.generate(items, lrn.trim(), outputFile);
        } catch (Exception ex) {
            logger.error("Eroare in procesul de generare.", ex);
            throw ex;
        }
    }
}
