package org.geoserver.jdbcdebug;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerDataDirectory;
import org.geoserver.config.GeoServerInitializer;
import org.geoserver.logging.LoggingUtils;
import org.geotools.data.DataUtilities;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class JDBCDebugInitializer implements GeoServerInitializer, ApplicationContextAware {

    ApplicationContext appContext;
    GeoServerDataDirectory dataDir;

    public JDBCDebugInitializer(GeoServerDataDirectory dataDir) {
        this.dataDir = dataDir;
    }

    @Override
    public void initialize(GeoServer geoServer) throws Exception {
        //configure logging
        File logConfigDir = dataDir.findOrCreateDir("logs");

        File logDir = null;

        //use the same log directory as the main log file
        String mainLogLocation = 
            LoggingUtils.getLogFileLocation(geoServer.getLogging().getLocation());
        if (mainLogLocation != null) {
            //throw the jdbc parallel to the main log
            logDir = new File(mainLogLocation).getParentFile();
            if (logDir != null && logDir.exists()) {
                logDir = new File(logDir, "jdbc");
                logDir.mkdir();
            }
        }

        if (logDir == null || !logDir.exists()) {
            //fall back on default
            logDir = new File(logConfigDir, "jdbc");
            logDir.mkdir();
        }

        //look for the properties file to configure
        File logPropsFile = new File(logConfigDir, "jdbclog.properties");
        if (!logPropsFile.exists()) {
            FileUtils.copyURLToFile(getClass().getResource("jdbclog.properties"), logPropsFile);
        }

        //configure the log4jdbc logging
        System.setProperty("jdbcdebug.logdir", logDir.getAbsolutePath());
        PropertyConfigurator.configure(DataUtilities.fileToURL(logPropsFile));
    }

    @Override
    public void setApplicationContext(ApplicationContext appContext) throws BeansException {
        this.appContext = appContext;
    }
}
