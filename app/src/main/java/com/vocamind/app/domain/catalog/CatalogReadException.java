package com.vocamind.app.domain.catalog;

public final class CatalogReadException extends RuntimeException {
    public CatalogReadException(Throwable cause) {
        super("Unable to read offline catalog", cause);
    }
}
