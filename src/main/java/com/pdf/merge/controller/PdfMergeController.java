package com.pdf.merge.controller;

import com.pdf.merge.service.PdfMergeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.nio.file.Path;
import io.swagger.v3.oas.annotations.parameters.RequestBody;


@Tag(name = "PDF Merge API", description = "Endpoints to merge and split PDF files")
@RestController
@RequestMapping("/api/pdf")
public class PdfMergeController {
    private final PdfMergeService pdfMergeService;
    @Autowired
    public PdfMergeController(PdfMergeService pdfMergeService) {
        this.pdfMergeService = pdfMergeService;
    }

    @PostMapping("/mergeAndSaveToDisk")
    public Mono<Path> mergeAndSaveToDisk(@RequestParam String sourceDir,
                                @RequestParam String outputFile) {
        return pdfMergeService.mergePdfs(sourceDir, outputFile);
    }
    @PostMapping(value = "/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Resource>> mergePdfs(@RequestPart("files") Flux<FilePart> files) {
        return pdfMergeService.mergePdfs(files)
                .map(resource -> {
                    String filename = resource.getFilename() != null ? resource.getFilename() : "merged.pdf";
                    MediaType contentType = filename.endsWith(".zip")
                            ? MediaType.APPLICATION_OCTET_STREAM  // or MediaType.valueOf("application/zip")
                            : MediaType.APPLICATION_PDF;

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                            .contentType(contentType)
                            .body(resource);
                });
    }
}
