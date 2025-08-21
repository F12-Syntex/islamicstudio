package com.syntex.islamicstudio.db.importer;

import java.io.InputStream;
import java.sql.Connection;

public interface Importer {
    String getName();  // e.g. "quran-uthmani", "sahih-international"

    /**
     * Clears all data managed by this importer.
     */
    void clearData(Connection conn) throws Exception;

    /**
     * Performs the import using the provided input stream.
     */
    void importData(Connection conn, InputStream inputStream) throws Exception;
}