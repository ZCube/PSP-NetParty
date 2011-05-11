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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;

import pspnetparty.lib.ICommandHandler;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IServerNetwork;
import pspnetparty.lib.engine.LobbyEngine;
import pspnetparty.lib.engine.PortalEngine;
import pspnetparty.lib.engine.RoomEngine;
import pspnetparty.lib.engine.SearchEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.AsyncUdpServer;
import pspnetparty.lib.socket.IProtocol;

public class AllInOneServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s 올인원 서버  version %sn", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("프로토콜: " + IProtocol.NUMBER);

		String iniFileName = "AllInOneServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("설정 INI 파일명: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 20000);
		if (port < 1 || port > 65535) {
			System.out.println("포토 번호가 부정합니다: " + port);
			return;
		}
		System.out.println("포토: " + port);

		String hostname = settings.get("HostName", "localhost");
		if (Utility.isEmpty(hostname)) {
			System.out.println("호스트명이 설정되어 있지 않습니다: " + hostname);
			return;
		}
		System.out.println("호스트명: " + hostname);

		final String loginMessageFile = settings.get(IniConstants.LOGIN_MESSAGE_FILE, "");
		System.out.println("룸 로그인 메세지 파일 : " + loginMessageFile);

		ILogger logger = ServerUtils.createLogger();

		AsyncTcpServer tcpServer = new AsyncTcpServer(logger, 100000);
		AsyncUdpServer udpServer = new AsyncUdpServer(logger);

		IServerNetwork network = new IServerNetwork() {
			@Override
			public void reload() {
			}

			@Override
			public boolean isValidPortalServer(InetAddress address) {
				return true;
			}
		};

		{
			int maxRooms = settings.get(IniConstants.MAX_ROOMS, 10);
			if (maxRooms < 1) {
				System.out.println("방수가 부정합니다: " + maxRooms);
				return;
			}
			System.out.println("최대 방수: " + maxRooms);

			RoomEngine roomEngine = new RoomEngine(tcpServer, udpServer, logger, network);
			roomEngine.setMaxRooms(maxRooms);
			roomEngine.setLoginMessageFile(loginMessageFile);
		}
		{
			int maxUsers = settings.get(IniConstants.MAX_USERS, 30);
			if (maxUsers < 1) {
				System.out.println("최대 검색 유저수가 부정합니다: " + maxUsers);
				return;
			}
			System.out.println("최대 검색 유저수: " + maxUsers);

			int maxSearchResults = settings.get(IniConstants.MAX_SEARCH_RESULTS, 50);
			if (maxSearchResults < 1) {
				System.out.println("최대 검색 건수가 부정합니다: " + maxSearchResults);
				return;
			}
			System.out.println("최대 검색 건수: " + maxSearchResults);

			int descriptionMaxLength = settings.get(IniConstants.DESCRIPTION_MAX_LENGTH, 100);
			if (descriptionMaxLength < 1) {
				System.out.println("방의 상세·비고의 최대 사이즈가 부정합니다: " + descriptionMaxLength);
				return;
			}
			System.out.println("방의 상세·비고의 최대 문자수: " + descriptionMaxLength);

			SearchEngine searchEngine = new SearchEngine(tcpServer, logger, network);
			searchEngine.setMaxUsers(maxUsers);
			searchEngine.setMaxSearchResults(maxSearchResults);
			searchEngine.setDescriptionMaxLength(descriptionMaxLength);
		}
		{
			LobbyEngine lobbyEngine = new LobbyEngine(tcpServer, logger, network);
			lobbyEngine.setTitle("로비");
			lobbyEngine.setLoginMessageFile(loginMessageFile);
		}

		ini.saveToIni();

		PortalEngine portalEngine = new PortalEngine(tcpServer, logger);
		HashSet<String> addresses = new HashSet<String>();
		addresses.add(hostname + ":" + port);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);
		udpServer.startListening(bindAddress);

		portalEngine.connectRoomServers(addresses);
		portalEngine.connectSearchServers(addresses);
		portalEngine.connectLobbyServers(addresses);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdownnt 서버를 종료시킨다");
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
		udpServer.stopListening();
	}
}
