/** 
 * Copyright (c) 2018 John Christopher Allwein (johnnyapol)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package me.johnnyapol.shane;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class Main {

	private static final Logger log = Logger.getLogger("Shane");
	
	public static void main(String[] args) throws IOException {
		final long start = System.currentTimeMillis();
		log.info("*** starting shane v1.0 by github.com/johnnyapol ***");
		log.info(new Date(start).toString());
		
		
		log.info("[config] loading shane.cfg");
		
		File cfg = new File("shane.cfg");
		
		if (!cfg.exists()) {
			log.warning("[config] shane.cfg does not exist. Generating new configuration file from defaults");
			log.warning("[config] After the configuration file has been generated, the process will terminate. Please edit these options to your liking and restart the service.");
			writeCfg(cfg);
			
			System.exit(0);
		}
		
		List<IRCConnection> connections = loadCfg(cfg);
		
		// main command loop
		String line = "";
		BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
		
		while ((line = console.readLine()) != null) {
			if (line.equalsIgnoreCase("stop")) {
				log.info("[shane] received stop command, shutting down!");
				
				for (IRCConnection connection : connections) {
					connection.stop();
				}
				
				break;
			}
			
			if (line.equalsIgnoreCase("stats")) {
				log.info("[shane] up since: " + new Date(start));
				continue;
			}
		}
		console.close();
	}
	
	
	public static void writeCfg(File cfg) throws IOException {
		String data = new StringBuilder()
				.append("#Shane IRC Bouncer Configuration File" + System.lineSeparator())
				.append("# General Settings" + System.lineSeparator())
				.append("afk-msg=Sorry! I'm currently away from my computer right now. I'll get back to you as soon as I can." + System.lineSeparator())
				.append("password=shanebouncer" + (Math.random() * Math.random() * 10000) + System.lineSeparator())
				.append("bouncer-ssl-enable=false" + System.lineSeparator())
				.append("#The following only need to be changed if you intend on using SSL on your bouncer." + System.lineSeparator())
				.append("bouncer-ssl-keystore=path" + System.lineSeparator())
				.append("bouncer-ssl-password=password" + System.lineSeparator())
				.append("#IRC networks are denoted by a [network name] and ended with an [end] block" + System.lineSeparator())
				.append("[freenode]" + System.lineSeparator())
				.append("	ip=irc.freenode.net" + System.lineSeparator())
				.append("	port=6667" + System.lineSeparator())
				.append("	use-ssl=false" + System.lineSeparator())
				.append("	nick=shanebouncer" + System.lineSeparator())
				.append("	channels=##networking,#general" + System.lineSeparator())
				.append("	bouncer-port=6667" + System.lineSeparator())
				.append("[end]").toString();
		
		FileWriter fWriter = new FileWriter(cfg);
		fWriter.write(data);
		fWriter.close();
	}
	
	public static List<IRCConnection> loadCfg(File cfg) throws IOException {
		String afk = null, password = null;
		boolean enableBouncerSSL = false;
		
		Map<String, ServerConfig> serverConfigs = new HashMap<String, ServerConfig>();
		
		BufferedReader reader = new BufferedReader(new FileReader(cfg));
		String line;
		
	    List<String> serverLines = new ArrayList<String>();
	    boolean isServer = false;
	    String serverName = "";
	    
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.startsWith("#")) 
				continue;
			
			if (isServer) {
				if (line.equalsIgnoreCase("[end]")) {
					isServer = false;
					String[] lines = new String[serverLines.size()];
					serverConfigs.put(serverName, new ServerConfig(serverLines.toArray(lines)));
					serverLines.clear();
					serverName = "";
					continue;
				}
				serverLines.add(line);
				continue;
			}
			
			if (line.startsWith("[")) {
				isServer = true;
				serverName = line.substring(1, line.length() - 1);
				continue;
			}
			
			// Should be general props
			if (line.contains("=")) {
				String[] split = line.split("=");
				
				String key = split[0];
				String value = split[1];
				
				if (key.equalsIgnoreCase("afk-msg")) {
					afk = value;
					continue;
				}
				
				if (key.equalsIgnoreCase("password")) {
					password = value;
					continue;
				}
				
				if (key.equalsIgnoreCase("bouncer-ssl-enable")) {
					enableBouncerSSL = Boolean.parseBoolean(value);
					continue;
				}
				
				if (key.equalsIgnoreCase("bouncer-ssl-keystore") && enableBouncerSSL) {
					System.setProperty("javax.net.ssl.keyStore", value);
					continue;
				}
				
				if (key.equalsIgnoreCase("bouncer-ssl-password") && enableBouncerSSL) {
					System.setProperty("javax.net.ssl.keyStorePassword", value);
					continue;	
				}
			}
			
			log.warning("[cfg] unrecognized input: " + line);
		}
		
		reader.close();
		
		Iterator<String> servers = serverConfigs.keySet().iterator();
		
		List<IRCConnection> connections = new ArrayList<IRCConnection>();
		
		while (servers.hasNext()) {
			String name = servers.next();
			
			log.info("[core] Connecting to " + name);
			
			ServerConfig server_cfg = serverConfigs.get(name);
			IRCServer server = new IRCServer(server_cfg.getBouncerPort(), password, enableBouncerSSL);	
			IRCConnection connection = new IRCConnection(name, server_cfg.getIpAddress(), server_cfg.getPort(), server, server_cfg.getNickname(), server_cfg.getChannels(), server_cfg.getUseSSL(), afk);
			new Thread(server).start();
			new Thread(connection).start();
			
			connections.add(connection);
		}	
		
		return connections;
	}
}
