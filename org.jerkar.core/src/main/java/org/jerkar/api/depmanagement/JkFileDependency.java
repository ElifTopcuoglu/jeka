package org.jerkar.api.depmanagement;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.jerkar.api.utils.JkUtilsPath;

/**
 * Dependencies that can directly provide files without passing by an
 * external medium.
 *
 * @author Jerome Angibaud
 */
public interface JkFileDependency extends JkDependency {
    /**
     * Returns files constituting this file dependencies.
     */
    @Deprecated
    default List<File> files() {
        return JkUtilsPath.toFiles(paths());
    }

    List<Path> paths();
}
