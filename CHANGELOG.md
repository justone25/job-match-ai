# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2025-12-12

### Added

#### Core Features
- Resume parsing with LLM (Ollama)
  - Extract skills with proficiency levels
  - Extract work experience with highlights
  - Extract education information
  - Support for Chinese and English resumes

- JD parsing with LLM
  - Extract hard requirements (skills, experience, education)
  - Extract soft requirements (preferred skills, bonus items)
  - Extract implicit requirements (inferred level, management scope)
  - JD quality scoring

- Matching engine
  - Hard gate check (pass/fail on mandatory requirements)
  - Soft score calculation (weighted scoring)
  - Gap analysis (identify missing skills)
  - Action suggestions (personalized recommendations)

- Report generation
  - Markdown format with visual indicators
  - JSON format for programmatic access
  - Simple text format for terminal output

#### CLI Commands
- `analyze` - Analyze resume and JD matching
  - File mode: `-r <resume> -j <jd>`
  - Text mode: `--resume-text <text> --jd-text <text>`
  - Interactive mode (no arguments)
  - Output formats: markdown, json, simple

- `history` - Manage analysis history
  - List all history entries
  - View specific report
  - Delete report
  - Clear all history

- `cache` - Cache management
  - View cache statistics
  - Clean expired entries
  - Clear all cache

- `feedback` - User feedback
  - Submit feedback with rating (1-3)
  - Add optional comments
  - View feedback statistics

- `config` - Configuration viewer
  - Display current configuration
  - Show configuration file path

#### Error Handling
- Standardized error codes (1xxx-5xxx ranges)
  - 1xxx: Input errors (user fixable)
  - 2xxx: Parse errors
  - 3xxx: LLM errors
  - 4xxx: Storage errors
  - 5xxx: System errors
- User-friendly error messages with suggestions
- Retryable error detection

#### Storage
- JSON-based local storage
- History management
- Cache with TTL support
- Feedback collection

#### Logging
- File-based logging with rotation
- Separate error log
- LLM request/response logging
- Debug mode support via environment variable

#### Resilience
- Rule-based parser fallback when LLM unavailable
- Automatic LLM provider fallback
- Retry mechanism with exponential backoff

### Technical Details
- Java 17+
- Picocli for CLI framework
- Jackson for JSON processing
- OkHttp for HTTP client
- SLF4J + Logback for logging
- Lombok for boilerplate reduction
- JUnit 5 for testing

### Known Issues
- Logback conditional processing requires Janino library (optional)
- Long texts may exceed model context limit

## [Unreleased]

### Planned
- Web UI (localhost)
- Multiple resume management
- JD collection and analysis
- Trend analysis
- Cloud LLM support (OpenAI, Claude)
