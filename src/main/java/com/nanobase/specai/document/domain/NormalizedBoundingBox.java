package com.nanobase.specai.document.domain;

public record NormalizedBoundingBox(
    int page,
    double x,
    double y,
    double width,
    double height
) {
    public NormalizedBoundingBox {
        if (page < 1) {
            throw new IllegalArgumentException("Bounding box page must be positive");
        }
        validate("x", x);
        validate("y", y);
        validate("width", width);
        validate("height", height);
        if (x + width > 1.000001d || y + height > 1.000001d) {
            throw new IllegalArgumentException("Bounding box exceeds the normalized page");
        }
    }

    public static NormalizedBoundingBox fromProvider(
        int page, double left, double top, double right, double bottom,
        double pageWidth, double pageHeight, int rotation) {
        if (pageWidth <= 0 || pageHeight <= 0 || right < left || bottom < top) {
            throw new IllegalArgumentException("Provider bounding box dimensions are invalid");
        }
        double x = left / pageWidth;
        double y = top / pageHeight;
        double width = (right - left) / pageWidth;
        double height = (bottom - top) / pageHeight;
        return switch (Math.floorMod(rotation, 360)) {
            case 0 -> new NormalizedBoundingBox(page, x, y, width, height);
            case 90 -> new NormalizedBoundingBox(page, 1d - y - height, x, height, width);
            case 180 -> new NormalizedBoundingBox(page, 1d - x - width,
                1d - y - height, width, height);
            case 270 -> new NormalizedBoundingBox(page, y, 1d - x - width, height, width);
            default -> throw new IllegalArgumentException(
                "Page rotation must be 0, 90, 180, or 270");
        };
    }

    private static void validate(String name, double value) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
