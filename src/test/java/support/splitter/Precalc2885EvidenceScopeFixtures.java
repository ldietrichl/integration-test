package support.splitter;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import config.services.container.KubernetesTunnelSettings;
import infrastructure.kubernetes.OcServiceLogs;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.qameta.allure.*;
import io.qameta.allure.model.TestResult;

import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Reusable offline fixtures; test cases remain in their ticket package. */
public abstract class Precalc2885EvidenceScopeFixtures {

    public static final class Fixture implements AutoCloseable {
        public final AllureLifecycle previous = Allure.getLifecycle();
        public final Precalc2885ReportingFixtures.Writer writer = new Precalc2885ReportingFixtures.Writer();
        public final AllureLifecycle lifecycle = new AllureLifecycle(writer);
        public final String id = UUID.randomUUID().toString();
        public final KubernetesClient api = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        public final KubernetesTunnelSettings settings;
        public final Object evidence;
        public final Class<?> type;
        public final JsonNode pod;
        public Fixture() throws Exception {
            Properties props = new Properties();
            props.setProperty("kubernetes.namespace", "test");
            props.setProperty("kubernetes.logs.enabled", "true");
            props.setProperty("kubernetes.logs.container", "app");
            props.setProperty("kubernetes.logs.max.bytes", "4096");
            props.setProperty("kubernetes.logs.tail.lines", "2000");
            props.setProperty("kubernetes.logs.clock-skew.seconds", "0");
            settings = KubernetesTunnelSettings.fromProperties(props);
            var events = mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
            var eventOperations = api.events().v1().events();
            doReturn(events).when(eventOperations).inNamespace("test");
            when(events.list()).thenReturn(new io.fabric8.kubernetes.api.model.events.v1.EventList());
            var quotas = mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
            var quotaOperations = api.resourceQuotas();
            doReturn(quotas).when(quotaOperations).inNamespace("test");
            when(quotas.list()).thenReturn(new ResourceQuotaList());
            var pods = mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
            var podOperations = api.pods();
            doReturn(pods).when(podOperations).inNamespace("test");
            var podResource = mock(io.fabric8.kubernetes.client.dsl.PodResource.class, RETURNS_DEEP_STUBS);
            doReturn(podResource).when(pods).withName("pod");
            Pod nativePod = new PodBuilder().withNewMetadata().withName("pod").withUid("uid").endMetadata()
                    .withNewStatus().addNewContainerStatus().withName("app").withReady(true).withRestartCount(0).endContainerStatus().endStatus().build();
            when(api.pods().inNamespace("test").withName("pod").get()).thenReturn(nativePod);
            when(api.pods().inNamespace("test").withName("pod").inContainer("app").limitBytes(4096)
                    .sinceTime(anyString()).tailingLines(2000).getLog()).thenReturn("current scenario log\n");
            logs(Instant.now().minusSeconds(1) + " current operation log\n");
            pod = dto.splitter.precalc.ReactionsPrecalcRequests.JSON.valueToTree(nativePod);
            type = Class.forName("infrastructure.kubernetes.WorkloadEvidence");
            var constructor = type.getDeclaredConstructor(KubernetesTunnelSettings.class, KubernetesClient.class);
            constructor.setAccessible(true); evidence = constructor.newInstance(settings, api);
            Allure.setLifecycle(lifecycle);
            lifecycle.scheduleTestCase(new TestResult().setUuid(id).setName("evidence scope")); lifecycle.startTestCase(id);
        }
        public void begin(Instant start) throws Exception { invoke("beginScenario", new Class<?>[]{Instant.class}, start); }
        public void logs(String text) {
            when(api.pods().inNamespace("test").withName("pod").inContainer("app").usingTimestamps().limitBytes(4096)
                    .sinceTime(anyString()).tailingLines(2000).getLog()).thenReturn(text);
        }
        public OcServiceLogs.Capture window(Instant start, Instant end) {
            return OcServiceLogs.captureNativeWindow(settings, api, new OcServiceLogs.Target("pod", "uid", "app", 8080, 0), "test", start, end);
        }
        public void capture(String phase) throws Exception {
            invoke("capture", new Class<?>[]{String.class,JsonNode.class,List.class,List.class,Instant.class},
                    phase, null, List.of(), List.of(pod), Instant.now());
        }
        public void finish(boolean failed) throws Exception {
            invoke("finishScenario", new Class<?>[]{String.class,JsonNode.class,List.class,List.class,Instant.class,boolean.class},
                    "after scenario", null, List.of(), List.of(pod), Instant.now(), failed);
        }
        public void invoke(String name, Class<?>[] parameters, Object... args) throws Exception {
            var method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); method.invoke(evidence,args);
        }
        public void write() { lifecycle.updateTestCase(id, result -> writer.result = result); }
        @Override public void close() {
            lifecycle.stopTestCase(id); lifecycle.writeTestCase(id); Allure.setLifecycle(previous);
        }
    }
}
