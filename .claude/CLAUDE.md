# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build
mvn compile                    # Compile only
mvn package -DskipTests        # Build fat JAR (output: target/job-match-0.1.0-SNAPSHOT.jar)

# Test
mvn test                       # Run all tests
mvn test -Dtest=ClassName      # Run single test class
mvn test -Dtest=ClassName#methodName  # Run single test method

# Install locally
./install.sh                   # Build and install to ~/.jobmatch/bin

# Run (after install)
jobmatch analyze -r resume.pdf -j job.txt
```

## Architecture Overview

This is a CLI tool for matching resumes against job descriptions using LLM-powered parsing.

### Processing Pipeline

```
Input → Parser Layer → Matcher Engine → Report Output
         (LLM/Rules)    (4 stages)
```

**Parser Layer** (`parser/`):
- `ResumeParserService` / `JDParserService` - LLM-based structured extraction
- `RuleBasedParser` - Fallback when LLM unavailable

**Matcher Engine** (`matcher/`) - 4-stage pipeline:
1. `HardGateChecker` - Binary pass/fail on hard requirements (education, years, must-have skills)
2. `SoftScoreCalculator` - Weighted scoring (skill 60%, experience 30%, bonus 10%)
3. `GapAnalyzer` - Identify missing skills and candidate strengths
4. `ActionGenerator` - Produce actionable suggestions

### Configuration System

Priority (low → high): JAR defaults → `~/.jobmatch/*.yaml` → `./jobmatch.yaml` → env vars

Key config files:
- `application.yaml` - LLM provider, storage paths
- `matcher.yaml` - Scoring weights, thresholds
- `skill_implications.yaml` - Skill inference rules (e.g., Spring Boot → Java)

### LLM Integration (`llm/`)

- `LlmClient` interface with `OllamaClient` / `OpenAiClient` implementations
- `RetryableLlmClient` decorator for retry logic
- `LlmClientFactory` for provider selection

## Code Conventions

- Comments in English, Javadoc for public APIs
- Custom exceptions extend `JobMatchException` with error codes (1xxx-5xxx)
- Use Context7 MCP tools for library docs when adding dependencies

## Key Files for Common Tasks

| Task | Files |
|------|-------|
| Add CLI command | `cli/JobMatchCommand.java` (parent), new `*Command.java` |
| Modify scoring logic | `matcher/SoftScoreCalculator.java`, `resources/matcher.yaml` |
| Add skill inference rule | `resources/skill_implications.yaml` |
| Change LLM prompts | `parser/*ParserService.java` |
