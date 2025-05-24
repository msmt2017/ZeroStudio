package com.dark.androidbox.codeView;

import android.graphics.Color;
import com.amrdeveloper.codeview.CodeView;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Editor {
    public CodeView txtCode;

    // Patterns for syntax highlighting
    private final Pattern keywordsPattern = Pattern.compile(
            "\\b(void|public|private|protected|static|final|new|return|int|short|long|float|double|boolean|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|throws|interface|extends|implements|package|import)\\b");
    private final Pattern classPattern = Pattern.compile(
            "\\b(String|Array|ArrayList|HashMap|Map|Pattern)\\b");
    private final Pattern methodPattern = Pattern.compile(
            "\\b([a-zA-Z_$][a-zA-Z\\d_$]*)(\\s*\\()");
    private final Pattern variablePattern = Pattern.compile(
            "(public|private|protected)?\\s*(static)?\\s*(final)?\\s*([A-Za-z0-9_$<>]+)\\s+([a-zA-Z_$][a-zA-Z\\d_$]*)\\s*([=;])");
    private final Pattern packagePattern = Pattern.compile(
            "(?<=import\\s)([\\w.]+\\*?)(?=;)");
    private final Pattern commentPattern = Pattern.compile(
            "/\\*(?:[^*]|\\*(?!/))*\\*/|//.*");

    // Colors for syntax highlighting
    private static final int KEYWORD_COLOR = Color.parseColor("#B57AFF");  // Purple
    private static final int CLASS_COLOR = Color.parseColor("#A9B7C6");    // Light gray
    private static final int METHOD_COLOR = Color.parseColor("#469ded");   // Yellow
    private static final int VARIABLE_COLOR = Color.parseColor("#9876AA"); // Purple
    private static final int PACKAGE_COLOR = Color.parseColor("#6897BB");  // Blue
    private static final int COMMENT_COLOR = Color.parseColor("#808080");  // Gray

    public Editor(CodeView codeView) {
        this.txtCode = codeView;
        setUp();
    }

    private void setUp() {
        // Setup line numbering
        txtCode.setEnableLineNumber(true);
        txtCode.setLineNumberTextColor(Color.parseColor("#606366"));
        txtCode.setLineNumberTextSize(30);

        // Setup auto-complete for code pairs
        Map<Character, Character> pairCompleteMap = new HashMap<>();
        pairCompleteMap.put('{', '}');
        pairCompleteMap.put('[', ']');
        pairCompleteMap.put('(', ')');
        pairCompleteMap.put('<', '>');
        pairCompleteMap.put('"', '"');
        txtCode.enablePairComplete(true);
        txtCode.setPairCompleteMap(pairCompleteMap);

        // Add syntax highlighting patterns
        txtCode.addSyntaxPattern(keywordsPattern, KEYWORD_COLOR);
       // txtCode.addSyntaxPattern(classPattern, CLASS_COLOR);
        txtCode.addSyntaxPattern(methodPattern, METHOD_COLOR);
        //txtCode.addSyntaxPattern(variablePattern, VARIABLE_COLOR);
        txtCode.addSyntaxPattern(packagePattern, PACKAGE_COLOR);
        txtCode.addSyntaxPattern(commentPattern, COMMENT_COLOR);

        // Set editor background color
        txtCode.setBackgroundColor(Color.parseColor("#2B2B2B"));
        txtCode.setTextColor(Color.parseColor("#A9B7C6"));
    }

    public void setTxtColor(String targetString, int color) {
        Pattern pattern = Pattern.compile(Pattern.quote(targetString));
        txtCode.addSyntaxPattern(pattern, color);
    }
}