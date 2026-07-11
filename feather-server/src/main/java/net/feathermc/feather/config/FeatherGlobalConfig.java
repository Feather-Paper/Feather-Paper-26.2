package net.feathermc.feather.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FeatherGlobalConfig {

    private static final String CURRENT_VERSION = "3.0";
    private static final String CURRENT_REGION = Locale.getDefault().getCountry().toUpperCase(Locale.ROOT); // It will be in uppercase by default, just make sure
    private static final boolean isCN = CURRENT_REGION.equals("CN");

    private static ConfigFile configFile;
    private static FeatherPresets.Preset preset = FeatherPresets.Preset.BALANCED;

    public FeatherGlobalConfig(boolean init) throws Exception {
        configFile = ConfigFile.loadConfig(new File(FeatherConfig.I_CONFIG_FOLDER, FeatherConfig.I_GLOBAL_CONFIG_FILE));

        try {
            preset = FeatherPresets.Preset.valueOf(getString("preset", "BALANCED", pickStringRegionBased(
                """
                    The optimization preset to use.
                    Supported values: BALANCED, PERFORMANCE, EXTREME""",
                """
                    使用的优化预设.
                    支持的值: BALANCED, PERFORMANCE, EXTREME""")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            FeatherConfig.LOGGER.error("Invalid preset configured, falling back to BALANCED.");
            preset = FeatherPresets.Preset.BALANCED;
        }

        FeatherConfig.loadConfigVersion(getString("config-version"), CURRENT_VERSION);
        configFile.set("config-version", CURRENT_VERSION);

        configFile.addComments("config-version", pickStringRegionBased("""
                Feather Config

                GitHub Repo: https://github.com/Denis-Feather/Feather""",
            """
                Feather 配置

                GitHub 仓库: https://github.com/Denis-Feather/Feather"""));

        // Pre-structure to force order
        structureConfig();
    }

    protected void structureConfig() {
        for (EnumConfigCategory configCate : EnumConfigCategory.getCategoryValues()) {
            createTitledSection(configCate.name(), configCate.getBaseKeyName());
        }
    }

    public void saveConfig() throws Exception {
        configFile.save();
    }

    // Config Utilities

    /* getAndSet */

    public void createTitledSection(String title, String path) {
        configFile.addSection(title);
        configFile.addDefault(path, null);
    }

    public boolean getBoolean(String path, boolean def, String comment) {
        boolean overriddenDef = FeatherPresets.getOverride(preset, path, def);
        configFile.addDefault(path, overriddenDef, comment);
        return configFile.getBoolean(path, overriddenDef);
    }

    public boolean getBoolean(String path, boolean def) {
        boolean overriddenDef = FeatherPresets.getOverride(preset, path, def);
        configFile.addDefault(path, overriddenDef);
        return configFile.getBoolean(path, overriddenDef);
    }

    public String getString(String path, String def, String comment) {
        configFile.addDefault(path, def, comment);
        return configFile.getString(path, def);
    }

    public String getString(String path, String def) {
        configFile.addDefault(path, def);
        return configFile.getString(path, def);
    }

    public double getDouble(String path, double def, String comment) {
        double overriddenDef = FeatherPresets.getOverride(preset, path, def);
        configFile.addDefault(path, overriddenDef, comment);
        return configFile.getDouble(path, overriddenDef);
    }

    public double getDouble(String path, double def) {
        double overriddenDef = FeatherPresets.getOverride(preset, path, def);
        configFile.addDefault(path, overriddenDef);
        return configFile.getDouble(path, overriddenDef);
    }

    public int getInt(String path, int def, String comment) {
        int overriddenDef = FeatherPresets.getOverride(preset, path, def);
        configFile.addDefault(path, overriddenDef, comment);
        return configFile.getInteger(path, overriddenDef);
    }

    public int getInt(String path, int def) {
        int overriddenDef = FeatherPresets.getOverride(preset, path, def);
        configFile.addDefault(path, overriddenDef);
        return configFile.getInteger(path, overriddenDef);
    }

    public long getLong(String path, long def, String comment) {
        long overriddenDef = FeatherPresets.getOverride(preset, path, def);
        configFile.addDefault(path, overriddenDef, comment);
        return configFile.getLong(path, overriddenDef);
    }

    public long getLong(String path, long def) {
        long overriddenDef = FeatherPresets.getOverride(preset, path, def);
        configFile.addDefault(path, overriddenDef);
        return configFile.getLong(path, overriddenDef);
    }

    public List<String> getList(String path, List<String> def, String comment) {
        configFile.addDefault(path, def, comment);
        return configFile.getStringList(path);
    }

    public List<String> getList(String path, List<String> def) {
        configFile.addDefault(path, def);
        return configFile.getStringList(path);
    }

    public ConfigSection getConfigSection(String path, Map<String, Object> defaultKeyValue, String comment) {
        configFile.addDefault(path, null, comment);
        configFile.makeSectionLenient(path);
        defaultKeyValue.forEach((string, object) -> configFile.addExample(path + "." + string, object));
        return configFile.getConfigSection(path);
    }

    public ConfigSection getConfigSection(String path, Map<String, Object> defaultKeyValue) {
        configFile.addDefault(path, null);
        configFile.makeSectionLenient(path);
        defaultKeyValue.forEach((string, object) -> configFile.addExample(path + "." + string, object));
        return configFile.getConfigSection(path);
    }

    /* get */

    public Boolean getBoolean(String path) {
        String value = configFile.getString(path, null);
        return value == null ? null : Boolean.parseBoolean(value);
    }

    public String getString(String path) {
        return configFile.getString(path, null);
    }

    public Double getDouble(String path) {
        String value = configFile.getString(path, null);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            FeatherConfig.LOGGER.warn("{} is not a valid number, skipped! Please check your configuration.", path, e);
            return null;
        }
    }

    public Integer getInt(String path) {
        String value = configFile.getString(path, null);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            FeatherConfig.LOGGER.warn("{} is not a valid number, skipped! Please check your configuration.", path, e);
            return null;
        }
    }

    public Long getLong(String path) {
        String value = configFile.getString(path, null);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            FeatherConfig.LOGGER.warn("{} is not a valid number, skipped! Please check your configuration.", path, e);
            return null;
        }
    }

    public List<String> getList(String path) {
        return configFile.getList(path, null);
    }

    // TODO, check
    public ConfigSection getConfigSection(String path) {
        configFile.addDefault(path, null);
        configFile.makeSectionLenient(path);
        //defaultKeyValue.forEach((string, object) -> configFile.addExample(path + "." + string, object));
        return configFile.getConfigSection(path);
    }

    public void addComment(String path, String comment) {
        configFile.addComment(path, comment);
    }

    public void addCommentIfCN(String path, String comment) {
        if (isCN) {
            configFile.addComment(path, comment);
        }
    }

    public void addCommentIfNonCN(String path, String comment) {
        if (!isCN) {
            configFile.addComment(path, comment);
        }
    }

    public void addCommentRegionBased(String path, String en, String cn) {
        configFile.addComment(path, isCN ? cn : en);
    }

    public String pickStringRegionBased(String en, String cn) {
        return isCN ? cn : en;
    }
}
