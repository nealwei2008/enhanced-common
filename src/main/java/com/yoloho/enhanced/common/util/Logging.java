package com.yoloho.enhanced.common.util;

import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class Logging {
    private final static Logger logger = LoggerFactory.getLogger(Logging.class.getSimpleName());
    private static String defaultLevel = "INFO";
    private static String defaultMaxSize = "50 MB";
    private static boolean inited = false;
    
    private static boolean hasLog4j() {
        try {
            Class<?> clazz = Class.forName("org.apache.logging.log4j.LogManager");
            if (clazz != null) {
                return true;
            }
        } catch (ClassNotFoundException e) {
        }
        logger.error("no log4j found");
        return false;
    }
    
    /**
     * 设置默认的日志级别
     * 
     * @param level
     */
    public static void setLoggingLevel(String level) {
        if (!hasLog4j()) return;
        defaultLevel = level;
        if (!inited) return;
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        config.getRootLogger().setLevel(Level.toLevel(level));
        ctx.updateLoggers();
    }
    
    /**
     * 设置默认的日志级别
     * 
     * @param level
     */
    public static void setLoggingLevel(Level level) {
        if (!hasLog4j()) return;
        setLoggingLevel(level.name());
    }
    
    /**
     * 设置最大的单文件大小
     * <p>
     * 需要在initLogging前调用
     * 
     * @param size
     */
    public static void setMaxFileSize(String size) {
        defaultMaxSize = size;
    }
    
    public static void initLogging() {
        initLogging(true);
    }
    
    public static void initLogging(boolean console) {
        initLogging(console, true);
    }
    
    private static TriggeringPolicy getPolicy() {
        //时间规则
        /*TimeBasedTriggeringPolicy timeBasedTriggeringPolicy = null;
        try {
            timeBasedTriggeringPolicy = TimeBasedTriggeringPolicy.newBuilder()
                    .withModulate(true)
                    .withInterval(1)
                    .withMaxRandomDelay(300)
                    .build();
        } catch (Exception e) {
            //old version
            timeBasedTriggeringPolicy = TimeBasedTriggeringPolicy.createPolicy("1", "true");
        }*/
        //大小规则
        SizeBasedTriggeringPolicy sizeBasedTriggeringPolicy = SizeBasedTriggeringPolicy.createPolicy(defaultMaxSize);
        /*CompositeTriggeringPolicy compositeTriggeringPolicy = CompositeTriggeringPolicy
                .createPolicy(timeBasedTriggeringPolicy, sizeBasedTriggeringPolicy);*/
        return sizeBasedTriggeringPolicy;
        
    }

    public static void initLogging(boolean console, boolean file) {
        if ((!console & !file) || !hasLog4j()) return;
        //for rocketmq
        System.setProperty("rocketmq.client.log.loadconfig", "false");
        // log必须最先开始判断要不要初始化
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        List<AppenderRef> appenderRefs = Lists.newArrayList();
        String configLocation = config.getConfigurationSource().getLocation();
        if (StringUtils.isNotEmpty(configLocation) && !configLocation.contains("spring-boot") && !configLocation.contains("rocketmq_client")) {
            // 这里规避log，但如果log4j2.xml位于spring-boot中，忽略
            logger.info("found log4j2 config file: {}, skip init", config.getConfigurationSource().getLocation());
            return;
        }
        // 先清除所有旧logger
        Iterator<Entry<String, Appender>> it = config.getRootLogger().getAppenders().entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, Appender> entry = it.next();
            config.getRootLogger().removeAppender(entry.getKey());
            entry.getValue().stop();
        }
        try {
            config.getRootLogger().getAppenderRefs().clear();
        } catch (Exception e) {
        }
        /**
         * 设置默认level为info 这里未来可以设置几个可选的输入覆盖方式
         */
        config.getRootLogger().setLevel(Level.toLevel(defaultLevel));
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%d %highlight{%-5p} %style{%c{1.}}{cyan} [%t] %m%n")
                .withConfiguration(config).build();
        DefaultRolloverStrategy defaultRolloverStrategy = null;
        try {
            defaultRolloverStrategy = DefaultRolloverStrategy.newBuilder()
                    .withConfig(config)
                    .withMax("10")
                    .build();
        } catch (Exception e) {
            //old version, 可能版本小于 2.8
            logger.error("DefaultRolloverStrategy create failed ", e);
            return;
        }
        // console
        if (console) {

            @SuppressWarnings(value = {"rawtypes", "unchecked"})
            ConsoleAppender.Builder<?> builder = ConsoleAppender.<ConsoleAppender.Builder> newBuilder();
            Appender appenderConsole = builder.setName("STDOUT").setImmediateFlush(true)
                    .setBufferedIo(true).setBufferSize(1024).setLayout(layout).build();
            appenderConsole.start();
            config.getRootLogger().addAppender(appenderConsole, null, null);
            //for log4j < 2.8, just ignore currently
            try {
                AppenderRef refConsole = AppenderRef.createAppenderRef(appenderConsole.getName(), null, null);
                appenderRefs.add(refConsole);
                config.getRootLogger().getAppenderRefs().add(refConsole);
            } catch (Exception e) {
            }
        }
        // NormalRollingFile
        if (file) {
            ThresholdFilter thresholdFilter = ThresholdFilter.createFilter(Level.ERROR, Result.DENY, Result.ACCEPT);

            @SuppressWarnings(value = {"rawtypes", "unchecked"})
            RollingFileAppender.Builder<?> builder = RollingFileAppender.<RollingFileAppender.Builder> newBuilder();

            Appender appenderNormal = builder.setName("NormalRollingFile")
                    .withFileName("logs/app.log").withFilePattern("logs/app-%i.log.gz").withLocking(false)
                    .setImmediateFlush(true).setFilter(thresholdFilter).withPolicy(getPolicy())
                    .withStrategy(defaultRolloverStrategy).setBufferedIo(true).setBufferSize(1024).setLayout(layout)
                    .build();
            appenderNormal.start();
            config.getRootLogger().addAppender(appenderNormal, null, null);
            //for log4j < 2.8, just ignore currently
            try {
                AppenderRef refNormal = AppenderRef.createAppenderRef(appenderNormal.getName(), null, null);
                appenderRefs.add(refNormal);
                config.getRootLogger().getAppenderRefs().add(refNormal);
            } catch (Exception e) {
            }
        }
        // ErrorRollingFile
        if (file) {
            ThresholdFilter thresholdFilter = ThresholdFilter.createFilter(Level.ERROR, Result.ACCEPT, Result.DENY);

            @SuppressWarnings(value = {"rawtypes", "unchecked"})
            RollingFileAppender.Builder<?> builder = RollingFileAppender.<RollingFileAppender.Builder> newBuilder();

            Appender appenderError = builder.setName("ErrorRollingFile")
                    .withFileName("logs/error.log").withFilePattern("logs/error-%i.log.gz").withLocking(false)
                    .setImmediateFlush(true).setFilter(thresholdFilter).withPolicy(getPolicy())
                    .withStrategy(defaultRolloverStrategy).setBufferedIo(true).setIgnoreExceptions(false)
                    .setBufferSize(1024).setLayout(layout).build();
            appenderError.start();
            config.getRootLogger().addAppender(appenderError, null, null);
            //for log4j < 2.8, just ignore currently
            try {
                AppenderRef refError = AppenderRef.createAppenderRef(appenderError.getName(), null, null);
                appenderRefs.add(refError);
                config.getRootLogger().getAppenderRefs().add(refError);
            } catch (Exception e) {
            }
        }
        {
            LoggerConfig loggerConfig = LoggerConfig.newBuilder().withAdditivity(false).withLevel(Level.WARN)
                    .withLoggerName("ONSLogger").withRefs(appenderRefs.toArray(new AppenderRef[] {})).withConfig(config)
                    .build();
            config.addLogger("RocketmqClient", loggerConfig);
            config.addLogger("RocketmqRemoting", loggerConfig);
        }
        {
            ServiceLoader<LoggerAppender> serviceLoader = ServiceLoader.load(LoggerAppender.class);
            if(serviceLoader != null){
                Iterator<LoggerAppender> itor = serviceLoader.iterator();
                while(itor.hasNext()){
                    LoggerAppender curLogAppender = itor.next();
                    if(curLogAppender.getLogger()!=null || curLogAppender.getLogger().size()>0){
                        LoggerConfig customLogConfig = LoggerConfig.newBuilder().withAdditivity(curLogAppender.getAdditivity()).withLevel(curLogAppender.getLogLevel())
                            .withLoggerName(curLogAppender.getLoggerName()).withIncludeLocation(curLogAppender.getIncludeLocation())
                            .withRefs(appenderRefs.toArray(new AppenderRef[]{})).withProperties(curLogAppender.getProperties())
                            .withConfig(config).withFilter(curLogAppender.getFilter()).build();
                        for(String loggerPattern : curLogAppender.getLogger()){
                            config.addLogger(loggerPattern, customLogConfig);
                            System.out.println("加载自定义日志Logger: LoggerPattern="+loggerPattern+", LogLevel="+curLogAppender.getLogLevel());
                        }
                    }
                }
            }
        }
        ctx.updateLoggers();
        inited = true;
        logger.info("no log4j2.xml found, init default log4j2 succeed");
    }
}
