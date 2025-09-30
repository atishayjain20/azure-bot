package com.example.reviewer.util;

public final class PromptTemplates {

    private PromptTemplates() {}

    public static final String REVIEW_INSTRUCTIONS = String.join("\n",
            "Your task is to review pull requests based on a provided git diff.",
            "",
            "Instructions:",
            "- ONLY comment on BREAKING CHANGES, BUGS, SECURITY ISSUES, or LOGIC ERRORS that could cause runtime failures.",
            "- DO NOT comment on general functionality verification, component integration checks, or 'ensure components work' type requests.",
            "- DO NOT comment on code that simply adds new features without breaking existing functionality.",
            "- If no breaking issues are found, return an empty comments array and a simple overall message like 'No issues found'.",
            "- Do not give positive comments or compliments.",
            "- Write the comment in GitHub Markdown format.",
            "- Use the pull request description for overall context",
            "- IMPORTANT: NEVER suggest adding comments to the code.",
        //     "- Severity & verbosity policy: classify each issue as one of {critical, major, minor}.",
        //     "  * critical/major: provide a detailed, actionable comment (root cause, fix sketch, risks).",
        //     "  * minor: keep comments short and to-the-point (1-2 sentences).",
            "- CRITICAL: Do not raise comments for non-functional changes such as:",
            "  * Dependency or manifest moves/updates (e.g., package.json dependencies reordered, version bumps).",
            "  * Imports or using statements moved without logic changes.",
            "  * Code blocks moved unchanged (line number shifts only).",
            "  * Formatting-only or whitespace-only diffs.",
            "  * Adding new components that don't break existing functionality.",
            "  * General verification requests like 'ensure components work' or 'verify implementation'.",
            "- Only raise comments when the change:",
            "  * Introduces a bug that could cause runtime errors.",
            "  * Breaks existing functionality or APIs.",
            "  * Introduces security vulnerabilities.",
            "  * Causes performance issues or memory leaks.",
            "  * Has logic errors that could lead to incorrect behavior.",
            "- If a dependency/library appears added/removed or reordered with no code-behavior impact, explicitly IGNORE it and do not produce a comment.",
            "- If an added line simply duplicates an existing line moved from elsewhere with no logic change, IGNORE it.",
            "- If you find no breaking issues to comment on, return an empty comments array [] and set overall to a simple message like 'No issues identified in the changes'."
    );

    public static final String FILE_FILTER_INSTRUCTIONS = String.join("\n",
            "Your task is to analyze a list of changed files from a pull request and return only the files that are relevant for a human review.",
            "",
            "Crucial Instructions:",
            "1. You must filter out files that are not relevant to the review process using the exclusion rules below.",
            "2. The file paths you return must be an exact, character-for-character match to the file paths provided in the input list. Do not shorten, simplify, or alter the paths in any way.",
            "",
            "Exclusion Rules (Files to IGNORE):",
            "* Lock files: package-lock.json, pnpm-lock.yaml, yarn.lock, Gemfile.lock, Pipfile.lock, poetry.lock, go.sum, composer.lock",
            "* Build & Generated Artifacts:",
            "    * Any files within directories named 'bin/', 'obj/', 'dist/', 'build/', or 'target/'.",
            "    * API client libraries, generated documentation, minified assets ('.min.js', '.min.css'), and snapshot files ('.snap').",
            "* Binary & Non-Code Files:",
            "    * Images ('.png', '.jpg', '.gif', '.svg')",
            "    * Compiled code or debug symbols ('.dll', '.exe', '.pdb', '.o', '.so', '.a')",
            "    * Visual Studio metadata ('.suo')",
            "    * Compressed files ('.zip', '.tar', '.gz')",
            "* Third-Party Libraries: Any files within directories like 'vendor/', 'node_modules/', 'bower_components/'.",
            "* System-Specific Config: '.DS_Store', 'thumbs.db'");

    public static final String STRICT_JSON_SCHEMA = String.join("\n",
            "Return STRICT JSON (no Markdown) with this schema:",
            "{",
            "  \"overall\": string,",
            "  \"comments\": [ { \"file\": string, \"line\": number, \"comment\": string, \"changed_line_text\": string } ],",
            "  \"relevantFiles\": [ string ]",
            "}",
            "Rules:",
            "- Only use line numbers for the added lines in the diff (RIGHT side).",
            "- Use file paths exactly as in the diff headers (e.g., src/..).",
            "- Keep each per-line comment specific to that added line and its 2-3 lines of context.",
            "- If no issues are found, return an empty comments array [] and set overall to a simple message.",
            "- Apply the exclusion rules to produce 'relevantFiles'. If none remain, return an empty array.");
}

