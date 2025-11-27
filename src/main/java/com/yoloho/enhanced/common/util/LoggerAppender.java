package com.yoloho.enhanced.common.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.config.Property;

import java.util.List;

public interface LoggerAppender {

    boolean getAdditivity();

    Level getLogLevel();

    String getLoggerName();

    String getIncludeLocation();

    Property[] getProperties();

    Filter getFilter();

    List<String> getLogger();

}