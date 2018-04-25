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
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocketFactory;

import me.johnnyapol.shane.IRCServer.IRCClient;

public class IRCConnection implements Runnable {

	private Socket socket = null;
	private BufferedReader reader = null;
	private BufferedWriter writer = null;
	
	private String nickName = null;
	
	private IRCServer server = null;
	
	private final Logger log = Logger.getLogger("Shane");
	
	// takes a nick and holds a msg queue for when that client reconnects
	private Map<String, ArrayList<String>> missedMessages = new ConcurrentHashMap<String, ArrayList<String>>();
	// messages excluding PRIVMSG and PINGs, for some reason clients need these to function properly. I'm probably messing something up too
	private List<String> serverMsgs = new CopyOnWriteArrayList<String>();
	
	private String[] channels;
	private String networkName;

	private boolean useSSL = false;
	private String ipAddress;
	private int port;
	private String afkMsg = "";
	
	private boolean isRunning = true;
	
	public IRCConnection(String networkName, String ipAddress, int port, IRCServer server, String nick, String[] channels, boolean useSSL, String afk) throws IOException {
		this.networkName = networkName;
		this.ipAddress = ipAddress;
		this.port = port;
		this.server = server;
		this.nickName = nick;
		this.channels = channels;
		this.useSSL = useSSL;
		this.afkMsg = afk;
		
		this.socket = (useSSL ? (((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(ipAddress, port)) : new Socket(ipAddress, port));
		try {
			init();
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}

	private void init() throws IOException {
		log.info("[" + this.networkName + "] Connecting to: " + socket.getRemoteSocketAddress().toString());
		if (useSSL)
			log.info("[" + this.networkName + "] Using SSL for connection");
		
		this.reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
		this.writer = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
		
		this.sendMesssage("NICK " + this.nickName);
		this.sendMesssage("USER shanebouncer 8 *  : " + this.nickName);
		
		for (String channel : this.channels) {
			this.sendMesssage("JOIN " + channel);
		}
		this.server.setIRCConnection(this);
	}
	
	@Override
	public void run() {
		while (this.isRunning) {
			try {
				Thread.sleep(50);
				String msg = null;
				
				while ((msg = this.reader.readLine()) != null) {
					String[] split = msg.split(" ");
					if (msg.contains("PING")) {
						int dataIndex = 1;
						
						for (int i = 0; i < split.length; i++) {
							if (split[i].equalsIgnoreCase("PING")) {
								dataIndex = i;
								break;
							}
						}
						this.sendMesssage("PONG " + split[dataIndex + 1]);
						continue;
					}
					
					// block WHO responses since they lag us really bad
					if (!msg.contains("PRIVMSG") && !(msg.startsWith(":")  && split[1].equals("352"))) {
						this.serverMsgs.add(msg);
					}
					
					// Afk functionality
					if (msg.contains("PRIVMSG") && msg.contains(this.nickName) && this.server.getConnectedClients().size() == 0) {
						// We're afk, let them know
						String user = msg.split("!")[0].substring(1);
						
						this.sendMesssage("PRIVMSG " + user + " :" + this.afkMsg);
					}
					
					log.info("[" + this.networkName + "] [msg] " + msg);
					
					this.server.distributeMessage(msg);
					
					List<String> nicksOnline = new ArrayList<String>();
					
					for (IRCClient c : this.server.getConnectedClients()) {
						nicksOnline.add(c.getNick());
					}
					
					Iterator<String> clients = this.missedMessages.keySet().iterator();
					while (clients.hasNext()) {
						String nick = clients.next();
						if (!nicksOnline.contains(nick)) {
							// store msg for later
							// block WHO responses, they lag up the place
							if ((msg.startsWith(":")  && split[1].equals("352"))) {
								continue;
							}
							this.missedMessages.get(nick).add(msg);
						}
					}
				}
			} catch (Throwable t) {
				log.log(Level.SEVERE, "Throwable while processing message", t);
				
				if (t instanceof IOException) {
					// we've probably lost connection
					while (true) {
						log.log(Level.WARNING, "[" + this.networkName + "] Lost connection to " + this.socket.getRemoteSocketAddress().toString());
						log.info("[" + this.networkName + "] Attempting to reconnect...");
						
						this.serverMsgs.clear();
						
						try {
							this.socket = (useSSL ? (((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(ipAddress, port)) : new Socket(ipAddress, port));
							while (!this.socket.isConnected()) {
								Thread.sleep(50);
							}
							
							init();
							return; // kill off this thread
						} catch (Exception e) {
							log.log(Level.SEVERE, "[" + this.networkName + "] Failed to connect to " + ipAddress + ", sleeping for 30 seconds and trying again..");
							try {
								Thread.sleep(1000 * 30);
							} catch (InterruptedException e1) {
								// ignored
							}
						}
					}
				}
			}
		}
	}
	
	public void sendMesssage(String msg) throws IOException {
		this.writer.write(msg + "\r\n");
		this.writer.flush();
	}
	
	public void onClientConnect(IRCClient client) {
		for (String msg : this.serverMsgs) {
			try {
				client.sendMessage(msg);
			} catch (IOException e) {
				// TODO: autogenerated
				e.printStackTrace();
			}
		}
		
		ArrayList<String> msgs = this.missedMessages.get(client.getNick());
		
		if (msgs == null) {
			msgs = new ArrayList<String>();
			this.missedMessages.put(client.getNick(), msgs);
			return;
		}
		
		for (String msg : msgs) {
			try {
				client.sendMessage(msg);
			} catch (IOException e) {
				log.log(Level.SEVERE, "[" + this.networkName + "] Lost connection to client " + client.getConnection().getRemoteSocketAddress() + ", an IOException occurred while writing", e);
				this.server.getConnectedClients().remove(client);
			}
		}
		
		// cleanup
		msgs.clear();
	}
	public String getNickName() {
		return this.nickName;
	}
	
	public IRCServer getServer() {
		return this.server;
	}

	public void stop() {
		this.isRunning = false;
		
		try {
			this.sendMesssage("QUIT :ShaneBouncer shutting down!");
			this.socket.close();	
		} catch (IOException e) {
			log.log(Level.SEVERE, "IOException occurred while shutting down connection to: " + this.networkName, e);
		} finally {
			try {
				this.socket.close();
			} catch (IOException e) {
				// ignored
			}
		}
		
		this.getServer().stop();
	}
}
