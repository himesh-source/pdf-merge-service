package com.pdf.merge.service;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;


public interface PdfMergeService {
    Mono<Path> mergePdfs(String sourceDir, String outputFilePath);
    Mono<Resource> mergePdfs(Flux<FilePart> files);
}
