package pl.allegro.tech.hermes.tracker.elasticsearch.consumers;

import com.codahale.metrics.MetricRegistry;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.ClassRule;
import pl.allegro.tech.hermes.api.SentMessageTraceStatus;
import pl.allegro.tech.hermes.metrics.PathsCompiler;
import pl.allegro.tech.hermes.tracker.consumers.AbstractLogRepositoryTest;
import pl.allegro.tech.hermes.tracker.consumers.LogRepository;
import pl.allegro.tech.hermes.tracker.elasticsearch.ElasticsearchResource;
import pl.allegro.tech.hermes.tracker.elasticsearch.LogSchemaAware;
import pl.allegro.tech.hermes.tracker.elasticsearch.SchemaManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.awaitility.Duration.ONE_MINUTE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;

public class ConsumersElasticsearchLogRepositoryTest extends AbstractLogRepositoryTest implements LogSchemaAware {

    private static final String CLUSTER_NAME = "primary";

    private static final Clock clock = Clock.fixed(LocalDate.of(2000, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneId.systemDefault());
    private static final ConsumersIndexFactory indexFactory = new ConsumersDailyIndexFactory(clock);

    @ClassRule
    public static ElasticsearchResource elasticsearch = new ElasticsearchResource(indexFactory);

    @Override
    protected LogRepository createLogRepository() {
        return new ConsumersElasticsearchLogRepository.Builder(elasticsearch.client(), new PathsCompiler("localhost"), new MetricRegistry())
                .withIndexFactory(indexFactory)
                .build();
    }

    @Override
    protected void awaitUntilMessageIsPersisted(String topic, String subscription, String id, SentMessageTraceStatus status) throws Exception {
        await().atMost(ONE_MINUTE).until(() -> {
            SearchResponse response = elasticsearch.client().prepareSearch(indexFactory.createIndex())
                    .setTypes(SchemaManager.SENT_TYPE)
                    .setQuery(boolQuery()
                            .should(matchQuery(TOPIC_NAME, topic))
                            .should(matchQuery(SUBSCRIPTION, subscription))
                            .should(matchQuery(MESSAGE_ID, id))
                            .should(matchQuery(STATUS, status.toString()))
                            .should(matchQuery(CLUSTER, CLUSTER_NAME)))
                    .execute().get();
            return response.getHits().getTotalHits() == 1;
        });
    }
}