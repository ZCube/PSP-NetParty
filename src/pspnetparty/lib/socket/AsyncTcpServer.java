﻿/*
Copyright (C) 2011 monte

This file is part of PSP NetParty.

PSP NetParty is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package pspnetparty.lib.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import pspnetparty.lib.ILogger;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;

public class AsyncTcpServer implements IServer {

	private static final int INITIAL_READ_BUFFER_SIZE = 2000;

	private ILogger logger;
	private int maxPacketSize;

	private Selector selector;
	private ConcurrentHashMap<IServerListener, Object> serverListeners;

	private ServerSocketChannel serverChannel;

	private HashMap<String, IProtocol> protocolHandlers = new HashMap<String, IProtocol>();
	private ConcurrentHashMap<Connection, Object> establishedConnections;
	private final Object valueObject = new Object();

	private ByteBuffer bufferProtocolOK = AppConstants.CHARSET.encode(IProtocol.PROTOCOL_OK);
	private ByteBuffer bufferProtocolNG = AppConstants.CHARSET.encode(IProtocol.PROTOCOL_NG);

	private Thread selectorThread;
	private Thread pingThread;

	public AsyncTcpServer(ILogger logger, int maxPacketSize) {
		this.logger = logger;
		this.maxPacketSize = maxPacketSize;
		serverListeners = new ConcurrentHashMap<IServerListener, Object>();
		establishedConnections = new ConcurrentHashMap<Connection, Object>(30, 0.75f, 3);
	}

	@Override
	public void addServerListener(IServerListener listener) {
		serverListeners.put(listener, this);
	}

	@Override
	public void addProtocol(IProtocol handler) {
		protocolHandlers.put(handler.getProtocol(), handler);
	}

	@Override
	public boolean isListening() {
		return selector != null && selector.isOpen();
	}

	@Override
	public void startListening(InetSocketAddress bindAddress) throws IOException {
		if (isListening())
			stopListening();

		selector = Selector.open();
		serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.socket().bind(bindAddress);
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);

		ServerSocket socket = serverChannel.socket();

		for (IServerListener listener : serverListeners.keySet())
			listener.log("TCP: Listening on " + socket.getLocalSocketAddress());

		selectorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				for (IServerListener listener : serverListeners.keySet())
					listener.serverStartupFinished();

				selectorLoop();

				for (IServerListener listener : serverListeners.keySet()) {
					listener.log("TCP: Now shuting down...");
					listener.serverShutdownFinished();
				}
				pingThread.interrupt();
			}
		}, getClass().getName() + " Selector");
		selectorThread.setDaemon(true);
		selectorThread.start();

		pingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					pingLoop();
				} catch (InterruptedException e) {
				}
			}
		}, getClass().getName() + " Ping");
		pingThread.setDaemon(true);
		pingThread.start();
	}

	private void selectorLoop() {
		try {
			while (serverChannel.isOpen())
				while (selector.select() > 0) {
					for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
						SelectionKey key = it.next();
						it.remove();

						try {
							if (key.isAcceptable()) {
								ServerSocketChannel channel = (ServerSocketChannel) key.channel();
								try {
									doAccept(channel);
								} catch (IOException e) {
									key.cancel();
								}
							} else if (key.isReadable()) {
								Connection conn = (Connection) key.attachment();
								boolean success = false;
								try {
									success = conn.doRead();
								} catch (IOException e) {
									// e.fillInStackTrace();
								} catch (RuntimeException e) {
									// e.fillInStackTrace();
								}

								if (!success) {
									conn.disconnect();
									key.cancel();
								}
							}
						} catch (CancelledKeyException e) {
						}
					}
				}
		} catch (IOException e) {
		} catch (ClosedSelectorException e) {
		} catch (RuntimeException e) {
			for (IServerListener listener : serverListeners.keySet())
				listener.log(Utility.stackTraceToString(e));
		}
	}

	private void pingLoop() throws InterruptedException {
		ByteBuffer pingBuffer = ByteBuffer.allocate(IProtocol.HEADER_BYTE_SIZE);
		pingBuffer.putInt(0);
		while (serverChannel.isOpen()) {
			long deadline = System.currentTimeMillis() - IProtocol.PING_DEADLINE;
			// int pingCount = 0, disconnectCount = 0;

			for (Connection conn : establishedConnections.keySet()) {
				try {
					if (conn.lastPingTime < deadline) {
						logger.log(Utility.makePingDisconnectLog("TCP", conn.getRemoteAddress(), deadline, conn.lastPingTime));
						conn.disconnect();
						// disconnectCount++;
					} else {
						pingBuffer.clear();
						conn.channel.write(pingBuffer);
						// pingCount++;
					}
				} catch (RuntimeException e) {
					logger.log(Utility.stackTraceToString(e));
				} catch (Exception e) {
					logger.log(Utility.stackTraceToString(e));
				}
			}

			// logger.log("TCP Server Ping 송신: p=" + pingCount + " d=" +
			// disconnectCount);
			Thread.sleep(IProtocol.PING_INTERVAL);
		}
	}

	@Override
	public void stopListening() {
		if (!isListening())
			return;

		try {
			selector.close();
		} catch (IOException e) {
		}
		// selector = null;

		if (serverChannel != null && serverChannel.isOpen()) {
			try {
				serverChannel.close();
			} catch (IOException e) {
			}
		}
		for (Connection conn : establishedConnections.keySet()) {
			conn.disconnect();
		}
	}

	private void doAccept(ServerSocketChannel serverChannel) throws IOException {
		SocketChannel channel = serverChannel.accept();

		Connection conn = new Connection(channel);

		channel.configureBlocking(false);
		channel.register(selector, SelectionKey.OP_READ, conn);

		establishedConnections.put(conn, valueObject);
	}

	private class Connection implements ISocketConnection {
		private SocketChannel channel;
		private long lastPingTime;

		private IProtocolDriver driver;

		private ByteBuffer headerReadBuffer = ByteBuffer.allocate(IProtocol.HEADER_BYTE_SIZE);
		private ByteBuffer dataReadBuffer = ByteBuffer.allocate(INITIAL_READ_BUFFER_SIZE);
		private PacketData packetData = new PacketData(dataReadBuffer);

		Connection(SocketChannel channel) {
			this.channel = channel;
			lastPingTime = System.currentTimeMillis();
		}

		@Override
		public InetSocketAddress getRemoteAddress() {
			return (InetSocketAddress) channel.socket().getRemoteSocketAddress();
		}

		@Override
		public InetSocketAddress getLocalAddress() {
			return (InetSocketAddress) channel.socket().getLocalSocketAddress();
		}

		@Override
		public boolean isConnected() {
			return channel.isConnected();
		}

		@Override
		public void send(ByteBuffer buffer) {
			if (!channel.isConnected())
				return;

			try {
				ByteBuffer headerData = ByteBuffer.allocate(IProtocol.HEADER_BYTE_SIZE);
				headerData.putInt(buffer.limit());
				headerData.flip();

				ByteBuffer[] array = new ByteBuffer[] { headerData, buffer };
				channel.write(array);
			} catch (IOException e) {
			}
		}

		@Override
		public void send(String message) {
			ByteBuffer buffer = AppConstants.CHARSET.encode(message);
			send(buffer);
		}

		@Override
		public void send(byte[] data) {
			ByteBuffer buffer = ByteBuffer.wrap(data);
			send(buffer);
		}

		@Override
		public void disconnect() {
			if (establishedConnections.remove(this) == null)
				return;

			try {
				if (channel.isOpen())
					channel.close();
			} catch (IOException e) {
			}

			if (driver != null) {
				driver.connectionDisconnected();
				driver = null;
			}
		}

		private boolean doRead() throws IOException {
			if (headerReadBuffer.remaining() != 0) {
				if (channel.read(headerReadBuffer) < 0)
					return false;
				if (headerReadBuffer.remaining() != 0)
					return true;

				int dataSize = headerReadBuffer.getInt(0);
				if (dataSize == 0) {
					lastPingTime = System.currentTimeMillis();
					headerReadBuffer.position(0);
					// logger.log(Utility.makePingLog("TCP Server",
					// getLocalAddress(), getRemoteAddress(), lastPingTime));
					return true;
				}
				// System.out.println("Data size=" + dataSize);
				if (dataSize < 1 || dataSize > maxPacketSize) {
					/* Invalid data size */
					// headerBuffer.position(0);
					// System.out.println(Utility.decode(headerBuffer));
					return false;
				}

				if (dataSize > dataReadBuffer.capacity()) {
					dataReadBuffer = ByteBuffer.allocate(dataSize);
					packetData.replaceBuffer(dataReadBuffer);
				} else {
					dataReadBuffer.limit(dataSize);
				}
			}

			int readBytes = channel.read(dataReadBuffer);
			if (readBytes < 0) {
				/** Client has disconnected */
				return false;
			}

			if (dataReadBuffer.remaining() != 0)
				return true;

			dataReadBuffer.position(0);
			if (driver == null) {
				String message = packetData.getMessage();

				String[] tokens = message.split(IProtocol.SEPARATOR);
				if (tokens.length != 2) {
					bufferProtocolNG.position(0);
					send(bufferProtocolNG);
					return false;
				}

				String protocol = tokens[0];
				String number = tokens[1];

				IProtocol handler = protocolHandlers.get(protocol);
				if (handler == null) {
					bufferProtocolNG.position(0);
					send(bufferProtocolNG);
					return false;
				}

				if (!number.equals(IProtocol.NUMBER)) {
					send(IProtocol.NUMBER);
					return false;
				}

				bufferProtocolOK.position(0);
				send(bufferProtocolOK);

				driver = handler.createDriver(this);
				if (driver == null) {
					return false;
				}
			} else if (driver.process(packetData)) {
			} else {
				return false;
			}

			headerReadBuffer.position(0);
			dataReadBuffer.clear();
			return true;
		}
	}

	public static void main(String[] args) throws IOException {
		InetSocketAddress address = new InetSocketAddress(30000);
		AsyncTcpServer server = new AsyncTcpServer(new ILogger() {
			@Override
			public void log(String message) {
			}
		}, 40000);
		server.addServerListener(new IServerListener() {
			@Override
			public void log(String message) {
				System.out.println(message);
			}

			@Override
			public void serverStartupFinished() {
			}

			@Override
			public void serverShutdownFinished() {
			}
		});
		server.addProtocol(new IProtocol() {
			@Override
			public void log(String message) {
				System.out.println(message);
			}

			@Override
			public String getProtocol() {
				return "TEST";
			}

			@Override
			public IProtocolDriver createDriver(final ISocketConnection connection) {
				System.out.println(connection.getRemoteAddress() + " [접속되었습니다]");

				return new IProtocolDriver() {
					@Override
					public ISocketConnection getConnection() {
						return connection;
					}

					@Override
					public boolean process(PacketData data) {
						String remoteAddress = connection.getRemoteAddress().toString();
						String message = data.getMessage();

						System.out.println(remoteAddress + " (" + message.length() + ")");
						connection.send(message);

						return true;
					}

					@Override
					public void connectionDisconnected() {
						System.out.println(connection.getRemoteAddress() + " [절단 되었습니다]");
					}

					@Override
					public void errorProtocolNumber(String number) {
					}
				};
			}
		});

		server.startListening(address);

		while (System.in.read() != '\n') {
		}

		server.stopListening();
	}
}
