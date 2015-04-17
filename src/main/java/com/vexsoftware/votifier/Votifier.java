/*
 * Copyright (C) 2012 Vex Software LLC
 * This file is part of Votifier.
 * 
 * Votifier is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Votifier is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Votifier.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.vexsoftware.votifier;

import java.io.*;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.*;

import com.vexsoftware.votifier.crypto.RSAIO;
import com.vexsoftware.votifier.crypto.RSAKeygen;
import com.vexsoftware.votifier.model.ListenerLoader;
import com.vexsoftware.votifier.model.VoteListener;
import com.vexsoftware.votifier.net.VoteReceiver;
import tk.coolv1994.gawdapi.plugin.Plugin;

/**
 * The main Votifier plugin class.
 * 
 * @author Blake Beaupain
 * @author Kramer Campbell
 * @author Vinnie (CoolV1994) - Porting
 */
public class Votifier implements Plugin {

	/** The logger instance. */
	private static final Logger LOG = Logger.getLogger("Votifier");

	/** Log entry prefix */
	private static final String logPrefix = "[Votifier] ";

	/** The Votifier instance. */
	private static Votifier instance;

	/** The current Votifier version. */
	private String version;

	/** The vote listeners. */
	private final List<VoteListener> listeners = new ArrayList<VoteListener>();

	/** The vote receiver. */
	private VoteReceiver voteReceiver;

	/** The RSA key pair. */
	private KeyPair keyPair;

	/** Debug mode flag */
	private boolean debug;

    /** Directory */
    private File dataFolder = new File("./plugins/Votifier");

	/**
	 * Attach custom log filter to logger.
	 */
	static {
		LOG.setFilter(new LogFilter(logPrefix));
	}

    public Votifier() {
        instance = this;
    }

    @Override
	public void startup() {
		// Set the plugin version.
		version = "1.9";

		// Handle configuration.
		if (!dataFolder.exists()) {
            dataFolder.mkdir();
		}
		File config = new File(dataFolder + "/config.properties");
		Properties cfg = new Properties();
		File rsaDirectory = new File(dataFolder + "/rsa");
		// Replace to remove a bug with Windows paths - SmilingDevil
		String listenerDirectory = dataFolder.toString()
				.replace("\\", "/") + "/listeners";

		/*
		 * Use IP address from server.properties as a default for
		 * configurations. Do not use InetAddress.getLocalHost() as it most
		 * likely will return the main server address instead of the address
		 * assigned to the server.
		 */
		String hostAddr = "0.0.0.0";

		/*
		 * Create configuration file if it does not exists; otherwise, load it
		 */
		if (!config.exists()) {
			try {
				// First time run - do some initialization.
				LOG.info("Configuring Votifier for the first time...");

				// Initialize the configuration file.
				config.createNewFile();

				cfg.setProperty("host", hostAddr);
				cfg.setProperty("port", "8192");
				cfg.setProperty("debug", "false");

				/*
				 * Remind hosted server admins to be sure they have the right
				 * port number.
				 */
				LOG.info("------------------------------------------------------------------------------");
				LOG.info("Assigning Votifier to listen on port 8192. If you are hosting Minecraft on a");
				LOG.info("shared server please check with your hosting provider to verify that this port");
				LOG.info("is available for your use. Chances are that your hosting provider will assign");
				LOG.info("a different port, which you need to specify in config.txt");
				LOG.info("------------------------------------------------------------------------------");

				cfg.setProperty("listener_folder", listenerDirectory);
				cfg.store(new FileOutputStream(config), "Votifier Config");
			} catch (Exception ex) {
				LOG.log(Level.SEVERE, "Error creating configuration file", ex);
				gracefulExit();
				return;
			}
		} else {
			// Load configuration.
            try {
                cfg.load(new FileInputStream(config));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

		/*
		 * Create RSA directory and keys if it does not exist; otherwise, read
		 * keys.
		 */
		try {
			if (!rsaDirectory.exists()) {
				rsaDirectory.mkdir();
				new File(listenerDirectory).mkdir();
				keyPair = RSAKeygen.generate(2048);
				RSAIO.save(rsaDirectory, keyPair);
			} else {
				keyPair = RSAIO.load(rsaDirectory);
			}
		} catch (Exception ex) {
			LOG.log(Level.SEVERE,
					"Error reading configuration file or RSA keys", ex);
			gracefulExit();
			return;
		}

		// Load the vote listeners.
		listenerDirectory = cfg.getProperty("listener_folder");
		listeners.addAll(ListenerLoader.load(listenerDirectory));

		// Initialize the receiver.
		String host = cfg.getProperty("host", hostAddr);
		int port = Integer.parseInt(cfg.getProperty("port", "8192"));
		debug = Boolean.parseBoolean(cfg.getProperty("debug"));
        if (debug)
			LOG.info("DEBUG mode enabled!");

		try {
			voteReceiver = new VoteReceiver(this, host, port);
			voteReceiver.start();

			LOG.info("Votifier enabled.");
		} catch (Exception ex) {
			gracefulExit();
			return;
		}
	}

    @Override
	public void shutdown() {
		// Interrupt the vote receiver.
		if (voteReceiver != null) {
			voteReceiver.shutdown();
		}
		LOG.info("Votifier disabled.");
	}

	private void gracefulExit() {
		LOG.log(Level.SEVERE, "Votifier did not initialize properly!");
	}

	/**
	 * Gets the instance.
	 * 
	 * @return The instance
	 */
	public static Votifier getInstance() {
		return instance;
	}

	/**
	 * Gets the version.
	 * 
	 * @return The version
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Gets the listeners.
	 * 
	 * @return The listeners
	 */
	public List<VoteListener> getListeners() {
		return listeners;
	}

	/**
	 * Gets the vote receiver.
	 * 
	 * @return The vote receiver
	 */
	public VoteReceiver getVoteReceiver() {
		return voteReceiver;
	}

	/**
	 * Gets the keyPair.
	 * 
	 * @return The keyPair
	 */
	public KeyPair getKeyPair() {
		return keyPair;
	}

	public boolean isDebug() {
		return debug;
	}

}
