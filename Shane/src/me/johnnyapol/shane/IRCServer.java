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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLServerSocketFactory;

public class IRCServer implements Runnable {

	private static final Logger logger = Logger.getLogger("Shane");
	
	private volatile int numClients = 0;
	private final String PASSWORD;
	
	private List<IRCClient> connectedClients = new CopyOnWriteArrayList<IRCClient>();
	private IRCConnection ircServer;
	
	private int port = 6667;
	private boolean useSSL = false;
	
	private boolean isRunning = true;
	/**
	 * Constructs a new IRCServer instance, which is where clients connect to be proxied into the server
	 * @param port The port at which the bouncer should run on
	 * @param _WORD The authentication password that new clients must provide
	 * @param enableBouncerSSL 
	 */
	public IRCServer(int port, String _WORD, boolean enableBouncerSSL) {
		this.port = port;
		this.PASSWORD = _WORD;
		this.useSSL = enableBouncerSSL;
	}
	
	/** 
	 * Called to set the particular IRCConnection instance that this bouncer is responsible for
	 * @param s The IRCConnection instance, representing the server that this particular bouncer is proxy-ing to
	 */
	public void setIRCConnection(IRCConnection s) {
		this.ircServer = s;
	}
	
	/** 
	 * Represents an IRC client connection to the bouncer. Enforces authentication standards and proxys data server <-> client
	 * @author john
	 */
	class IRCClient implements Runnable {
		private int clientId;
		private Socket connection;
		
		private BufferedReader reader = null;
		private BufferedWriter writer = null;
		
		private boolean isConnected = true;
		private boolean hasAuthenticated = false;
		
		private String nick = "default";
		private int authAttempts = 0;
		
		public IRCClient(int clientId, Socket connection) throws IOException {
			this.clientId = clientId;
			this.connection = connection;
			
			this.reader = new BufferedReader(new InputStreamReader(this.connection.getInputStream()));
			this.writer = new BufferedWriter(new OutputStreamWriter(this.connection.getOutputStream()));
		}
		
		public int getID() {
			return this.clientId;
		}
		
		public Socket getConnection() {
			return this.connection;
		}
		
		public void sendMessage(String msg) throws IOException {
			this.writer.write(msg + "\r\n");
			this.writer.flush();
		}
		
		
		@Override
		public void run() {
			while (IRCServer.this.isRunning || this.isConnected) {
				String msg = null;
				
				try {
					Thread.sleep(100);
					while ((msg = reader.readLine()) != null) {
						// Check if the client is authenticated, if not, their actions are basically restricted to authenticating and setting their nick
						logger.info("[client#" + this.clientId + "] msg: " + msg);
						if (!hasAuthenticated) {
							if (msg.toLowerCase().contains("password") || (msg.toLowerCase().contains("msg") && msg.toLowerCase().contains("bouncer"))) {
								String[] split = msg.split(" ");
								// Password check
								for (String s : split) {
									if (s.equals(PASSWORD) || s.equals(":" + PASSWORD)) {
										this.hasAuthenticated = true;
										this.sendMessage(":irc.shane.net 002 " + nick + " Thanks for authenticating! You are now connected!");
										IRCServer.this.connectedClients.add(this);
										IRCServer.logger.info("[ircserver] Client " + this.connection.getRemoteSocketAddress().toString() + " has authenticated succesfully, under nickname " + this.nick);
										IRCServer.this.ircServer.onClientConnect(this);
										break;
									} 
								}
								
								if (!hasAuthenticated) {
									this.sendMessage(":irc.shane.net 372 " + nick + " Wrong password! Please try again!");
									authAttempts++;
									
									if (authAttempts >= 3) {
										this.sendMessage(":irc.shane.net 372" + nick + " Too many auth attempts! Goodbye!");
										IRCServer.logger.warning("[ircserver] Too many failed authentication attempts from: " + this.connection.getRemoteSocketAddress().toString() + ", disconnecting!");
										this.connection.close();
										return;
									}
								}		
								continue;
							}
							// set the clients nick
							if (msg.contains("NICK")) {
								this.nick = msg.split(" ")[1];
							}
							
							continue;
						}
						
						// check to avoid parts caused by clients being closed
						if (msg.startsWith("PART")) {
							// ignored
							continue;
						}
						
						if (msg.startsWith("QUIT")) {
							// terminate connection
							logger.info("[ircserver] Client #" + this.clientId + " is parting!");
							IRCServer.this.connectedClients.remove(this);
							this.connection.close();
							continue;
						}
						
						// proxy the client's request
						IRCServer.this.ircServer.sendMesssage(msg);
					}
				} catch (Exception e) {
					logger.log(Level.SEVERE, "[ircserver] Lost connection to client: " + this.connection.getRemoteSocketAddress().toString(), e);
					break;
				}
			}	
			this.isConnected = false;
			// Cleanup 
			try {
				this.connection.close();
			} catch (Throwable t) { // ignored
			}
		}
		
