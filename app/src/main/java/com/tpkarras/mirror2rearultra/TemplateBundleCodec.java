package com.tpkarras.mirror2rearultra;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The file a set of saved templates travels in.
 *
 * <p>A template is a name and the stored values of its slot, each as the
 * {@code b:}/{@code i:}/{@code s:} text {@link DashboardWidgetLayout#encodeStateValue}
 * writes - the same encoding the settings backup uses, so the two cannot
 * disagree about what a value is. Keys are the slot's own, without the slot's
 * prefix: the importer gives each template a slot of its own, so ids are never
 * part of the file and a template can be loaded twice.
 */
final class TemplateBundleCodec {
    static final int MAX_FILE_BYTES = 4 * 1_048_576;
    static final int MAX_NAME_LENGTH = 40;
    private static final String FORMAT = "Mirror2RearUltra templates";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_TEMPLATES = 50;
    private static final int MAX_ENTRIES = 10_000;
    private static final int MAX_KEY_LENGTH = 200;
    private static final int MAX_VALUE_LENGTH = 20_000;

    static final class Template {
        final String name;
        final Map<String, String> state;

        Template(String name, Map<String, String> state) {
            this.name = name;
            this.state = Collections.unmodifiableMap(new LinkedHashMap<>(state));
        }
    }

    static final class BundleException extends Exception {
        BundleException(String message) {
            super(message);
        }

        BundleException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private TemplateBundleCodec() {
    }

    static void write(List<Template> templates, OutputStream output) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format", FORMAT);
        properties.setProperty("schema", String.valueOf(SCHEMA_VERSION));
        properties.setProperty("template.count", String.valueOf(templates.size()));
        for (int index = 0; index < templates.size(); index++) {
            Template template = templates.get(index);
            String prefix = "template." + index + ".";
            properties.setProperty(prefix + "name", template.name);
            properties.setProperty(prefix + "count", String.valueOf(template.state.size()));
            int entry = 0;
            for (Map.Entry<String, String> value : template.state.entrySet()) {
                properties.setProperty(prefix + entry + ".key", value.getKey());
                properties.setProperty(prefix + entry + ".value", value.getValue());
                entry++;
            }
        }
        properties.storeToXML(output, "Mirror2RearUltra templates", "UTF-8");
    }

    static List<Template> read(InputStream input) throws BundleException {
        Properties properties = new Properties();
        try {
            properties.loadFromXML(input);
        } catch (IOException | IllegalArgumentException error) {
            throw new BundleException("invalid_xml", error);
        }
        if (!FORMAT.equals(properties.getProperty("format"))) {
            throw new BundleException("wrong_format");
        }
        int schema = integer(properties, "schema", 1, Integer.MAX_VALUE);
        if (schema > SCHEMA_VERSION) {
            throw new BundleException("unsupported_schema");
        }
        int count = integer(properties, "template.count", 1, MAX_TEMPLATES);
        List<Template> templates = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String prefix = "template." + index + ".";
            String name = required(properties, prefix + "name").trim();
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                throw new BundleException("invalid_name");
            }
            int entries = integer(properties, prefix + "count", 1, MAX_ENTRIES);
            Map<String, String> state = new LinkedHashMap<>();
            for (int entry = 0; entry < entries; entry++) {
                String key = required(properties, prefix + entry + ".key");
                String value = required(properties, prefix + entry + ".value");
                if (!validKey(key) || !validValue(value) || state.put(key, value) != null) {
                    throw new BundleException("invalid_entry");
                }
            }
            // What marks a slot as holding a template at all.
            if (!"b:true".equals(state.get("saved"))) {
                throw new BundleException("not_a_template");
            }
            templates.add(new Template(name, state));
        }
        return templates;
    }

    /** A setting's own name: letters, digits and underscores, never a way into another slot. */
    private static boolean validKey(String key) {
        return !key.isEmpty() && key.length() <= MAX_KEY_LENGTH && key.matches("[A-Za-z0-9_]+");
    }

    private static boolean validValue(String value) {
        if (value.length() > MAX_VALUE_LENGTH || value.length() < 2) {
            return false;
        }
        String body = value.substring(2);
        if (value.startsWith("b:")) {
            return body.equals("true") || body.equals("false");
        }
        if (value.startsWith("i:")) {
            try {
                Integer.parseInt(body);
                return true;
            } catch (NumberFormatException error) {
                return false;
            }
        }
        return value.startsWith("s:");
    }

    private static String required(Properties properties, String key) throws BundleException {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new BundleException("missing_" + key);
        }
        return value;
    }

    private static int integer(Properties properties, String key, int minimum, int maximum)
            throws BundleException {
        try {
            int value = Integer.parseInt(required(properties, key));
            if (value < minimum || value > maximum) {
                throw new BundleException("integer_out_of_range");
            }
            return value;
        } catch (NumberFormatException error) {
            throw new BundleException("invalid_integer", error);
        }
    }
}
