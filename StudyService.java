package com.notesai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notesai.client.ClaudeClient;
import com.notesai.model.StudyPack;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Builds the prompt, calls the LLM and validates what comes back. */
@Service
public class StudyService {

    private static final int MIN_CHARS = 200;
    private static final int MAX_CHARS = 60_000; // keeps the request within a sensible token budget

    private static final String SYSTEM = """
            You are a study assistant. You turn lecture notes into study material.
            Use only facts that appear in the notes. Reply with a single JSON object and nothing else:
            no markdown fences, no commentary.
            """;

    private final ClaudeClient claude;
    private final ObjectMapper mapper;

    public StudyService(ClaudeClient claude, ObjectMapper mapper) {
        this.claude = claude;
        this.mapper = mapper;
    }

    public StudyPack generate(String notes, int flashcards, int questions) {
        String text = notes == null ? "" : notes.strip();
        if (text.length() < MIN_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Notes are too short. Add at least a few paragraphs.");
        }
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS);
        }
        flashcards = clamp(flashcards, 3, 20);
        questions = clamp(questions, 3, 15);

        String raw = claude.complete(SYSTEM, buildPrompt(text, flashcards, questions));
        return validate(parse(raw));
    }

    private String buildPrompt(String notes, int flashcards, int questions) {
        return """
                Create study material from the notes below.

                Return JSON in exactly this shape:
                {
                  "summary": "A clear summary in 120-200 words.",
                  "keyPoints": ["5 to 8 short key points"],
                  "flashcards": [{"front": "term or question", "back": "concise answer"}],
                  "quiz": [{
                    "question": "...",
                    "options": ["A", "B", "C", "D"],
                    "answerIndex": 0,
                    "explanation": "One sentence on why the answer is correct."
                  }]
                }

                Rules:
                - Exactly %d flashcards and exactly %d quiz questions.
                - Every quiz question has exactly 4 options, one correct. answerIndex is 0-based.
                - Spread the correct answers across positions; do not always use the same index.
                - Write in the same language as the notes.

                NOTES:
                ---
                %s
                ---
                """.formatted(flashcards, questions, notes);
    }

    private StudyPack parse(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI reply was not valid JSON. Try again.");
        }
        return mapper.readValue(raw.substring(start, end + 1), StudyPack.class);
    }

    /** Drops malformed items so the UI never has to deal with them. */
    private StudyPack validate(StudyPack p) {
        List<StudyPack.Flashcard> cards = nullSafe(p.flashcards()).stream()
                .filter(c -> c.front() != null && c.back() != null)
                .toList();
        List<StudyPack.QuizQuestion> quiz = nullSafe(p.quiz()).stream()
                .filter(q -> q.question() != null
                        && q.options() != null
                        && q.options().size() == 4
                        && q.answerIndex() >= 0 && q.answerIndex() < 4)
                .toList();
        return new StudyPack(p.summary(), nullSafe(p.keyPoints()), cards, quiz);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}