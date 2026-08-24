package com.adil.cvscanner.upload.infrastructure;

import com.adil.cvscanner.upload.config.UploadStorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

@Component
public class LocalUploadStorage {

    private final Path storageRoot;

    public LocalUploadStorage(
            UploadStorageProperties properties
    ) {

        this.storageRoot =
                properties
                        .storageRoot()
                        .toAbsolutePath()
                        .normalize();
    }

    /*
     * ============================================================
     * STAGING
     * ============================================================
     */

    public Path stage(
            MultipartFile file
    ) {

        UUID stagingId =
                UUID.randomUUID();

        Path stagingDirectory =
                stagingRoot()
                        .resolve(
                                stagingId.toString()
                        )
                        .normalize();

        try {

            Files.createDirectories(
                    stagingDirectory
            );

            Path target =
                    stagingDirectory.resolve(
                            "upload.zip"
                    );

            try (
                    InputStream inputStream =
                            file.getInputStream()
            ) {

                Files.copy(
                        inputStream,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return target;

        } catch (
                IOException exception
        ) {

            throw new UploadStorageException(
                    "Failed to store uploaded ZIP file",
                    exception
            );
        }
    }

    /*
     * ============================================================
     * DELETE STAGED FILE
     * ============================================================
     */

    public void delete(
            Path path
    ) {

        if (
                path == null
        ) {

            return;
        }

        Path managedPath =
                requireManagedPath(
                        path
                );

        try {

            Files.deleteIfExists(
                    managedPath
            );

            Path parent =
                    managedPath.getParent();

            /*
             * stage():
             *
             * staging/<uuid>/upload.zip
             *
             * upload.zip silindikdən sonra həmin
             * <uuid> directory-ni də silirik.
             *
             * storageRoot və staging root-a toxunmuruq.
             */
            if (
                    parent != null
                            &&
                            !parent.equals(
                                    storageRoot
                            )
                            &&
                            !parent.equals(
                                    stagingRoot()
                            )
            ) {

                Files.deleteIfExists(
                        parent
                );
            }

        } catch (
                IOException exception
        ) {

            throw new UploadStorageException(
                    "Failed to clean staged upload",
                    exception
            );
        }
    }

    /*
     * ============================================================
     * UPLOAD DIRECTORY
     * ============================================================
     */

    public Path uploadDirectory(
            UUID uploadId
    ) {

        Objects.requireNonNull(
                uploadId,
                "uploadId must not be null"
        );

        return storageRoot
                .resolve(
                        uploadId.toString()
                )
                .normalize();
    }

    /*
     * ============================================================
     * EXTRACTION DIRECTORY
     * ============================================================
     */

    public Path extractionDirectory(
            UUID uploadId
    ) {

        return uploadDirectory(
                uploadId
        )
                .resolve(
                        "cvs"
                )
                .normalize();
    }

    /*
     * ============================================================
     * DELETE ONE UPLOAD STORAGE
     * ============================================================
     *
     * Cleanup service arbitrary Path vermir.
     *
     * Yalnız uploadId verir.
     *
     * Target həmişə:
     *
     * storageRoot/<uploadId>
     *
     * olur.
     *
     *
     * Return:
     *
     * true  -> directory həqiqətən mövcud idi
     * false -> artıq yox idi
     */

    public boolean deleteUploadDirectory(
            UUID uploadId
    ) {

        Path uploadDirectory =
                uploadDirectory(
                        uploadId
                );

        boolean existed =
                Files.exists(
                        uploadDirectory
                );

        deleteRecursively(
                uploadDirectory
        );

        return existed;
    }

    /*
     * ============================================================
     * RECURSIVE DELETE
     * ============================================================
     */

    public void deleteRecursively(
            Path path
    ) {

        if (
                path == null
        ) {

            return;
        }

        /*
         * Əvvəl security boundary check.
         */
        Path managedPath =
                requireManagedPath(
                        path
                );

        /*
         * Idempotency:
         *
         * directory artıq yoxdursa error deyil.
         */
        if (
                Files.notExists(
                        managedPath
                )
        ) {

            return;
        }

        try (
                var paths =
                        Files.walk(
                                managedPath
                        )
        ) {

            paths
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(
                            currentPath -> {

                                try {

                                    Files.deleteIfExists(
                                            currentPath
                                    );

                                } catch (
                                        IOException exception
                                ) {

                                    throw new UncheckedIOException(
                                            exception
                                    );
                                }
                            }
                    );

        } catch (
                IOException
                |
                UncheckedIOException exception
        ) {

            throw new UploadStorageException(
                    "Failed to clean upload storage",
                    exception
            );
        }
    }

    /*
     * ============================================================
     * STORAGE BOUNDARY
     * ============================================================
     *
     * Məsələn:
     *
     * storageRoot =
     * C:/.../storage/uploads
     *
     *
     * Allowed:
     *
     * C:/.../storage/uploads/<uuid>
     * C:/.../storage/uploads/<uuid>/cvs
     * C:/.../storage/uploads/staging/<uuid>/upload.zip
     *
     *
     * Forbidden:
     *
     * C:/
     * C:/Users
     * storageRoot özü
     * storageRoot/staging özü
     */

    private Path requireManagedPath(
            Path path
    ) {

        Path normalizedPath =
                path
                        .toAbsolutePath()
                        .normalize();

        if (
                !normalizedPath.startsWith(
                        storageRoot
                )
                        ||
                        normalizedPath.equals(
                                storageRoot
                        )
                        ||
                        normalizedPath.equals(
                                stagingRoot()
                        )
        ) {

            throw new UploadStorageException(
                    "Refusing to delete path outside managed upload storage",
                    new IllegalArgumentException(
                            "Unsafe storage cleanup target"
                    )
            );
        }

        return normalizedPath;
    }



    private Path stagingRoot() {

        return storageRoot
                .resolve(
                        "staging"
                )
                .normalize();
    }
}