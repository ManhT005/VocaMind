package com.vocamind.app.data.catalog.upgrade;

/** Fail closed: caller displays its existing safe error/retry state, never resets userdata. */
public final class CatalogUpgradeException extends IllegalStateException {
    public CatalogUpgradeException(String message, Throwable cause) { super(message, cause); }
}
