package hu.fnf.devel.forex;

import com.dukascopy.api.*;
import com.dukascopy.api.system.*;
import com.dukascopy.api.system.ITesterClient.DataLoadingMethod;
import hu.fnf.devel.forex.utils.Info;
import hu.fnf.devel.forex.utils.RobotException;
import hu.fnf.devel.forex.utils.WebInfo;
import org.apache.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationException;
import org.joda.time.DateTime;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.util.concurrent.Future;

public class Main {

    private static final String JARFILE_PATH = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    private static final String JARFILE =
            JARFILE_PATH.split(System.getProperty("file.separator"))[JARFILE_PATH.split(System.getProperty("file.separator")).length - 1];
    public static final String VERSION = JARFILE.replaceAll("[a-z-]*", "").trim();
    public static Configuration conf;

    private static final Logger logger = Logger.getLogger(Main.class);
    private static IClient client;
    private static long processId;
    private static Info info;
    private static Phase phase;

    enum Phase {
        CONFIGURATION,
        INITILIZATION,
        INTERRUPTED,
        CONNECTION,
        STRATEGY_STARTING,
        RUNNING,
        CLOSING,
    }

    public static String getPhase() {
        return phase.name() + " ";
    }

    public static void setPhase(Phase phase) {
        if (Main.phase != null) {
            logger.info("--- Stopping " + getPhase() + "phase ---");
        }
        Main.phase = phase;
        if (Main.phase != null) {
            logger.info("----------------------------------------");
            logger.info("--- Starting " + getPhase() + "phase ---");
        }
    }

