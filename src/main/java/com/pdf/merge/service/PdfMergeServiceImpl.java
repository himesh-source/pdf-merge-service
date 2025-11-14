package com.pdf.merge.service;


import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PdfMergeServiceImpl implements PdfMergeService{


    private static final long MAX_SIZE_BYTES = (long) (4.5 * 1024 * 1024); // 4.5 MB
    private static final double MAX_FILE_SIZE_MB = 4.5;


    public Mono<Path> mergePdfs(String sourceDir, String outputFilePath) {
        return Mono.fromCallable(() -> {
            Path srcPath = Paths.get(sourceDir);
            if (!Files.exists(srcPath) || !Files.isDirectory(srcPath)) {
                throw new IOException("Source directory not found: " + sourceDir);
            }

            List<File> pdfFiles = Files.list(srcPath)
                    .filter(f -> f.toString().toLowerCase().endsWith(".pdf"))
                    .map(Path::toFile)
                    .sorted(Comparator.comparing(File::getName))
                    .collect(Collectors.toList());

            if (pdfFiles.isEmpty()) {
                throw new IOException("No PDF files found in source directory.");
            }

            Path outputPath = Paths.get(outputFilePath);
            Files.createDirectories(outputPath.getParent());

            long currentSize = 0;
            int part = 1;
            Path currentOutput = getPartFile(outputPath, part);
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationFileName(currentOutput.toString());

            for (File pdf : pdfFiles) {
                merger.addSource(pdf);
                currentSize += pdf.length();

                if (currentSize >= MAX_SIZE_BYTES) {
                    merger.mergeDocuments(null);
                    compressPdf(currentOutput);

                    part++;
                    currentOutput = getPartFile(outputPath, part);
                    merger = new PDFMergerUtility();
                    merger.setDestinationFileName(currentOutput.toString());
                    currentSize = 0;
                }
            }

            merger.mergeDocuments(null);
            compressPdf(currentOutput);

            return outputPath;
        });
    }

    private Path getPartFile(Path baseOutput, int part) {
        String baseName = baseOutput.getFileName().toString().replace(".pdf", "");
        return baseOutput.getParent().resolve(baseName + "_part" + part + ".pdf");
    }

    private void compressPdf(Path pdfPath) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            doc.setAllSecurityToBeRemoved(true);
            doc.save(new File(pdfPath.toString().replace(".pdf", "_compressed.pdf")));
            Files.deleteIfExists(pdfPath);
            Files.move(Paths.get(pdfPath.toString().replace(".pdf", "_compressed.pdf")), pdfPath);
        }
    }

    @Override
    public Mono<Resource> mergePdfs(Flux<FilePart> files) {
        return files
                .flatMap(this::saveTempFile)
                .collectList()
                .flatMap(this::mergeAndSplitIfNeeded);
    }

    private Mono<File> saveTempFile(FilePart part) {
        try {
            File tempFile = File.createTempFile("upload-", ".pdf");
            return part.transferTo(tempFile).thenReturn(tempFile);
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    private Mono<Resource> mergeAndSplitIfNeeded(List<File> files) {
        return Mono.fromCallable(() -> {
            File mergedFile = File.createTempFile("merged-", ".pdf");

            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationFileName(mergedFile.getAbsolutePath());
            for (File file : files) {
                merger.addSource(file);
            }

            // Merge all PDFs into one file
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());

            // Check final size
            double sizeMB = mergedFile.length() / (1024.0 * 1024.0);
            if (sizeMB > MAX_FILE_SIZE_MB) {
                List<File> splitFiles = splitLargePdf(mergedFile);
                return zipSplitFiles(splitFiles);
            }

            return new FileSystemResource(mergedFile);
        });
    }

    private List<File> splitLargePdf(File mergedFile) throws IOException {
        List<File> splitFiles = new ArrayList<>();
        try (PDDocument sourceDoc = PDDocument.load(mergedFile)) {
            int totalPages = sourceDoc.getNumberOfPages();
            int fileIndex = 1;

            PDDocument current = new PDDocument();

            for (int i = 0; i < totalPages; i++) {
                current.addPage(sourceDoc.getPage(i));

                // Estimate size if we saved now
                File tempSplit = File.createTempFile("check-", ".pdf");
                current.save(tempSplit);
                double currentSizeMB = tempSplit.length() / (1024.0 * 1024.0);
                tempSplit.delete();

                boolean isLastPage = (i == totalPages - 1);

                // Save this chunk if size exceeds limit or last page
                if (currentSizeMB >= MAX_FILE_SIZE_MB || isLastPage) {
                    File splitFile = File.createTempFile("split-" + fileIndex++ + "-", ".pdf");
                    current.save(splitFile);
                    splitFiles.add(splitFile);
                    current.close();
                    current = new PDDocument();
                }
            }

            current.close();
        }
        return splitFiles;
    }

    private Resource zipSplitFiles(List<File> splitFiles) throws IOException {
        File zipFile = File.createTempFile("merged-split-", ".zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {

            for (File file : splitFiles) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    zipOut.putNextEntry(entry);
                    fis.transferTo(zipOut);
                    zipOut.closeEntry();
                }
            }
        }
        return new FileSystemResource(zipFile);
    }

}

