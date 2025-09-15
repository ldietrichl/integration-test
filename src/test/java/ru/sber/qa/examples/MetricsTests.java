package ru.sber.qa.examples;

import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.matchers.JsonMatchers;
import ru.sber.qa.service.MetricService;
import ru.sber.qa.utils.MetricsTimeRange;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.sber.qa.config.services.CustomMetricsEnvironmentsConfiguration.getTestRequestSpecification;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class MetricsTests {

    List<String> finalQuery = new ArrayList<>();
    Map<String, String> filterQuery = new HashMap<>();

    @Test
    void getMetricsFromStorageSingleQueryList(MetricService metricService) {
        finalQuery.add("audit_send_event_total");

        metricService.metricClient("all")
                .getMetrics(finalQuery)
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryListAndSingleFilterMap() {
        MetricService metricService = Environment.getForCurrentThread().getService(MetricService.class);

        finalQuery.add("audit_send_event_total");
        filterQuery.put("app", "ecm-order-processor");

        metricService.metricClient("all")
                .getMetrics(finalQuery, filterQuery)
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryListAndStartEndTimeFilterMap(Environment environment) {
        MetricService metricService = environment.getService(MetricService.class);

        finalQuery.add("audit_send_event_total");
        filterQuery.put("start", MetricsTimeRange.setRange(Duration.ofHours(3)));
        filterQuery.put("end", MetricsTimeRange.setRange(Duration.ofMinutes(1)));

        metricService.metricClient("all")
                .getMetrics(finalQuery, filterQuery)
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryListAndStartEndTime(MetricService metricService) {
        finalQuery.add("audit_send_event_total");

        metricService.metricClient("all")
                .getMetrics(finalQuery, Duration.ofHours(4), Duration.ofMinutes(1))
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryListAndStartTime(MetricService metricService) {
        finalQuery.add("audit_send_event_total");

        metricService.metricClient("all")
                .getMetrics(finalQuery, Duration.ofHours(4))
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryListAndStringKeyStringValueFilter(MetricService metricService) {
        finalQuery.add("audit_send_event_total");

        metricService.metricClient("all")
                .getMetrics(finalQuery, "app", "ecm-order-processor")
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryListAndStringKeyStringValueFilterAndStartEndTime(MetricService metricService) {
        finalQuery.add("audit_send_event_total");

        metricService.metricClient("all")
                .getMetrics(finalQuery, "app", "ecm-order-processor",
                        Duration.ofHours(4), Duration.ofMinutes(1))
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryListAndStringKeyStringValueFilterAndStartTime(MetricService metricService) {
        finalQuery.add("audit_send_event_total");

        metricService.metricClient("all")
                .getMetrics(finalQuery, "app", "ecm-order-processor",
                        Duration.ofHours(4))
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryString(MetricService metricService) {
        metricService.metricClient("all")
                .getMetrics("audit_send_event_total")
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryStringAndCustomSpec(MetricService metricService) {

        metricService.metricClient("all")
                .getMetrics("audit_send_event_total", getTestRequestSpecification())
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryListAndSingleFilterMapAndCustomSpec(MetricService metricService) {
        finalQuery.add("audit_send_event_total");

        filterQuery.put("app", "ecm-order-processor");
        metricService.metricClient("all")
                .getMetrics(finalQuery, filterQuery, getTestRequestSpecification())
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryListAndSingleFilterMap(MetricService metricService) {
        finalQuery.add("audit_send_event_total");
        finalQuery.add("audit_kafka_out_seconds_count");

        metricService.metricClient("all")
                .getMetrics(finalQuery)
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }

    @Test
    void getMetricsFromStorageSingleQueryListAndStartEndTimeFilterMapAndCustomSpec(Environment environment) {
        MetricService metricService = environment.getService(MetricService.class);

        finalQuery.add("audit_send_event_total");
        filterQuery.put("start", MetricsTimeRange.setRange(Duration.ofHours(3)));
        filterQuery.put("end", MetricsTimeRange.setRange(Duration.ofMinutes(1)));

        metricService.metricClient("all")
                .getMetrics(finalQuery, filterQuery, getTestRequestSpecification())
                .filter("data.result")
                .should(JsonMatchers.haveJsonValueEqualTo("find{it.metric.__name__}.metric.__name__",
                        "audit_send_event_total"));
    }
}