    private static void configureShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    public void run() {
                        Main.setPhase(Phase.INTERRUPTED);
                        if (client != null && processId != 0) {
                            client.stopStrategy(processId);
                        }
                        logger.info("------------------------- Done. ----------------------------");
                    }
                })
        );
    }

    private static void configureClient() throws Exception {
        if (!conf.isTest()) {
            client = ClientFactory.getDefaultInstance();
            connectClient();
            return;
        }

        client = TesterFactory.getDefaultInstance();

        StateMachine.getInstance().subscribe(client);

        connectClient();

        ((ITesterClient) client).setInitialDeposit(Instrument.EURUSD.getSecondaryJFCurrency(), 500);
        logger.info("ITesterClient client has been initialized with deposit " + 500 + " USD");

        DateTime dateFrom = conf.getTestStartDate();
        DateTime dateTo = conf.getTestEndDate();

        ((ITesterClient) client).setDataInterval(DataLoadingMethod.TICKS_WITH_TIME_INTERVAL, dateFrom.getMillis(), dateTo.getMillis());
        // client.setDataInterval(Period.FIFTEEN_MINS, OfferSide.BID,
        // InterpolationMethod.CLOSE_TICK, dateFrom.getTime(),
        // dateTo.getTime());
        // load data
        logger.info("Downloading data");
        Future<?> future = ((ITesterClient) client).downloadData(null);
        // wait for downloading to complete
        future.get();
    }

    private static void configureClientCallbacks() {
        client.setSystemListener(new ISystemListener() {
            public void onStop(long processId) {
                logger.info("Client(" + processId + ") has been stopped.");
                Main.setPhase(null);
                System.exit(0);
            }

            public void onStart(long processId) {
                logger.info("Client(" + processId + ") has been started.");
                Main.processId = processId;
            }

            public void onDisconnect() {
                logger.info("Client has been disconnected.Trying to reconnect...");
                client.reconnect();
            }

            public void onConnect() {
                logger.info("Client has been connected...");
            }
        });
    }

    private static TesterMainGUI createGui() {
        final TesterMainGUI gui = new TesterMainGUI();
        StateMachine.getInstance().setGui(gui);

        //Gui will start strategy
        client.setSystemListener(new ISystemListener() {
            @Override
            public void onStart(long processId) {
                logger.info("Strategy started: " + processId);
                gui.updateButtons();
            }

            @Override
            public void onStop(long processId) {
                logger.info("Strategy stopped: " + processId);
                gui.resetButtons();
                if (client instanceof ITesterClient) {
                    File reportFile = new File("/tmp/report.html");
                    try {
                        ((ITesterClient) client).createReport(processId, reportFile);
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }

                if (client.getStartedStrategies().size() == 0) {
                    // Do nothing
                }
            }

            @Override
            public void onConnect() {
                logger.info("Connected");
            }

            @Override
            public void onDisconnect() {
                // tester doesn't disconnect
            }
        });
        gui.showChartFrame();

        return gui;
    }

    public static void main(String[] args) throws Exception {
        try {
            setPhase(Phase.CONFIGURATION);

            configureShutdownHook();

            Configuration.init(args);
            conf = Configuration.getInstance();

            logger.info("-------- Forex robot v" + VERSION + " written by johnnym.. modifed by cwirt --------");

            setPhase(Phase.INITILIZATION);

            // init web data cache
            info = new WebInfo();

            configureClient();

            configureClientCallbacks();

            if (conf.createGui()) {
                TesterMainGUI gui = createGui();
                if (conf.isTest()) {
                    ((ITesterClient)client).startStrategy(StateMachine.getInstance(), new LoadingProgressListener() {
                        @Override
                        public void dataLoaded(long startTime, long endTime, long currentTime, String information) {
                        }

                        @Override
                        public void loadingFinished(boolean allDataLoaded, long startTime, long endTime, long currentTime) {
                        }

                        @Override
                        public boolean stopJob() {
                            return false;
                        }
                    }, gui, gui);
                    return;
                }
            }

            startStrategy();

        } catch(ConfigurationException ce) {
            logger.fatal("Invalid configuration", ce);
        } catch(Exception ex) {
            logger.fatal(ex);
        } finally {
            closing();
        }
    }

    public static void connectClient() throws Exception {
        if (client == null) {
            throw new RobotException("Client is null unable to connect");
        }

        if (client.isConnected()) {
            logger.debug("Client is already connected");
            return;
        }

        setPhase(Phase.CONNECTION);
        client.connect(conf.getNetworkJnpl(), conf.getAccountUser(), conf.getAccountPassword());

        int i = 50;
        while (i-- > 0 && !client.isConnected()) {
            logger.debug("waiting for connection ...");
            Thread.sleep(1000);
        }
        logger.info("Number of instruments loaded is " + client.getAvailableInstruments().size());
    }

    private static void startStrategy() {
        setPhase(Phase.STRATEGY_STARTING);

        client.startStrategy(StateMachine.getInstance(), new IStrategyExceptionHandler() {
            @Override
            public void onException(long strategyId, Source source, Throwable t) {
                // throw t
                closing();
            }
        });

        setPhase(Phase.RUNNING);
    }

    private static void closing() {
        setPhase(Phase.CLOSING);
        if (client != null && client.isConnected()) {
            client.disconnect(); // listener onStop called (?)
        }
    }

    public static boolean isMarketOpen(String market) {
        return info.isMarketOpen(market);
    }

    public static void sendMail(String subject, String body) throws RobotException {
        if (!conf.isTest()) {
            String to = conf.getEmailAddress();
            String from = conf.getFromEmailAddress();
            System.setProperty("mail.smtp.host", conf.getSmtpHost());
            Session session = Session.getDefaultInstance(System.getProperties());

            try {
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(from));
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
                message.setSubject(subject);
                message.setText(body);
                Transport.send(message);
            } catch (Exception err) {
                throw new RobotException("Cannot send mail!", err);
            }
            logger.info("Mail \"" + subject + "\" has been sent to " + conf.getEmailAddress());
        }
    }

    public static void printDetails(IOrder iOrder) {
        logger.info("IOrder \"" + iOrder.getLabel() + "\" #" + iOrder.getId());
        logger.debug("\tid:            " + iOrder.getId());
        logger.debug("\tctime:         " + iOrder.getCreationTime());
        logger.debug("\tcurrency:      " + iOrder.getInstrument());
        logger.debug("\tlabel:         " + iOrder.getLabel());
        logger.debug("\tprofit/loss:   " + iOrder.getProfitLossInUSD());
    }
}

