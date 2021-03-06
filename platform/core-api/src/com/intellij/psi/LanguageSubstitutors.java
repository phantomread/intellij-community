/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public final class LanguageSubstitutors extends LanguageExtension<LanguageSubstitutor> {
  public static final LanguageSubstitutors INSTANCE = new LanguageSubstitutors();
  private static final Logger LOG = Logger.getInstance(LanguageSubstitutors.class);
  private static final Key<Language> SUBSTITUTED_LANG_KEY = Key.create("SUBSTITUTED_LANG_KEY");

  private boolean myReparsingInProgress;

  private LanguageSubstitutors() {
    super("com.intellij.lang.substitutor");
  }

  @NotNull
  public Language substituteLanguage(@NotNull Language lang, @NotNull VirtualFile file, @NotNull Project project) {
    for (LanguageSubstitutor substitutor : forKey(lang)) {
      Language language = substitutor.getLanguage(file, project);
      if (language != null) {
        processLanguageSubstitution(file, lang, language);
        return language;
      }
    }
    return lang;
  }


  private void processLanguageSubstitution(@NotNull final VirtualFile file,
                                           @NotNull Language originalLang,
                                           @NotNull final Language substitutedLang) {
    Language prevSubstitutedLang = SUBSTITUTED_LANG_KEY.get(file);
    final Language prevLang = ObjectUtils.notNull(prevSubstitutedLang, originalLang);
    if (!substitutedLang.equals(prevLang)) {
      if (ApplicationManager.getApplication().isDispatchThread() && myReparsingInProgress) {
        return; // avoid recursive reparsing
      }
      if (file.replace(SUBSTITUTED_LANG_KEY, prevSubstitutedLang, substitutedLang)) {
        invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            LOG.info("Reparsing " + file.getPath() + " because of language substitution " +
                     prevLang.getID() + "->" + substitutedLang.getID());
            myReparsingInProgress = true;
            FileContentUtilCore.reparseFiles(file);
            myReparsingInProgress = false;
          }
        }, ModalityState.defaultModalityState());
      }
    }
  }

  private static void invokeLaterIfNeeded(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable, modalityState);
    }
  }
}
