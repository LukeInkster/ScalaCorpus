/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import scala.collection.JavaConverters;

import play.libs.Scala;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The current application configuration.
 *
 * @deprecated Use Config instead.
 */
@Singleton
@Deprecated
public class Configuration {

    /**
     * The root configuration.
     * <p>
     * @return a Configuration instance
     */
    public static Configuration root() {
        return new Configuration(
            play.api.Play.unsafeApplication().configuration()
        );
    }

    /**
     * Load a new configuration from an environment.
     *
     * @param env the environment used to initialize the created config
     * @return the created config
     */
    public static Configuration load(Environment env) {
        return new Configuration(play.api.Configuration.load(env.underlying()));
    }

    /**
     * A new empty configuration.
     *
     * @return a new empty configuration
     */
    public static Configuration empty() {
        return new Configuration(ConfigFactory.empty());
    }

    /**
     * A new reference configuration.
     *
     * @return the configuration
     */
    public static Configuration reference() {
        return new Configuration(ConfigFactory.defaultReference());
    }

    // --

    private final play.api.Configuration conf;

    /**
     * Creates a new configuration from a Typesafe Config object.
     *
     * @param conf the typesafe config
     */
    public Configuration(Config conf) {
        this(new play.api.Configuration(conf));
    }

    /**
     * Creates a new configuration from a map.
     *
     * @param conf the configuration map
     */
    public Configuration(Map<String, Object> conf) {
        this(ConfigFactory.parseMap(conf));
    }

    /**
     * Creates a new configuration by parsing a string in HOCON format.
     *
     * @param s the HOCON-formatted string
     */
    public Configuration(String s) {
        this(ConfigFactory.parseString(s));
    }

    /**
     * Creates a new configuration from a Scala-based configuration.
     *
     * @param conf the scala-based configuration
     */
    @Inject
    public Configuration(play.api.Configuration conf) {
        this.conf = conf;
    }

    // --

    /**
     * Retrieves a sub-configuration, which is a configuration instance containing all keys that start with the given prefix.
     *
     * @param key The root prefix for this sub configuration.
     * @return Maybe a new configuration
     */
    public Configuration getConfig(String key) {
        scala.Option<play.api.Configuration> nConf = conf.getConfig(key);
        if(nConf.isDefined()) {
            return new Configuration(nConf.get());
        }
        return null;
    }

    /**
     * Retrieves a configuration value as a <code>String</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public String getString(String key) {
        return Scala.orNull(conf.getString(key, scala.Option.empty()));
    }

    /**
     * Retrieves a configuration value as a <code>String</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultString default value if configuration key doesn't exist
     * @return a configuration value or the defaultString
     */
    public String getString(String key, String defaultString) {
        return Scala.orElse(conf.getString(key, scala.Option.empty()), defaultString);
    }

    /**
     * Retrieves a configuration value as a <code>Milliseconds</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public Long getMilliseconds(String key) {
        return (Long)Scala.orNull(conf.getMilliseconds(key));
    }

    /**
     * Retrieves a configuration value as a <code>Milliseconds</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultMilliseconds default value if configuration key doesn't exist
     * @return a configuration value or the defaultMilliseconds
     */
    public Long getMilliseconds(String key, Long defaultMilliseconds) {
        return (Long)Scala.orElse(conf.getMilliseconds(key), defaultMilliseconds);
    }

    /**
     * Retrieves a configuration value as a <code>Nanoseconds</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public Long getNanoseconds(String key) {
        return (Long)Scala.orNull(conf.getNanoseconds(key));
    }

    /**
     * Retrieves a configuration value as a <code>Nanoseconds</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultNanoseconds default value if configuration key doesn't exist
     * @return a configuration value or the defaultMilliseconds
     */
    public Long getNanoseconds(String key, Long defaultNanoseconds) {
        return (Long)Scala.orElse(conf.getNanoseconds(key), defaultNanoseconds);
    }

    /**
     * Retrieves a configuration value as a <code>Bytes</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public Long getBytes(String key) {
        return (Long)Scala.orNull(conf.getBytes(key));
    }

    /**
     * Retrieves a configuration value as a <code>Bytes</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultBytes default value if configuration key doesn't exist
     * @return a configuration value or the defaultBytes
     */
    public Long getBytes(String key, Long defaultBytes) {
        return (Long)Scala.orElse(conf.getBytes(key), defaultBytes);
    }

    /**
     * Retrieves a configuration value as an <code>Double</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public Double getDouble(String key) {
        return (Double)Scala.orNull(conf.getDouble(key));
    }

    /**
     * Retrieves a configuration value as an <code>Double</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultDouble default value if configuration key doesn't exist
     * @return a configuration value or the defaultInteger
     */
    public Double getDouble(String key, Double defaultDouble) {
        return (Double)Scala.orElse(conf.getDouble(key), defaultDouble);
    }

