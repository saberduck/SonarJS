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

import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** This class contains snippets for the {@link Subscriber} interface. */
public class Sub {

  static final String PROJECT_ID = "project-test-199515";
  static final String SUBSCRIPTION_ID = "msub1";
  private static int counter = 0;

  static List<ReceivedMessage> createSubscriberWithSyncPull(
    String projectId, String subscriptionId, int numOfMessages) throws Exception {
    // [START subscriber_sync_pull]
    SubscriberStubSettings subscriberStubSettings =
      SubscriberStubSettings.newBuilder().build();
    try (SubscriberStub subscriber = GrpcSubscriberStub.create(subscriberStubSettings)) {
      // String projectId = "my-project-id";
      // String subscriptionId = "my-subscription-id";
      // int numOfMessages = 10;   // max number of messages to be pulled
      String subscriptionName = ProjectSubscriptionName.format(projectId, subscriptionId);
      PullRequest pullRequest =
        PullRequest.newBuilder()
          .setMaxMessages(numOfMessages)
          .setReturnImmediately(false) // return immediately if messages are not available
          .setSubscription(subscriptionName)
          .build();

      // use pullCallable().futureCall to asynchronously perform this operation
      PullResponse pullResponse = subscriber.pullCallable().call(pullRequest);
      List<String> ackIds = new ArrayList<>();
      for (ReceivedMessage message : pullResponse.getReceivedMessagesList()) {
        // handle received message
        // ...
        ++counter;
        System.out.println(counter + " " + message.getMessage().getData().toStringUtf8());
        ackIds.add(message.getAckId());
      }
      if (ackIds.isEmpty()) {
        return Collections.emptyList();
      }
      // acknowledge received messages
      AcknowledgeRequest acknowledgeRequest =
        AcknowledgeRequest.newBuilder()
          .setSubscription(subscriptionName)
          .addAllAckIds(ackIds)
          .build();
      // use acknowledgeCallable().futureCall to asynchronously perform this operation
      subscriber.acknowledgeCallable().call(acknowledgeRequest);
      return pullResponse.getReceivedMessagesList();
    }
    // [END subscriber_sync_pull]
  }

  public static void main(String[] args) throws Exception {
    while (true) {
      List<ReceivedMessage> msub1 = createSubscriberWithSyncPull(PROJECT_ID, SUBSCRIPTION_ID, 1);
    }
  }
}
