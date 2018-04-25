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

import java.util.logging.Logger;

public class ServerConfig {
	private String ipAddress;
	private int port = 6667;
	private int bouncerPort = 6667;
	private String nick;
	private String[] channels = new String[] { };
	private boolean useSSL = false;
	
	private final static Logger log = Logger.getLogger("Shane");
	
	public ServerConfig(String ipAddress, int port, int bouncerPort, String nick, String[] channels) {
		this.ipAddress = ipAddress;
		this.port = port;
		this.bouncerPort = bouncerPort;
		this.nick = nick;
		this.channels = channels;
	}
	
	public ServerConfig(String[] lines) {
		for (String line : lines) {
			line = line.trim();
			
			if (!line.contains("=") && !line.startsWith("#")) {
				log.warning("[config] invalid server config line: " + line);
				continue;
			}
			
			String[] split = line.split("=");
			String key = split[0];
			
			if (key.equalsIgnoreCase("ip") || key.equalsIgnoreCase("address")) {
				this.ipAddress = split[1];
				continue;
			}
			
			if (key.equalsIgnoreCase("port")) {
				this.port = Integer.parseInt(split[1]);
				continue;
			}
			
			if (key.equalsIgnoreCase("nick") || key.equalsIgnoreCase("nickname")) {
				this.nick = split[1];
				continue;
			}
			
			if (key.equalsIgnoreCase("channels")) {
				String cs = split[1];
				
				if (!cs.contains(",")) {
					this.channels = new String[] { cs };
					continue;
				}
				
				this.channels = cs.split(",");
				continue;
			}
			
			if (key.equalsIgnoreCase("bouncer-port")) {
				this.bouncerPort = Integer.parseInt(split[1]);
				continue;
			}
			
			if (key.equalsIgnoreCase("use-ssl")) {
				this.useSSL = Boolean.parseBoolean(split[1]);
				continue;
			}
			
			log.warning("[config] invalid server config line: " + line);
			continue;
		}
	}
	
	public String getIpAddress() {
		return this.ipAddress;
	}
	
	public int getPort() {
		return this.port;
	}
	
	public String getNickname() {
		return this.nick;
	}
	
	public String[] getChannels() {
		return this.channels;
	}

	public int getBouncerPort() {
		return this.bouncerPort;
	}

	public boolean getUseSSL() {
		return this.useSSL;
	}
}