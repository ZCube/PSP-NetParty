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

import java.net.InetSocketAddress;
import java.util.HashMap;

import pspnetparty.lib.ICommandHandler;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniPublicServer;
import pspnetparty.lib.engine.LobbyEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.IProtocol;

public class LobbyServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s 로비 서버  version %sn", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("프로토콜: " + IProtocol.NUMBER);

		String iniFileName = "LobbyServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("설정 INI 파일명: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 60000);
		if (port < 1 || port > 65535) {
			System.out.println("포토 번호가 부정합니다: " + port);
			return;
		}
		System.out.println("포토: " + port);

		String title = settings.get(IniConstants.LOBBY_TITLE, "로비");
		System.out.println("로비명: " + title);

		final String loginMessageFile = settings.get(IniConstants.LOGIN_MESSAGE_FILE, "");
		System.out.println("로그인 메세지 파일 : " + loginMessageFile);

		ini.saveToIni();

		ILogger logger = ServerUtils.createLogger();
		AsyncTcpServer tcpServer = new AsyncTcpServer(logger, 40000);

		final LobbyEngine engine = new LobbyEngine(tcpServer, logger, new IniPublicServer());
		engine.setTitle(title);
		engine.setLoginMessageFile(loginMessageFile);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdownnt 서버를 종료시킨다");
				System.out.println("statusnt 현재의 서버 상태를 표시");
				System.out.println("set Title 로비명 nt로비명을 설정");
				System.out.println("notify 메세지 nt전원에게 메세지를 고지");
				System.out.println("portal listnt 등록중의 포털 일람");
				System.out.println("portal acceptnt 포털 등록의 접수 개시");
				System.out.println("portal rejectnt 포털 등록의 접수 정지");
			}
		});
		handlers.put("status", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("포토: " + port);
				System.out.println("로비명: " + engine.getTitle());
				System.out.println("유저수: " + engine.getCurrentPlayers());
				System.out.println("로그인 메세지 파일 : " + loginMessageFile);
				System.out.println("포털 등록: " + (engine.isAcceptingPortal() ?  "접수중" : "정지중"));
			}
		});
		handlers.put("notify", new ICommandHandler() {
			@Override
			public void process(String message) {
				if (Utility.isEmpty(message))
					return;

				engine.notifyAllUsers(message);
				System.out.println("메세지를 고지했던 : " + message);
			}
		});
		handlers.put("set", new ICommandHandler() {
			@Override
			public void process(String argument) {
				String[] tokens = argument.split(" ", 2);
				if (tokens.length != 2)
					return;

				String key = tokens[0];
				String value = tokens[1];
				if (IniConstants.LOBBY_TITLE.equalsIgnoreCase(key)) {
					engine.setTitle(value);
					System.out.println("로비명을 " + value + " 로 설정했습니다");
				}
			}
		});
		handlers.put("portal", new ICommandHandler() {
			@Override
			public void process(String argument) {
				String action = argument.trim();
				if ("list".equalsIgnoreCase(action)) {
					System.out.println("[등록중의 포털 서버의 일람]");
					for (InetSocketAddress address : engine.getPortalAddresses()) {
						System.out.println(address.getAddress().getHostAddress() + ":" + address.getPort());
					}
				} else if ("accept".equalsIgnoreCase(action)) {
					engine.setAcceptingPortal(true);
					System.out.println("포털 접속의 접수를 개시했습니다");
				} else if ("reject".equalsIgnoreCase(action)) {
					engine.setAcceptingPortal(false);
					System.out.println("포털 접속의 접수를 정지했습니다");
				}
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
	}
}
