/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.api;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.concurrent.Immutable;

/** Configures how to build a layer in the container image. Instantiate with {@link #builder}. */
@Immutable
public class LayerConfiguration {

  /** Builds a {@link LayerConfiguration}. */
  public static class Builder {

    private String name = "";
    private List<LayerEntry> entries = new ArrayList<>();

    private Builder() {}

    /**
     * Sets a name for this layer. This name does not affect the contents of the layer.
     *
     * @param name the name
     * @return this
     */
    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets entries for the layer.
     *
     * @param entries file entries in the layer
     * @return this
     */
    public Builder setEntries(List<LayerEntry> entries) {
      this.entries = new ArrayList<>(entries);
      return this;
    }

    /**
     * Adds an entry to the layer.
     *
     * @param entry the layer entry to add
     * @return this
     */
    public Builder addEntry(LayerEntry entry) {
      entries.add(entry);
      return this;
    }

    /**
     * Adds an entry to the layer. Only adds the single source file to the exact path in the
     * container file system.
     *
     * <p>For example, {@code addEntry(Paths.get("myfile"),
     * AbsoluteUnixPath.get("/path/in/container"))} adds a file {@code myfile} to the container file
     * system at {@code /path/in/container}.
     *
     * <p>For example, {@code addEntry(Paths.get("mydirectory"),
     * AbsoluteUnixPath.get("/path/in/container"))} adds a directory {@code mydirectory/} to the
     * container file system at {@code /path/in/container/}. This does <b>not</b> add the contents
     * of {@code mydirectory}.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addEntry(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      return addEntry(
          sourceFile,
          pathInContainer,
          DEFAULT_FILE_PERMISSIONS_PROVIDER.apply(sourceFile, pathInContainer));
    }

    /**
     * Adds an entry to the layer with the given permissions. Only adds the single source file to
     * the exact path in the container file system. See {@link Builder#addEntry(Path,
     * AbsoluteUnixPath)} for more information.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @param permissions the file permissions on the container
     * @return this
     * @see Builder#addEntry(Path, AbsoluteUnixPath)
     * @see FilePermissions#DEFAULT_FILE_PERMISSIONS
     * @see FilePermissions#DEFAULT_FOLDER_PERMISSIONS
     */
    public Builder addEntry(
        Path sourceFile, AbsoluteUnixPath pathInContainer, FilePermissions permissions) {
      return addEntry(sourceFile, pathInContainer, permissions, DEFAULT_MODIFICATION_TIME);
    }

    /**
     * Adds an entry to the layer with the given file modification time. Only adds the single source
     * file to the exact path in the container file system. See {@link Builder#addEntry(Path,
     * AbsoluteUnixPath)} for more information.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @param modificationTime the file modification time
     * @return this
     * @see Builder#addEntry(Path, AbsoluteUnixPath)
     */
    public Builder addEntry(
        Path sourceFile, AbsoluteUnixPath pathInContainer, Instant modificationTime) {
      return addEntry(
          sourceFile,
          pathInContainer,
          DEFAULT_FILE_PERMISSIONS_PROVIDER.apply(sourceFile, pathInContainer),
          modificationTime);
    }

    /**
     * Adds an entry to the layer with the given permissions and file modification time. Only adds
     * the single source file to the exact path in the container file system. See {@link
     * Builder#addEntry(Path, AbsoluteUnixPath)} for more information.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @param permissions the file permissions on the container
     * @param modificationTime the file modification time
     * @return this
     * @see Builder#addEntry(Path, AbsoluteUnixPath)
     * @see FilePermissions#DEFAULT_FILE_PERMISSIONS
     * @see FilePermissions#DEFAULT_FOLDER_PERMISSIONS
     */
    public Builder addEntry(
        Path sourceFile,
        AbsoluteUnixPath pathInContainer,
        FilePermissions permissions,
        Instant modificationTime) {
      return addEntry(new LayerEntry(sourceFile, pathInContainer, permissions, modificationTime));
    }

    /**
     * Adds an entry to the layer. If the source file is a directory, the directory and its contents
     * will be added recursively.
     *
     * <p>For example, {@code addEntryRecursive(Paths.get("mydirectory",
     * AbsoluteUnixPath.get("/path/in/container"))} adds {@code mydirectory} to the container file
     * system at {@code /path/in/container} such that {@code mydirectory/subfile} is found at {@code
     * /path/in/container/subfile}.
     *
     * @param sourceFile the source file to add to the layer recursively
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     * @throws IOException if an exception occurred when recursively listing the directory
     */
    public Builder addEntryRecursive(Path sourceFile, AbsoluteUnixPath pathInContainer)
        throws IOException {
      return addEntryRecursive(sourceFile, pathInContainer, DEFAULT_FILE_PERMISSIONS_PROVIDER);
    }

