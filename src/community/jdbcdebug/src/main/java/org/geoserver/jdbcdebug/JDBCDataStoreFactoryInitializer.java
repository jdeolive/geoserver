package org.geoserver.jdbcdebug;

import org.geoserver.data.DataStoreFactoryInitializer;
import org.geotools.jdbc.JDBCDataStoreFactory;

public class JDBCDataStoreFactoryInitializer extends DataStoreFactoryInitializer<JDBCDataStoreFactory> {

    protected JDBCDataStoreFactoryInitializer() {
        super(JDBCDataStoreFactory.class);
    }

    @Override
    public void initialize(JDBCDataStoreFactory factory) {
        factory.setDataSourceFactory(new JDBCDebugDataSourceFactory(factory.getDataSourceFactory()));
    }

}
