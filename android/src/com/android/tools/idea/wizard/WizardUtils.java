/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.wizard;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.base.CharMatcher;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Static utility methods used by the New Project/New Module wizards
 */
public class WizardUtils {
  private static final CharMatcher ILLEGAL_CHARACTER_MATCHER = CharMatcher.anyOf(WizardConstants.INVALID_FILENAME_CHARS);

  /**
   * Remove spaces, switch to lower case, and remove any invalid characters. If the resulting name
   * conflicts with an existing module, append a number to the end to make a unique name.
   */
  @NotNull
  public static String computeModuleName(@NotNull String appName, @Nullable Project project) {
    String moduleName = appName.toLowerCase().replaceAll(WizardConstants.INVALID_FILENAME_CHARS, "");
    moduleName = moduleName.replaceAll("\\s", "");

    if (!isUniqueModuleName(moduleName, project)) {
      int i = 2;
      while (!isUniqueModuleName(moduleName + Integer.toString(i), project)) {
        i++;
      }
      moduleName += Integer.toString(i);
    }
    return moduleName;
  }

  /**
   * @return true if the given module name is unique inside the given project. Returns true if the given
   * project is null.
   */
  public static boolean isUniqueModuleName(@NotNull String moduleName, @Nullable Project project) {
    if (project == null) {
      return true;
    }
    // Check our modules
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module m : moduleManager.getModules()) {
      if (m.getName().equalsIgnoreCase(moduleName)) {
        return false;
      }
    }
    return true;
  }

  /**
   * A Validation Result for Wizard Validations, contains a status and a message
   */
  public static class ValidationResult {
    public enum Status {
      OK, WARN, ERROR
    }

    public enum Message {
      NO_LOCATION_SPECIFIED("Please specify a project location"),
      BAD_SLASHES("Your project location contains incorrect slashes ('\\' vs '/')"),
      ILLEGAL_CHARACTER("Illegal character in project location path: '%1c' in filename %2s"),
      ILLEGAL_FILENAME("Illegal filename in project location path: %s"),
      WHITESPACE("Your project location contains whitespace. This can cause problems on some platforms and is not recommended."),
      NON_ASCII_CHARS("Your project location contains non-ASCII characters. This can cause problems on Windows. Proceed with caution."),
      PATH_NOT_WRITEABLE("The path '%s' is not writeable. Please choose a new location."),
      PROJECT_LOC_IS_FILE("There must not already be a file at the project location."),
      NON_EMPTY_DIR(
        "A non-empty directory already exists at the specified project location. Existing files may be overwritten. Proceed with caution."),
      PROJECT_IS_FILE_SYSTEM_ROOT("The project location can not be at the filesystem root"),
      PARENT_NOT_DIR("The project location's parent directory must be a directory, not a plain file");

      private final String myText;

      Message(final String text) {
        myText = text;
      }

      @Override
      public String toString() {
        return myText;
      }
    }

    public static final ValidationResult OK = new ValidationResult(Status.OK, null);

    private final Status myStatus;
    private final Message myMessage;
    private final Object[] myMessageParams;

    private ValidationResult(@NotNull Status status, @Nullable Message message, Object... messageParams) {
      myStatus = status;
      myMessage = message;
      myMessageParams = messageParams;
    }

    public static ValidationResult warn(@NotNull Message message, Object... params) {
      return new ValidationResult(Status.WARN, message, params);
    }

    public static ValidationResult error(@NotNull Message message, Object... params) {
      return new ValidationResult(Status.ERROR, message, params);
    }

    @Nullable
    @VisibleForTesting
    Message getMessage() {
      return myMessage;
    }

    @VisibleForTesting
    Object[] getMessageParams() {
      return myMessageParams;
    }

    public String getFormattedMessage() {
      if(myMessage == null) {
        throw new IllegalStateException("Null message, are you trying to get the message of an OK?");
      }
      return String.format(myMessage.toString(), myMessageParams);
    }

    @NotNull
    public Status getStatus() {
      return myStatus;
    }

    public boolean isError() {
      return myStatus.equals(Status.ERROR);
    }

    public boolean isOk() {
      return myStatus.equals(Status.OK);
    }
  }

  /**
   * Will return {@link com.android.tools.idea.wizard.WizardUtils.ValidationResult.OK} if projectLocation is valid
   * or {@link com.android.tools.idea.wizard.WizardUtils.ValidationResult} with error if not.
   */
  @NotNull
  public static ValidationResult validateLocation(@Nullable String projectLocation) {
    ValidationResult warningResult = null;
    if (projectLocation == null || projectLocation.isEmpty()) {
      return ValidationResult.error(ValidationResult.Message.NO_LOCATION_SPECIFIED);
    }
    // Check the separators
    if ((File.separatorChar == '/' && projectLocation.contains("\\")) ||
        (File.separatorChar == '\\' && projectLocation.contains("/"))) {
      return ValidationResult.error(ValidationResult.Message.BAD_SLASHES);
    }
    // Check the individual components for not allowed characters.
    File testFile = new File(projectLocation);
    while (testFile != null) {
      String filename = testFile.getName();
      if (ILLEGAL_CHARACTER_MATCHER.matchesAnyOf(filename)) {
        char illegalChar = filename.charAt(ILLEGAL_CHARACTER_MATCHER.indexIn(filename));
        return ValidationResult.error(ValidationResult.Message.ILLEGAL_CHARACTER, illegalChar, filename);
      }
      if (WizardConstants.INVALID_WINDOWS_FILENAMES.contains(filename.toLowerCase())) {
        return ValidationResult.error(ValidationResult.Message.ILLEGAL_FILENAME, filename);
      }
      if (CharMatcher.WHITESPACE.matchesAnyOf(filename)) {
        warningResult = ValidationResult.warn(ValidationResult.Message.WHITESPACE);
      }
      if (!CharMatcher.ASCII.matchesAllOf(filename)) {
        warningResult = ValidationResult.warn(ValidationResult.Message.NON_ASCII_CHARS);
      }
      // Check that we can write to that location: make sure we can write into the first extant directory in the path.
      if (!testFile.exists() && testFile.getParentFile() != null && testFile.getParentFile().exists()) {
        if (!testFile.getParentFile().canWrite()) {
          return ValidationResult.error(ValidationResult.Message.PATH_NOT_WRITEABLE, testFile.getParentFile().getPath());
        }
      }
      testFile = testFile.getParentFile();
    }

    File file = new File(projectLocation);
    if (file.isFile()) {
      return ValidationResult.error(ValidationResult.Message.PROJECT_LOC_IS_FILE);
    } else if (file.isDirectory() && TemplateUtils.listFiles(file).length > 0) {
      return ValidationResult.error(ValidationResult.Message.NON_EMPTY_DIR);
    }
    if (file.getParent() == null) {
      return ValidationResult.error(ValidationResult.Message.PROJECT_IS_FILE_SYSTEM_ROOT);
    }
    if (file.getParentFile().exists() && !file.getParentFile().isDirectory()) {
      return ValidationResult.error(ValidationResult.Message.PARENT_NOT_DIR);
    }

    return (warningResult == null) ? ValidationResult.OK : warningResult;
  }

}