    /**
     * Retrieves a configuration value as an <code>Int</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public Integer getInt(String key) {
        return (Integer)Scala.orNull(conf.getInt(key));
    }

    /**
     * Retrieves a configuration value as an <code>Int</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultInteger default value if configuration key doesn't exist
     * @return a configuration value or the defaultInteger
     */
    public Integer getInt(String key, Integer defaultInteger) {
        return (Integer)Scala.orElse(conf.getInt(key), defaultInteger);
    }

    /**
     * Retrieves a configuration value as an <code>Long</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public Long getLong(String key) {
        return (Long)Scala.orNull(conf.getLong(key));
    }

    /**
     * Retrieves a configuration value as an <code>Long</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultLong default value if configuration key doesn't exist
     * @return a configuration value or the defaultInteger
     */
    public Long getLong(String key, Long defaultLong) {
        return (Long)Scala.orElse(conf.getLong(key), defaultLong);
    }

    /**
     * Retrieves a configuration value as an <code>Number</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public Number getNumber(String key) {
        return (Number)Scala.orNull(conf.getNumber(key));
    }

    /**
     * Retrieves a configuration value as an <code>Number</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultNumber default value if configuration key doesn't exist
     * @return a configuration value or the defaultInteger
     */
    public Number getNumber(String key, Number defaultNumber) {
        return (Number)Scala.orElse(conf.getNumber(key), defaultNumber);
    }

    /**
     * Retrieves a configuration value as a <code>Boolean</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public Boolean getBoolean(String key) {
        return (Boolean)Scala.orNull(conf.getBoolean(key));
    }

    /**
     * Retrieves a configuration value as a <code>Boolean</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultBoolean default value if configuration key doesn't exist
     * @return a configuration value or the defaultBoolean
     */
    public Boolean getBoolean(String key, Boolean defaultBoolean) {
        return (Boolean)Scala.orElse(conf.getBoolean(key), defaultBoolean);
    }

    /**
     * Retrieves the set of keys available in this configuration.
     *
     * @return the set of keys available in this configuration
     */
    public Set<String> keys() {
        return JavaConverters.setAsJavaSetConverter(conf.keys()).asJava();
    }

    /**
     * Retrieves the set of direct sub-keys available in this configuration.
     *
     * @return the set of direct sub-keys available in this configuration
     */
    public Set<String> subKeys() {
        return JavaConverters.setAsJavaSetConverter(conf.subKeys()).asJava();
    }

    /**
     * Returns the config as a map of plain old Java maps, lists and values.
     *
     * @return The config map
     */
    public Map<String, Object> asMap() {
        return conf.underlying().root().unwrapped();
    }

    /**
     * Returns the underlying Typesafe config object.
     *
     * @return The config
     */
    public Config underlying() {
        return conf.underlying();
    }

    /**
     * Returns the config as a set of full paths to config values.  This is
     * different to {@link #asMap()} in that it returns {@link com.typesafe.config.ConfigValue}
     * objects, and keys are recursively expanded to be pull path keys.
     *
     * @return The config as an entry set
     */
    public Set<Map.Entry<String, ConfigValue>> entrySet() {
        return conf.underlying().entrySet();
    }

    /**
     * Creates a configuration error for a specific configuration key.
     *
     * @param key the configuration key, related to this error
     * @param message the error message
     * @param e the optional related exception
     * @return a configuration exception
     */
    public RuntimeException reportError(String key, String message, Throwable e) {
        return conf.reportError(key, message, scala.Option.apply(e));
    }

