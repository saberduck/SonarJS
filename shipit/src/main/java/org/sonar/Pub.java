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

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Pub {

  static Gson gson = new Gson();

  public static void main(String[] args) throws Exception {
    ProjectTopicName topicName = ProjectTopicName.of(Sub.PROJECT_ID, "massimoTopic");
    Publisher publisher = null;
    List<ApiFuture<String>> messageIdFutures = new ArrayList<>();

    try {
      // Create a publisher instance with default settings bound to the topic
      publisher = Publisher.newBuilder(topicName).build();
      Publisher finalPublisher = publisher;

      Files.walk(Paths.get(args[0]))
        .filter(p -> p.toString().endsWith(".js"))
        .map(Pub::createMessage)
        .map(m -> gson.toJson(m))
        .forEach(message -> {
          PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(message)).build();
          ApiFuture<String> messageIdFuture = finalPublisher.publish(pubsubMessage);
          messageIdFutures.add(messageIdFuture);
        });
    } finally {
      // wait on any pending publish requests.
      List<String> messageIds = ApiFutures.allAsList(messageIdFutures).get();

      for (String messageId : messageIds) {
        System.out.println("published with message ID: " + messageId);
      }

      if (publisher != null) {
        // When finished with the publisher, shutdown to free up resources.
        publisher.shutdown();
      }
    }
  }

  private static Message createMessage(Path p) {
    Message msg = new Message();
    try {
      msg.source = new String(Files.readAllBytes(p));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return msg;
  }

  static class Message {
    String source;
  }
}
