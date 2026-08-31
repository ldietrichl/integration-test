package constants;

public class Endpoints {
    public static class ExperimentsV1 {
        public static final String V1_EXPERIMENTS = "/api/v1/experiments";
        public static final String V1_EXPERIMENTS_ID = "/api/v1/experiments/%s";
        public static final String V1_EXPERIMENTS_ID_STATUS = "/api/v1/experiments/%s/status";
        public static final String V1_EXPERIMENTS_LIST_RUNNING = "/api/v1/experiments/list/running";
        public static final String V1_EXPERIMENTS_SPLITS_LIST_RUNNING = "/api/v1/experiments/splits/list/running";
        public static final String V1_EXPERIMENTS_EVICT_CASH = "/api/v1/experiments/evict-cash";
    }

    public static class LayersV1 {
        public static final String V1_LAYERS = "/api/v1/experiments/layers";
        public static final String V1_LAYERS_ID = "/api/v1/experiments/layers/{id}";
        public static final String V1_LAYERS_REGISTRY = "/api/v1/experiments/layers/registry";
    }

    public static class ExperimentsV2 {
        public static final String V2_EXPERIMENTS = "/api/v2/experiments";
        public static final String V2_EXPERIMENTS_ID = "/api/v2/experiments/{id}";
        public static final String V2_EXPERIMENTS_REGISTRY = "/api/v2/experiments/registry";
        public static final String V2_EXPERIMENTS_COMPLETE_ACTION = "/api/v2/experiments/complete-action";
    }

    public static class LayersV2 {
        public static final String V2_LAYERS = "/api/v2/experiments/layers";
        public static final String V2_LAYERS_ID = "/api/v2/experiments/layers/{id}";
        public static final String V2_LAYERS_ID_START = "/api/v2/experiments/layers/{id}/start";
        public static final String V2_LAYERS_ID_STOP = "/api/v2/experiments/layers/{id}/stop";
        public static final String V2_LAYERS_REGISTRY = "/api/v2/experiments/layers/registry";
    }

    public static class DictionariesV2 {
        public static final String V2_EXPRESSION_PARAMETER_DICT = "/api/v2/dictionaries/expression-parameter-dict";
    }

    public static class Configurations {
        public static final String V2_CONF_ACTION_REQUEST = "/api/v2/configurations/action-request";
        public static final String V2_CONF_GENERATE_SPLITTING_CONFIG = "/api/v2/configurations/generate-splitting-config";
    }

    public static class Splitter {
        private static final String SPLITTER_VINTAGE_BASE = "/api/v1/splitter-vintage";

        public static final String SPLITTER_CONFIG = SplitterEndpointPaths.mapperConfig();
        public static final String SPLITTER_SPLIT = SplitterEndpointPaths.mapperSplit();
        public static final String SPLITTER_PRECALCULATE = SplitterEndpointPaths.mapperPrecalculate();
        public static final String SPLITTER_VERSION = SplitterEndpointPaths.mapperVersion();

        public static final String SPLITTER_VINTAGE_CONFIG = SPLITTER_VINTAGE_BASE + "/config";
        public static final String SPLITTER_VINTAGE_SPLIT = SPLITTER_VINTAGE_BASE + "/split";
        public static final String SPLITTER_VINTAGE_PRECALCULATE = SPLITTER_VINTAGE_BASE + "/pre-calculate";
        public static final String SPLITTER_VINTAGE_VERSION = SPLITTER_VINTAGE_BASE + "/version";

        public static final String SPLITTER_REACTIONS_CONFIG = SplitterEndpointPaths.reactionsConfig();
        public static final String SPLITTER_REACTIONS_SPLIT = SplitterEndpointPaths.reactionsSplit();
        public static final String SPLITTER_REACTIONS_PRECALCULATE = SplitterEndpointPaths.reactionsPrecalculate();
        public static final String SPLITTER_REACTIONS_VERSION = SplitterEndpointPaths.reactionsVersion();
    }

    /** REST API data-operator-service v2. */
    public static class DataOperatorV2 {
        private static final String BASE = "/api/v2/data-operator";

        public static final String SPLITTING_OBJECTS = BASE + "/splitting-objects";
        public static final String SPLITTING_OBJECTS_IDS = BASE + "/splitting-objects-ids";
        public static final String SPLITTING_OBJECT_IDS = SPLITTING_OBJECTS_IDS;

        public static final String OBJECT_BY_ID = BASE + "/object/{splittingPoint}/{id}";
        public static final String SO_FIELD_VALUES_DICTS = BASE + "/so-field-values-dicts";
        public static final String SO_DICT_BY_PARAMS = BASE + "/so-dict-by-params";
        public static final String SPLITTING_OBJECTS_LINKS = BASE + "/splitting-objects-links";
    }

    /** Compatibility aliases for tests that use the older naming convention. */
    public static class DataOperator {
        public static final String V2_SPLITTING_OBJECTS = DataOperatorV2.SPLITTING_OBJECTS;
        public static final String V2_SPLITTING_OBJECT_IDS = DataOperatorV2.SPLITTING_OBJECTS_IDS;
    }

    /** REST API pilot-service v2. */
    public static class Pilots {
        private static final String BASE = "/api/v2/pilots";

        public static final String PILOTS = BASE;
        public static final String PILOT_BY_ID = BASE + "/{id}";
        public static final String PILOT_STATUS = BASE + "/{id}/status";
        public static final String PILOT_REGISTRY = BASE + "/registry";
        public static final String PILOT_SHORT_REGISTRY = BASE + "/short-registry";
        public static final String PILOT_LINKED_EXPERIMENTS = BASE + "/{id}/linked-experiments";
        public static final String PILOT_LINKED_CAMPAIGNS = BASE + "/{id}/linked-campaigns";
        public static final String PILOT_VALIDATE_CAMPAIGNS = BASE + "/validate-pilot-campaigns-list";
    }
}
