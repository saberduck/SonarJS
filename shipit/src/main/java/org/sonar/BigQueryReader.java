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

import com.google.cloud.bigquery.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BigQueryReader {

  static final String PROJECT_ID = "project-test-199515";

  private static final Analyzer analyzer = new Analyzer();
  private static final List<InsertAllRequest.RowToInsert> rows = new ArrayList<>();
  private static Table resultTable;

  public static void main(String[] args) throws Exception {
    args = new String[] { "1", "0"};

    int totalWorkers = Integer.parseInt(args[0]);
    int workerId = Integer.parseInt(args[1]);

    int insertBatchSize = 1000;

    BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
    Table issuesTable = bigquery.getTable("github_us", "sample_js_files_and_contents");
    resultTable = bigquery.getTable("github_eu", "issues_copy_dinesh");

    TableResult result = issuesTable.list();
    int total = 0;
    int processed = 0;
    for (FieldValueList values: result.iterateAll()) {
      if (total % totalWorkers == workerId) {
        System.out.println("Processed so far: " + processed + " out of " + total + " seen, rows buffer: " + rows.size());
        process(values);

        processed++;

        if (rows.size() > insertBatchSize) {
          persistRows();
        }
      }
      total++;
    }

  }

  private static void persistRows() {
    try {
      InsertAllResponse response = resultTable.insert(rows);
      if (response.hasErrors()) {
        System.out.println("Error while persisting the batch! " + response.toString());
      }

      rows.clear();
    } catch (RuntimeException e) {
      System.err.println("Got error while persisting: " + e);
      e.printStackTrace();
    }
  }

  private static void process(FieldValueList values) {
    try {
      String repo_name = values.get(0).getStringValue();
      String ref = values.get(1).getStringValue();
      String path = values.get(2).getStringValue();
      String content = values.get(3).getStringValue();

      String orga = repo_name;
      String project = repo_name;

      int slash = repo_name.indexOf('/');
      if (slash != -1) {
        orga = repo_name.substring(0, slash);
        project = repo_name.substring(slash + 1);
      }

      System.out.println("  repo_name: " + repo_name + ", orga: " + orga + ", project: " + project + ", ref: " + ref + ", path: " + path + ", content length: " + content.length());

      List<Analyzer.Issue> issues = analyzer.analyze(content);

      System.out.println("    " + issues.size() + " issues");
      for (Analyzer.Issue issue: issues) {
        String ruleKey = issue.ruleKey;
        String ruleTitle = issue.ruleTitle;
        String issueMessage = issue.message;
        int lineNumber = issue.line;
        String issueType = issueTypeToString(issue.type);

        System.out.println("      - (" + ruleKey + ") " + issueMessage + " at line " + lineNumber + " type: " + issueType);

        Map<String, String> row = new HashMap<>();
        row.put("RULE_KEY", ruleKey);
        row.put("ISSUE_TYPE", issueType);
        row.put("COMMIT", ref);
        row.put("FILE_PATH", path);
        row.put("GITHUB_ORG", orga);
        row.put("GITHUB_PRJ", project);
        row.put("LINE_NUMBER", "" + lineNumber);
        row.put("ISSUE_MESSAGE", issueMessage);
        row.put("RULE_TITLE", ruleTitle);

        rows.add(InsertAllRequest.RowToInsert.of(row));
      }
    } catch (RuntimeException e) {
      System.err.println("Got error while processing: " + e);
      e.printStackTrace();
    }
  }

  private static String issueTypeToString(Analyzer.IssueType issueType) {
    switch (issueType) {
      case CODE_SMELL:
        return "Code smell";
      case BUG:
        return "Bug";
      case VULNERABILITY:
        return "Vulnerability";
      default:
        return "Unknown";
    }
  }

}
