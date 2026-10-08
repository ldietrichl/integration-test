package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;

final class WorkloadJson {
    static final ObjectMapper JSON = new ObjectMapper();
    private WorkloadJson() { }
}
