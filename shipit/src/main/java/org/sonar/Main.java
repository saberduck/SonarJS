package org.sonar;/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.javascript.checks.AlertUseCheck;
import org.sonar.javascript.checks.verifier.TestUtils;
import org.sonar.javascript.visitors.JavaScriptVisitorContext;
import org.sonar.plugins.javascript.api.JavaScriptCheck;
import org.sonar.plugins.javascript.api.visitors.Issue;
import org.sonar.plugins.javascript.api.visitors.PreciseIssue;

public class Main {

  static Gson gson = new Gson();

  public static void main(String[] args) throws Exception {
    System.out.println("Running analysis");

    String input = readInput();
    DefaultInputFile inputFile = new TestInputFileBuilder("moduleKey", "file.js")
      .setContents(input)
      .setCharset(StandardCharsets.UTF_8)
      .build();
    JavaScriptVisitorContext context = TestUtils.createContext(inputFile);
    List<Issue> actualIssues = getActualIssues(new AlertUseCheck(), context);
    saveIssues(actualIssues);
  }

  private static void saveIssues(List<Issue> actualIssues) throws IOException {
    actualIssues.forEach(i -> {
      if (i instanceof PreciseIssue) {
        PreciseIssue preciseIssue = (PreciseIssue) i;
        System.out.println(preciseIssue.primaryLocation().startLine());
      }
    });
  }

  private static String readInput() throws IOException {
    return new String(Files.readAllBytes(Paths.get("input.js")));
  }

  public static List<Issue> getActualIssues(JavaScriptCheck check, JavaScriptVisitorContext context) {
    return check.scanFile(context);
  }
}
