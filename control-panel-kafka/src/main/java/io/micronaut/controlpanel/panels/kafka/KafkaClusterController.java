/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.controlpanel.panels.kafka;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.apache.kafka.clients.admin.AdminClient;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.Broker;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ConsumerGroupDetail;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ConsumerGroupSummary;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.MessagePage;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.Overview;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.Section;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.TopicDetail;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.TopicPage;

/**
 * GET-only JSON endpoints used by the Kafka Cluster control panel.
 */
@Controller(ControlPanelSecurityPaths.KAFKA)
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
@Requires(beans = AdminClient.class)
@Requires(property = KafkaClusterControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public final class KafkaClusterController {

    static final String PATH = ControlPanelSecurityPaths.KAFKA_PATH;

    private final KafkaClusterService service;

    KafkaClusterController(KafkaClusterService service) {
        this.service = service;
    }

    @Get("/summary")
    public Section<Overview> summary() {
        return service.overview();
    }

    @Get("/brokers")
    public Section<List<Broker>> brokers() {
        return service.brokers();
    }

    @Get("/topics{?search,includeInternal,start,length}")
    public Section<TopicPage> topics(@Nullable @QueryValue String search,
                                     @QueryValue(defaultValue = "false") boolean includeInternal,
                                     @QueryValue(defaultValue = "0") int start,
                                     @QueryValue(defaultValue = "25") int length) {
        return service.topics(search, includeInternal, start, length);
    }

    @Get("/topics/{topic}")
    public Section<TopicDetail> topic(String topic) {
        return service.topic(topic);
    }

    @Get("/consumer-groups")
    public Section<List<ConsumerGroupSummary>> consumerGroups() {
        return service.consumerGroups();
    }

    @Get("/consumer-groups/{groupId}")
    public Section<ConsumerGroupDetail> consumerGroup(String groupId) {
        return service.consumerGroup(groupId);
    }

    @Get("/messages{?topic,partition,mode,offset,timestamp,limit}")
    public Section<MessagePage> messages(@QueryValue String topic,
                                         @QueryValue int partition,
                                         @QueryValue(defaultValue = "beginning") String mode,
                                         @Nullable @QueryValue Long offset,
                                         @Nullable @QueryValue Long timestamp,
                                         @QueryValue(defaultValue = "25") int limit) {
        return service.messages(topic, partition, mode, offset, timestamp, limit);
    }
}
