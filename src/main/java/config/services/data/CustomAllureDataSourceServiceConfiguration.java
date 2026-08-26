package config.services.data;

import io.perfeccionista.framework.datasource.DataSourceHolder;
import io.perfeccionista.framework.datasource.DefaultDataSourceServiceConfiguration;
import io.perfeccionista.framework.datasource.Stash;
import io.perfeccionista.framework.datasource.StashWithAllure;

import java.util.stream.Stream;

public class CustomAllureDataSourceServiceConfiguration extends DefaultDataSourceServiceConfiguration {
    @Override
    public Stream<DataSourceHolder<?>> dataSourceHolders() {
        return Stream.of(
                DataSourceHolder.of(Stash.class, StashWithAllure.class)
        );
    }
}
