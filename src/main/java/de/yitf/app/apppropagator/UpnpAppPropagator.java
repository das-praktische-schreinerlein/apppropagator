/**
 * <h4>FeatureDomain:</h4>
 *     Collaboration
 *
 * <h4>FeatureDescription:</h4>
 *     software for apppropagation
 * 
 * @author Michael Schreiner <michael.schreiner@your-it-fellow.de>
 * @category collaboration
 * @copyright Copyright (c) 2014, Michael Schreiner
 * @license http://mozilla.org/MPL/2.0/ Mozilla Public License 2.0
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.yitf.app.apppropagator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.binding.LocalServiceBindingException;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.ManufacturerDetails;
import org.fourthline.cling.model.meta.ModelDetails;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;

/**
 * <h4>FeatureDomain:</h4>
 *     Webservice
 * <h4>FeatureDescription:</h4>
 *     a UPNP-Service-Popagator to publish information about the app-instance
 * 
 * @package de.yaio.app.upnp
 * @author Michael Schreiner <michael.schreiner@your-it-fellow.de>
 * @category collaboration
 * @copyright Copyright (c) 2014, Michael Schreiner
 * @license http://mozilla.org/MPL/2.0/ Mozilla Public License 2.0
 */
public class UpnpAppPropagator implements Runnable {
    
    /** exitcode for shell */
    public static final int CONST_EXITCODE_OK = 0;
    /** exitcode for shell */
    public static final int CONST_EXITCODE_FAILED_ARGS = 1;
    /** exitcode for shell */
    public static final int CONST_EXITCODE_FAILED_CONFIG = 2;
    /** exitcode for shell */
    public static final int CONST_EXITCODE_FAILED_JOB = 3;

    protected static Properties props = null;

    private static final Logger LOGGER = Logger.getLogger(UpnpAppPropagator.class);
    
    protected static CommandLine commandLine;
    
