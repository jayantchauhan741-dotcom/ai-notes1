package com.notesai.web;

import com.notesai.model.StudyPack;
import com.notesai.service.PdfTextExtractor;
import com.notesai.service.StudyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class StudyController {

    private final PdfTextExtractor extractor;
    private final StudyService study;

    public StudyController(PdfTextExtractor extractor, StudyService study) {
        this.extractor = extractor;
        this.study = study;
    }

    /** Accepts either an uploaded file or pasted text. */
    @PostMapping(value = "/study", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StudyPack create(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "8") int flashcards,
            @RequestParam(defaultValue = "5") int questions) {

        String notes;
        if (file != null && !file.isEmpty()) {
            notes = extractor.extract(file);
        } else if (text != null && !text.isBlank()) {
            notes = text;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a file or paste some notes.");
        }
        return study.generate(notes, flashcards, questions);
    }
}