    /**
     * Retrieves a configuration value as a {@code List<Boolean>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<Boolean> getBooleanList(String key) {
        return (List<Boolean>)Scala.orNull(conf.getBooleanList(key));
    }

    /**
     * Retrieves a configuration value as a {@code List<Boolean>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<Boolean> getBooleanList(String key, List<Boolean> defaultList) {
        return (List<Boolean>)Scala.orElse(conf.getBooleanList(key), defaultList);
    }

    /**
     * Retrieves a configuration value as a {@code List<Long>} representing bytes.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<Long> getBytesList(String key) {
        return (List<Long>)Scala.orNull(conf.getBytesList(key));
    }

    /**
     * Retrieves a configuration value as a {@code List<Long>} representing bytes.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<Long> getBytesList(String key, List<Long> defaultList) {
        return (List<Long>)Scala.orElse(conf.getBytesList(key), defaultList);
    }

    /**
     * Retrieves a configuration value as a {@code List<Configuration>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<Configuration> getConfigList(String key) {
        if (conf.getConfigList(key).isDefined()) {
            return conf.getConfigList(key).get().stream().map(Configuration::new).collect(Collectors.toList());
        }

        return null;
    }

    /**
     * Retrieves a configuration value as a {@code List<Configuration>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<Configuration> getConfigList(String key, List<Configuration> defaultList) {
        List<Configuration> out = getConfigList(key);
        if (out == null) {
          out = defaultList;
        }
        return out;
    }

    /**
     * Retrieves a configuration value as a {@code List<Double>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<Double> getDoubleList(String key) {
        return (List<Double>)Scala.orNull(conf.getDoubleList(key));
    }

    /**
     * Retrieves a configuration value as a {@code List<Double>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<Double> getDoubleList(String key, List<Double> defaultList) {
        return (List<Double>)Scala.orElse(conf.getDoubleList(key), defaultList);
    }

    /**
     * Retrieves a configuration value as a {@code List<Integer>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<Integer> getIntList(String key) {
        return (List<Integer>)Scala.orNull(conf.getIntList(key));
    }

    /**
     * Retrieves a configuration value as a {@code List<Integer>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<Integer> getIntList(String key, List<Integer> defaultList) {
        return (List<Integer>)Scala.orElse(conf.getIntList(key), defaultList);
    }

    /**
     * Retrieves a configuration value as a {@code List<Object>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<Object> getList(String key) {
        if (conf.getList(key).isDefined()) {
          return conf.getList(key).get().unwrapped();
        }
        return null;
    }

    /**
     * Retrieves a configuration value as a {@code List<Object>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<Object> getList(String key, List<Object> defaultList) {
        List<Object> out = getList(key);
        if (out == null) {
          out = defaultList;
        }
        return out;
    }

    /**
     * Retrieves a configuration value as a {@code List<Long>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<Long> getLongList(String key) {
        return (List<Long>)Scala.orNull(conf.getLongList(key));
    }

    /**
     * Retrieves a configuration value as a {@code List<Long>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<Long> getLongList(String key, List<Long> defaultList) {
        return (List<Long>)Scala.orElse(conf.getLongList(key), defaultList);
    }

    /**
     * Retrieves a configuration value as a {@code List<Long>} representing Milliseconds.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<Long> getMillisecondsList(String key) {
        return (List<Long>)Scala.orNull(conf.getMillisecondsList(key));
    }

    /**
     * Retrieves a configuration value as a {@code List<Long>} representing Milliseconds.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<Long> getMillisecondsList(String key, List<Long> defaultList) {
        return (List<Long>)Scala.orElse(conf.getMillisecondsList(key), defaultList);
    }

    /**
     * Retrieves a configuration value as a {@code List<Long>} representing Nanoseconds.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<Long> getNanosecondsList(String key) {
        return (List<Long>)Scala.orNull(conf.getNanosecondsList(key));
    }

    /**
     * Retrieves a configuration value as a {@code List<Long>} representing Nanoseconds.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<Long> getNanosecondsList(String key, List<Long> defaultList) {
        return (List<Long>)Scala.orElse(conf.getNanosecondsList(key), defaultList);
    }

    /**
     * Retrieves a configuration value as a {@code List<Number>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<Number> getNumberList(String key) {
        return (List<Number>)Scala.orNull(conf.getNumberList(key));
    }

    /**
     * Retrieves a configuration value as a {@code List<Number>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<Number> getNumberList(String key, List<Number> defaultList) {
        return (List<Number>)Scala.orElse(conf.getNumberList(key), defaultList);
    }

    /**
     * Retrieves a configuration value as a {@code List<Map<String, Object>>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<Map<String, Object>> getObjectList(String key) {
        if (conf.getObjectList(key).isDefined()) {
            return conf.getObjectList(key).get().stream().map((Function<ConfigObject, Map<String, Object>>) ConfigObject::unwrapped).collect(Collectors.toList());
        }
        return null;
    }

    /**
     * Retrieves a configuration value as a {@code List<Map<String, Object>>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<Map<String, Object>> getObjectList(String key, List<Map<String, Object>> defaultList) {
        List<Map<String, Object>> out = getObjectList(key);
        if (out == null) {
          out = defaultList;
        }
        return out;
    }

    /**
     * Retrieves a configuration value as a {@code List<String>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public List<String> getStringList(String key) {
        return (List<String>)Scala.orNull(conf.getStringList(key));
    }

    /**
     * Retrieves a configuration value as a {@code List<Number>}.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultList default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public List<String> getStringList(String key, List<String> defaultList) {
        return (List<String>)Scala.orElse(conf.getStringList(key), defaultList);
    }

    /**
     * Retrieves a configuration value as a <code>Object</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @return a configuration value or <code>null</code>
     */
    public Object getObject(String key) {
        if (conf.getObject(key).isDefined()) {
          return conf.getObject(key).get().unwrapped();
        }
        return null;
    }

    /**
     * Retrieves a configuration value as a <code>Object</code>.
     *
     * @param key configuration key (relative to configuration root key)
     * @param defaultObject default value if configuration key doesn't exist
     * @return a configuration value or the defaultList
     */
    public Object getObject(String key, Object defaultObject) {
        Object out = getObject(key);
        if (out == null) {
          out = defaultObject;
        }
        return out;
    }

    /**
     * Extend this configuration with fallback configuration.
     *
     * @param fallback the configuration to fall back on if no value is found for a key
     * @return a new configuration that falls back on the provided one
     */
    public Configuration withFallback(Configuration fallback) {
        return new Configuration(underlying().withFallback(fallback.underlying()));
    }

    public play.api.Configuration getWrappedConfiguration() {
        return conf;
    }
}