    /**
     * <h4>FeatureDomain:</h4>
     *     CLI
     * <h4>FeatureDescription:</h4>
     *     Main-method to start the application
     * <h4>FeatureResult:</h4>
     *   <ul>
     *     <li>initialize the application
     *   </ul> 
     * <h4>FeatureKeywords:</h4>
     *     CLI
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        try {
            // parse cmdArgs
            LOGGER.info("initCommandLine");
            Options availiableCmdLineOptions = new Options();
            addAvailiableBaseCmdLineOptions(availiableCmdLineOptions);
            CommandLine commandLine = createCommandLineFromCmdArgs(args, availiableCmdLineOptions);

            // check for unknown Args
            LOGGER.info("used CmdLineArgs: " + args);
            if (commandLine != null) {
                LOGGER.info("unknown CmdLineArgs: " + commandLine.getArgs());
            }

            // get Configpath
            String configPath = commandLine.getOptionValue("config");
            
            // read properties
            props = readProperties(configPath);
            
            // check if app should run
            if (!"yes".equalsIgnoreCase(props.getProperty("propagator.upnp.start", "no"))) {
                LOGGER.info("aborted application: propagator.upnp.start not set");
                System.exit(CONST_EXITCODE_OK);
            }

            // Start a user thread that runs the UPnP stack
            Thread serverThread = new Thread(new UpnpAppPropagator());
            serverThread.setDaemon(false);
            serverThread.start();
            //CHECKSTYLE.OFF: IllegalCatch - Much more readable than catching x exceptions
        } catch (Throwable ex) {
            //CHECKSTYLE.ON: IllegalCatch
            // catch Exception
            System.out.println(ex);
            LOGGER.fatal(ex);
            ex.printStackTrace();
            LOGGER.info("Exit: 1");
            System.exit(CONST_EXITCODE_FAILED_ARGS);
        }
    }
    
    /**
     * <h4>FeatureDomain:</h4>
     *     CLI
     * <h4>FeatureDescription:</h4>
     *     run the service-thread
     * <h4>FeatureResult:</h4>
     *   <ul>
     *     <li>run the service-thread
     *   </ul> 
     * <h4>FeatureKeywords:</h4>
     *     CLI
     */
    public void run() {
        try {
            final UpnpService upnpService = new UpnpServiceImpl();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    upnpService.shutdown();
                    LOGGER.info("done application");
                }
            });
            
            Iterator<InetAddress> iterInetAddr = 
                            upnpService.getRouter().getConfiguration().createNetworkAddressFactory().getBindAddresses();
            while (iterInetAddr.hasNext())  {
                // Add the bound local device to the registry
                InetAddress inetAddr = iterInetAddr.next();
                upnpService.getRegistry().addDevice(createDevice(inetAddr));
            }

            //CHECKSTYLE.OFF: IllegalCatch - Much more readable than catching x exceptions
        } catch (Exception ex) {
            //CHECKSTYLE.ON: IllegalCatch
            System.err.println("Exception occured: " + ex);
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * <h4>FeatureDomain:</h4>
     *     CLI
     * <h4>FeatureDescription:</h4>
     *     create the device
     * <h4>FeatureResult:</h4>
     *   <ul>
     *     <li>create the device
     *   </ul> 
     * <h4>FeatureKeywords:</h4>
     *     CLI
     * @return the device
     * @param inetAddr - inetAddr to bind device to
     */
    protected LocalDevice createDevice(final InetAddress inetAddr) 
                    throws ValidationException, LocalServiceBindingException, IOException, URISyntaxException {
        // get local address
        String localAddress = props.getProperty("propagator.upnp.device.starturl.protocol", "http") + "://" 
                        + inetAddr.getHostAddress() 
                        + ":" + props.getProperty("propagator.upnp.device.starturl.port", "80") 
                        + "/" + props.getProperty("propagator.upnp.device.starturl.uri", "");
        localAddress = props.getProperty("propagator.upnp.device.starturl.static", localAddress);
        
        // create deviceinfos
        DeviceType type = new UDADeviceType(props.getProperty("propagator.upnp.device.type", "Basic"), 
                        Integer.parseInt(props.getProperty("propagator.upnp.device.typeversion", "1")));
        DeviceDetails details = new DeviceDetails(
                        props.getProperty("propagator.upnp.device.friendlyname", ""),
                        new ManufacturerDetails(props.getProperty("propagator.upnp.manufacturer.name", ""), 
                                        props.getProperty("propagator.upnp.manufacturer.url", "")),
                        new ModelDetails(props.getProperty("propagator.upnp.model.name", ""),
                                        props.getProperty("propagator.upnp.model.desc", ""),
                                        props.getProperty("propagator.upnp.model.version", ""),
                                        props.getProperty("propagator.upnp.model.url", "")), 
                        new URI(localAddress), 
                        null, 
                        null);

        // load icon
        String iconType = props.getProperty("propagator.upnp.icon.type");
        String iconLoc = props.getProperty("propagator.upnp.icon.uri");
        Icon icon = null;
        if (StringUtils.isNotEmpty(iconType) && StringUtils.isNotEmpty(iconLoc)) {
             URI iconRes = null;
            //iconRes = (new DefaultResourceLoader()).getResource(iconLoc).getURI();
            icon = new Icon(iconType, 
                            Integer.parseInt(props.getProperty("propagator.upnp.icon.width", "32")), 
                            Integer.parseInt(props.getProperty("propagator.upnp.icon.height", "32")), 
                            Integer.parseInt(props.getProperty("propagator.upnp.icon.depth", "8")), 
                            iconRes);
        }
        
        // create unique Device
        DeviceIdentity identity = new DeviceIdentity(
                        UDN.uniqueSystemIdentifier(props.getProperty("propagator.upnp.device.friendlyname", "App") 
                                        + inetAddr.getHostAddress()));
        LocalService<?> yaioService = null;
        LocalDevice yaioDevice = new LocalDevice(identity, type, details, icon, yaioService);
        LOGGER.info("create device for " + inetAddr.getHostAddress() 
                        + " with url:" + localAddress + " data:" + yaioDevice);
        return yaioDevice;
    }

    /**
     * <h1>Bereich:</h1>
     *     Tools - CLI-Config
     * <h1>Funktionalitaet:</h1>
     *     konfiguriert die verfuegbaren Base-CLI-Optionen
     * <h1>Nebenwirkungen:</h1>
     *     aktualisiert availiableCmdLineOptions
     * @param availiableCmdLineOptions Options
     */
    protected static void addAvailiableBaseCmdLineOptions(final Options availiableCmdLineOptions) {
        // Config-File
        Option configOption = new Option(null, "config", true,
                "comma separated list of JobConfig property files");
        configOption.setRequired(true);
        availiableCmdLineOptions.addOption(configOption);

        // Hilfe-Option
        Option helpOption = new Option("h", "help", false, "usage");
        helpOption.setRequired(false);
        availiableCmdLineOptions.addOption(helpOption);

        // debug-Option
        Option debugOption = new Option(null, "debug", false, "debug");
        debugOption.setRequired(false);
        availiableCmdLineOptions.addOption(debugOption);
    }
    
    /**
     * <h1>Bereich:</h1>
     *     Tools - CLI-Handling
     * <h1>Funktionalitaet:</h1>
     *     erzeugt aus den CMD-Args ein CLI-Commandline-Object
     * <h1>Nebenwirkungen:</h1>
     *     Rueckgabe als CommandLine
     * @param cmdArgs - Parameter aus z.B. main
     * @param availiableCmdLineOptions - verfuegbare CLI-Optionen
     * @return CommandLine
     * @throws ParseException - pase-Exceptions possible
     */
    protected static CommandLine createCommandLineFromCmdArgs(final String[] cmdArgs,
                                                              final Options availiableCmdLineOptions) throws ParseException {
        CommandLineParser parser = new PosixParser();
        return parser.parse(availiableCmdLineOptions, cmdArgs);
    }
    

    /**
     * <h4>FeatureDomain:</h4>
     *     Configuration
     * <h4>FeatureDescription:</h4>
     *     read the properties from the given filepath (first by filesystem, 
     *     if failed by classpath)
     * <h4>FeatureResult:</h4>
     *   <ul>
     *     <li>returnValue Properties - the properties read from propertyfile
     *   </ul> 
     * <h4>FeatureKeywords:</h4>
     *     Configuration
     * @param filePath - path to the file (filesystem or classressource)
     * @return the properties read from propertyfile
     * @throws Exception - parse/io-Exceptions possible
     */
    protected static Properties readProperties(final String filePath) throws Exception {
        Properties prop = new Properties();
        
        // first try it from fileystem
        try {
            InputStream in = new FileInputStream(new File(filePath));
            prop.load(in);
            in.close();
            //CHECKSTYLE.OFF: IllegalCatch - Much more readable than catching x exceptions
        } catch (Throwable ex) {
            //CHECKSTYLE.ON: IllegalCatch
            // try it from jar
            try {
                InputStream in = Class.class.getResourceAsStream(filePath);
                prop.load(in);
                in.close();
                //CHECKSTYLE.OFF: IllegalCatch - Much more readable than catching x exceptions
            } catch (Throwable ex2) {
                //CHECKSTYLE.ON: IllegalCatch
                throw new Exception("cant read propertiesfile: " + filePath 
                                + " Exception1:" + ex
                                + " Exception2:" + ex2);
            }
        }
        return prop;
    }
}
