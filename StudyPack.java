package com.notesai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Everything generated from one set of notes. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudyPack(
        String summary,
        List<String> keyPoints,
        List<Flashcard> flashcards,
        List<QuizQuestion> quiz) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Flashcard(String front, String back) {}

    /** answerIndex is 0-based and points into options. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuizQuestion(
            String question,
            List<String> options,
            int answerIndex,
            String explanation) {}
}