/*
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
package pspnetparty.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;

import pspnetparty.lib.ICommandHandler;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniPublicServer;
import pspnetparty.lib.engine.PortalEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.IProtocol;

public class PortalServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s 포털 서버  version %sn", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("프로토콜: " + IProtocol.NUMBER);

		String iniFileName = "PortalServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("설정 INI 파일명: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 50000);
		if (port < 1 || port > 65535) {
			System.out.println("포토 번호가 부정합니다: " + port);
			return;
		}
		System.out.println("포토: " + port);

		ini.saveToIni();

		ILogger logger = ServerUtils.createLogger();
		AsyncTcpServer tcpServer = new AsyncTcpServer(logger, 40000);

		final PortalEngine engine = new PortalEngine(tcpServer, logger);

		connect(engine);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdownnt 서버를 종료시킨다");
				System.out.println("statusnt 현재의 서버 상태를 표시");
				System.out.println("roomsnt 보관 유지하고 있는 방정보의 일람");
				System.out.println("server activent 접속중의 서버의 일람");
				System.out.println("server deadnt 접속하고 있지 않는 서버의 일람");
				System.out.println("server reloadnt 서버 리스트를 재독 보고 붐비어 해 접속을 갱신한다");
				System.out.println("reconnectnt 접속하고 있지 않는 서버와 재접속을 시도한다");
			}
		});
		handlers.put("status", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("포토: " + port);
				System.out.println(engine.statusToString());
			}
		});
		handlers.put("rooms", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println(engine.allRoomsToString());
			}
		});
		handlers.put("server", new ICommandHandler() {
			@Override
			public void process(String argument) {
				if ("active".equalsIgnoreCase(argument)) {
					System.out.println("[접속중의 룸 서버의 일람]");
					printList(engine.listActiveRoomServers());
					System.out.println("[접속중의 검색 서버의 일람]");
					printList(engine.listActiveSearchServers());
					System.out.println("[접속중의 로비 서버의 일람]");
					printList(engine.listActiveLobbyServers());
				} else if ("dead".equalsIgnoreCase(argument)) {
					System.out.println("[절단 된 룸 서버의 일람]");
					printList(engine.listDeadRoomServers());
					System.out.println("[절단 된 검색 서버의 일람]");
					printList(engine.listDeadSearchServers());
					System.out.println("[절단 된 로비 서버의 일람]");
					printList(engine.listDeadLobbyServers());
				} else if ("reload".equalsIgnoreCase(argument)) {
					try {
						connect(engine);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					return;
				}
			}
		});
		handlers.put("reconnect", new ICommandHandler() {
			@Override
			public void process(String argument) {
				engine.reconnectNow();
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
	}

	private static void printList(String[] list) {
		for (String s : list)
			System.out.println(s);
	}

	private static void connect(PortalEngine engine) throws IOException {
		IniPublicServer publicServer = new IniPublicServer();
		HashSet<String> addresses = new HashSet<String>();

		for (String address : publicServer.getRoomServers()) {
			addresses.add(address);
		}
		engine.connectRoomServers(addresses);

		addresses.clear();
		for (String address : publicServer.getSearchServers()) {
			addresses.add(address);
		}
		engine.connectSearchServers(addresses);

		addresses.clear();
		for (String address : publicServer.getLobbyServers()) {
			addresses.add(address);
		}
		engine.connectLobbyServers(addresses);
	}
}
