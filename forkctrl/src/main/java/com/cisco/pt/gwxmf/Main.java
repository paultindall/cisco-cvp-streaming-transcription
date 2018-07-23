package com.cisco.pt.gwxmf;

import org.apache.commons.cli.*;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        CommandLine cli = parseArgs(args);
        String gateways = cli.getOptionValue('g');
        String address = cli.getOptionValue('a');
        int port = Integer.parseInt(cli.getOptionValue('p'));
        logger.info("Starting embedded web server on port {}", port);

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.setConnectors(new Connector[]{connector});

        ServletContextHandler contextHandler = new ServletContextHandler();
        contextHandler.setContextPath("/forkctrl/");
        server.setHandler(contextHandler);

        ServletHolder servletHolder = new ServletHolder(Forking.class);
        servletHolder.setInitOrder(0);
        servletHolder.setInitParameter("GatewayHostList", gateways);
        servletHolder.setInitParameter("ListenAddress", address);
        servletHolder.setInitParameter("ListenPort", Integer.toString(port));
        contextHandler.addServlet(servletHolder, "/");
        try {
            server.start();
        } catch (Exception e) {
            logger.error("Server start failed", e);
            System.exit(1);
        }

        server.join();
    }

    private static CommandLine parseArgs(String[] args) {
        Option gateways = new Option("g", "gateways", true, "Comma-separated list of gateways");
        Option address = new Option("a", "listen-address", true, "Listen address");
        Option port = new Option("p", "listen-port", true, "Listen port");

        gateways.setRequired(true);
        address.setRequired(true);
        port.setRequired(true);

        Options options = new Options().addOption(gateways).addOption(address).addOption(port);
        CommandLineParser parser = new DefaultParser();
        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("forkctrl", options);
            System.exit(1);
            return null;
        }
    }
}