		public boolean isConnected() {
			return this.isConnected;
		}
		
		public boolean hasAuthenticated() {
			return this.hasAuthenticated;
		}
		
		public String getNick() {
			return this.nick;
		}

		public void disconnect() {
			this.isConnected = false;
			
		}
	}
	
	@Override
	public void run() {
		ServerSocket socket;
		try {
			socket = (useSSL ? (SSLServerSocketFactory.getDefault().createServerSocket(port)) : new ServerSocket(this.port))	;
		} catch (IOException e) {
			this.isRunning = false;
			logger.log(Level.SEVERE, "Failed to create ServerSocket instance, aborting launch.", e);
			return;
		}
		
		while (this.isRunning) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e1) {
				// ignored
			}
			try {
				Socket s = socket.accept();
				// apply some socket options
				s.setTcpNoDelay(true);
				s.setKeepAlive(true);
				numClients++;
				logger.info("[ircserver] Got connection id " + numClients + " from " +  s.getRemoteSocketAddress());
				IRCClient client = new IRCClient(numClients, s);
				
				// Send our beautiful MOTD
				// TODO: Am I even doing this right? 
				client.sendMessage(":irc.shane.net 001 newClient Hello! Welcome to Shane!");
				client.sendMessage(":irc.shane.net 002 newClient Your host is shanebouncer, running version 1.0");
				client.sendMessage(":irc.shane.net 003 newClient Please type /password <pass> OR /msg bouncer <password> to authenticate.");
				
				Thread thread = new Thread(client, "Client-" + numClients);
				thread.start();
			} catch (IOException e) {
				logger.log(Level.SEVERE, "IOException while performing handshake with client #" + this.numClients, e);
			}
		}
		
		// Cleanup
		try {
			socket.close();
		} catch (IOException e) {
			// ignored
		}
	}

	/**
	 * Sends a message to all **authenticated** clients, usually just used to echo what was received from the IRC server"
	 * If any IOException occurs, the connection is assumed to be dead and the orphaned client will be removed from the connected list
	 * @param msg The message to be sent
	 */
	public void distributeMessage(String msg) {
		for (IRCClient client : this.connectedClients) {
			try {
				client.sendMessage(msg);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Lost connection to client: " + client.getConnection().getRemoteSocketAddress().toString(), e);
				this.connectedClients.remove(client);
				
				if (this.connectedClients.size() == 0) {
					try {
						this.ircServer.sendMesssage("NICK " + this.ircServer.getNickName() + " afk");
					} catch (IOException e1) {
						logger.log(Level.WARNING, "IOException occurred while setting afk status", e1);
					}
				}
				break;
			}
		}
	}

	/**
	 * @return the list of connected clients
	 */
	public List<IRCClient> getConnectedClients() {
		return this.connectedClients;
	}
	
	public void stop() {
		this.isRunning = false;
		
		// Kick all clients off
		this.distributeMessage(":irc.shane.net 372 bouncer Bouncer is shutting down! Goodbye!");
		for (IRCClient client : this.connectedClients) {
			client.disconnect();
		}
	}
	
	public boolean isRunning() {
		return this.isRunning;
	}
}
