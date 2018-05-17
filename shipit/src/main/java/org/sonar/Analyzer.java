/*
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
package org.sonar;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.check.Rule;
import org.sonar.javascript.checks.CheckList;
import org.sonar.javascript.checks.verifier.TestUtils;
import org.sonar.javascript.visitors.JavaScriptVisitorContext;
import org.sonar.plugins.javascript.api.JavaScriptCheck;
import org.sonar.plugins.javascript.api.visitors.FileIssue;
import org.sonar.plugins.javascript.api.visitors.LineIssue;
import org.sonar.plugins.javascript.api.visitors.PreciseIssue;

public class Analyzer {

  static Gson gson = new Gson();

  Map<JavaScriptCheck, Metadata> metadatas = new HashMap<>();

  private static final String PROFILE_JSON = "org/sonar/l10n/javascript/rules/javascript/Sonar_way_profile.json";
  private final List<JavaScriptCheck> checks;

  Analyzer() {
    checks = getChecks();
    System.out.println("Found " + checks.size() + " checks");
  }

  List<Issue> analyze(String input) {
    DefaultInputFile inputFile = new TestInputFileBuilder("moduleKey", "file.js")
      .setContents(input)
      .setCharset(StandardCharsets.UTF_8)
      .build();
    JavaScriptVisitorContext context = TestUtils.createContext(inputFile);
    return checks.stream()
      .flatMap(check -> getActualIssues(check, context))
      .collect(Collectors.toList());
  }


  Stream<Issue> getActualIssues(JavaScriptCheck check, JavaScriptVisitorContext context) {
    return check.scanFile(context).stream().map(this::toIssue);
  }

  List<JavaScriptCheck> getChecks() {
    Set<String> sonarWayRuleKeys = sonarWayRuleKeys();
    List<Class> allRules = CheckList.getChecks();
    return allRules.stream()
      .filter(rule -> isRuleInProfile(rule, sonarWayRuleKeys))
      .map(this::createCheck)
      .collect(Collectors.toList());
  }

  JavaScriptCheck createCheck(Class rule) {
    try {
      JavaScriptCheck instance = (JavaScriptCheck) rule.newInstance();
      Metadata metadata = ruleMetadata(ruleKey(rule));
      metadatas.put(instance, metadata);
      return instance;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Metadata ruleMetadata(String ruleKey) {
    String jsonFile = "org/sonar/l10n/javascript/rules/javascript/" + ruleKey + ".json";
    InputStream resource = Main.class.getClassLoader().getResourceAsStream(jsonFile);
    Metadata ruleJson = gson.fromJson(new InputStreamReader(resource), Metadata.class);
    return ruleJson;
  }

  static boolean isRuleInProfile(Class rule, Set<String> sonarWayRuleKeys) {
    return sonarWayRuleKeys.contains(ruleKey(rule));
  }

  private static String ruleKey(Class rule) {
    Rule annotation = (Rule) rule.getAnnotation(Rule.class);
    return annotation.key();
  }

  static Set<String> sonarWayRuleKeys() {
    String jsonFile = PROFILE_JSON;
    InputStream resource = Main.class.getClassLoader().getResourceAsStream(jsonFile);
    if (resource == null) {
      throw new IllegalStateException(jsonFile);
    }
    Profile profile = gson.fromJson(new InputStreamReader(resource), Profile.class);
    return profile.ruleKeys;
  }

  Issue toIssue(org.sonar.plugins.javascript.api.visitors.Issue jsIssue) {
    Metadata checkMetadata = metadatas.get(jsIssue.check());
    Issue issue = new Issue();
    issue.ruleTitle = checkMetadata.title;
    issue.ruleKey = checkMetadata.sqKey;
    issue.type = checkMetadata.type;
    issue.message = message(jsIssue);
    issue.line = line(jsIssue);
    return issue;
  }

  private static String message(org.sonar.plugins.javascript.api.visitors.Issue jsIssue) {
    if (jsIssue instanceof PreciseIssue) {
      return ((PreciseIssue) jsIssue).primaryLocation().message();
    }
    if (jsIssue instanceof FileIssue) {
      return ((FileIssue) jsIssue).message();
    }
    if (jsIssue instanceof LineIssue) {
      return ((LineIssue) jsIssue).message();
    }
    return null;
  }

  private static Integer line(org.sonar.plugins.javascript.api.visitors.Issue jsIssue) {
    if (jsIssue instanceof PreciseIssue) {
      return ((PreciseIssue) jsIssue).primaryLocation().startLine();
    }
    if (jsIssue instanceof FileIssue) {
      return 1;
    }
    if (jsIssue instanceof LineIssue) {
      return ((LineIssue) jsIssue).line();
    }
    return null;
  }

  static class Profile {
    String name;
    Set<String> ruleKeys;
  }

  enum IssueType {
    CODE_SMELL,
    BUG,
    VULNERABILITY
  }

  static class Metadata {
    String title;
    String sqKey;
    IssueType type;
  }

  static class Issue {
    String ruleTitle;
    String ruleKey;
    IssueType type;
    String message;
    Integer line;
    String org;
    String project;
    String sha;
    String path;
  }


}