    /**
     * Adds an entry to the layer. If the source file is a directory, the directory and its contents
     * will be added recursively.
     *
     * @param sourceFile the source file to add to the layer recursively
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @param filePermissionProvider a provider that takes a source path and destination path on the
     *     container and returns the file permissions that should be set for that path
     * @return this
     * @throws IOException if an exception occurred when recursively listing the directory
     */
    public Builder addEntryRecursive(
        Path sourceFile,
        AbsoluteUnixPath pathInContainer,
        BiFunction<Path, AbsoluteUnixPath, FilePermissions> filePermissionProvider)
        throws IOException {
      return addEntryRecursive(
          sourceFile, pathInContainer, filePermissionProvider, DEFAULT_MODIFICATION_TIME_PROVIDER);
    }

    /**
     * Adds an entry to the layer. If the source file is a directory, the directory and its contents
     * will be added recursively.
     *
     * @param sourceFile the source file to add to the layer recursively
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @param filePermissionProvider a provider that takes a source path and destination path on the
     *     container and returns the file permissions that should be set for that path
     * @param modificationTimeProvider a provider that takes a source path and destination path on
     *     the container and returns the file modification time that should be set for that path
     * @return this
     * @throws IOException if an exception occurred when recursively listing the directory
     */
    public Builder addEntryRecursive(
        Path sourceFile,
        AbsoluteUnixPath pathInContainer,
        BiFunction<Path, AbsoluteUnixPath, FilePermissions> filePermissionProvider,
        BiFunction<Path, AbsoluteUnixPath, Instant> modificationTimeProvider)
        throws IOException {
      FilePermissions permissions = filePermissionProvider.apply(sourceFile, pathInContainer);
      Instant modificationTime = modificationTimeProvider.apply(sourceFile, pathInContainer);
      addEntry(sourceFile, pathInContainer, permissions, modificationTime);
      if (!Files.isDirectory(sourceFile)) {
        return this;
      }
      try (Stream<Path> files = Files.list(sourceFile)) {
        for (Path file : files.collect(Collectors.toList())) {
          addEntryRecursive(
              file,
              pathInContainer.resolve(file.getFileName()),
              filePermissionProvider,
              modificationTimeProvider);
        }
      }
      return this;
    }

    /**
     * Returns the built {@link LayerConfiguration}.
     *
     * @return the built {@link LayerConfiguration}
     */
    public LayerConfiguration build() {
      return new LayerConfiguration(name, entries);
    }
  }

  /** Provider that returns default file permissions (644 for files, 755 for directories). */
  public static final BiFunction<Path, AbsoluteUnixPath, FilePermissions>
      DEFAULT_FILE_PERMISSIONS_PROVIDER =
          (sourcePath, destinationPath) ->
              Files.isDirectory(sourcePath)
                  ? FilePermissions.DEFAULT_FOLDER_PERMISSIONS
                  : FilePermissions.DEFAULT_FILE_PERMISSIONS;

  /** Default file modification time (EPOCH + 1 second). */
  public static final Instant DEFAULT_MODIFICATION_TIME = Instant.ofEpochSecond(1);

  /** Provider that returns default file modification time (EPOCH + 1 second). */
  public static final BiFunction<Path, AbsoluteUnixPath, Instant>
      DEFAULT_MODIFICATION_TIME_PROVIDER =
          (sourcePath, destinationPath) -> DEFAULT_MODIFICATION_TIME;

  /**
   * Gets a new {@link Builder} for {@link LayerConfiguration}.
   *
   * @return a new {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  private final String name;
  private final List<LayerEntry> entries;

  /**
   * Use {@link #builder} to instantiate.
   *
   * @param name an optional name for the layer
   * @param entries the list of {@link LayerEntry}s
   */
  private LayerConfiguration(String name, List<LayerEntry> entries) {
    this.name = name;
    this.entries = entries;
  }

  /**
   * Gets the name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the list of entries.
   *
   * @return the list of entries
   */
  public ImmutableList<LayerEntry> getLayerEntries() {
    return ImmutableList.copyOf(entries);
  }

  public Builder toBuilder() {
    return builder().setName(name).setEntries(entries);
  }
}
