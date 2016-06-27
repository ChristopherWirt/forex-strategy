package hu.fnf.devel.forex;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.log4j.PropertyConfigurator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

public class Configuration {

    private static final DateTimeFormatter dateFormat = DateTimeFormat
            .forPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(DateTimeZone.forID("GMT"));

    private static Configuration instance;

    private Logger logger = LogManager.getLogger();
    private Properties prop = new Properties();

    public static void init(String[] args) throws ConfigurationException {
        if (args.length != 1) {
            throw new ConfigurationException("You must provide a single program argument pointing to the properties file");
        }
        instance = new Configuration(args[0]);
    }

    public static Configuration getInstance() throws ConfigurationException {
        if(instance == null) {
            throw new ConfigurationException("Configuration is not yet initilized. Please call init() first");
        }
        return instance;
    }

    private Configuration(String configPath) throws ConfigurationException {
        try{
            prop.load(new FileInputStream(configPath));
            PropertyConfigurator.configure(configPath);
        } catch(IOException e) {
            throw new ConfigurationException("Unable to load conf from " + configPath, e);
        }

        logger.info("Using conf file: " + configPath);
        for (Object key : prop.keySet()) {
            logger.debug("\t" + key.toString() + "\t=\t" + get(key));
        }

        logger.info("Account: " + getAccountUser());
    }

    private String get(Object key) { return get(key.toString()); }

    public String get(String key) {
        if (key.contains("assword")) {
            return "";
        }
        return prop.getProperty(key);
    }

    public DateTime getTestStartDate() { return getDate("test.from"); }

    public DateTime getTestEndDate() { return getDate("test.till"); }

    public boolean isTest() { return BooleanUtils.toBoolean(get("test.mode")); }

    public boolean createGui() {
        return BooleanUtils.toBoolean(get("account.gui"));
    }

    public String getEmailAddress() {
        return get("account.email");
    }

    public String getNetworkJnpl() {
        return get("network.jnpl");
    }

    public String getAccountUser() {
        return get("account.user");
    }

    public String getAccountPassword() {
        return get("account.password");
    }

    public String getSmtpHost() { return get("email.host"); }

    public String getFromEmailAddress() { return get("email.from"); }

    public String getStartState() { return get("state.start"); }

    private DateTime getDate(String key) {
        DateTime date = dateFormat.parseDateTime(get(key));
        //maybe add some sanity check here
        return date;
    }
}
