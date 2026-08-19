package com.figma.design.dto;

public record DesignTokenComparisonRow(
        String component,
        String figmaColor,
        String codeColor,
        int figmaSpacing,
        int codeSpacing,
        int figmaFontSize,
        int codeFontSize,
        String figmaFontWeight,
        String codeFontWeight,
        int figmaBorderRadius,
        int codeBorderRadius,
        int figmaWidth,
        int codeWidth,
        int figmaHeight,
        int codeHeight
) {
}
