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
import pspnetparty.lib.engine.SearchEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.IProtocol;

public class SearchServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s 검색 서버  version %sn", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("프로토콜: " + IProtocol.NUMBER);

		String iniFileName = "SearchServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("설정 INI 파일명: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 40000);
		if (port < 1 || port > 65535) {
			System.out.println("포토 번호가 부정합니다: " + port);
			return;
		}
		System.out.println("포토: " + port);

		int maxUsers = settings.get(IniConstants.MAX_USERS, 30);
		if (maxUsers < 1) {
			System.out.println("최대 유저수가 부정합니다: " + maxUsers);
			return;
		}
		System.out.println("최대 유저수: " + maxUsers);

		final String loginMessageFile = settings.get(IniConstants.LOGIN_MESSAGE_FILE, "");
		System.out.println("로그인 메세지 파일 : " + loginMessageFile);

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

		ini.saveToIni();

		ILogger logger = ServerUtils.createLogger();
		AsyncTcpServer tcpServer = new AsyncTcpServer(logger, 1000000);

		final SearchEngine engine = new SearchEngine(tcpServer, logger, new IniPublicServer());
		engine.setMaxUsers(maxUsers);
		engine.setDescriptionMaxLength(descriptionMaxLength);
		engine.setMaxSearchResults(maxSearchResults);
		engine.setLoginMessageFile(loginMessageFile);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdownnt 서버를 종료시킨다");
				System.out.println("statusnt 현재의 서버 상태를 표시");
				System.out.println("set MaxUsers 유저수nt최대 유저수를 설정");
				System.out.println("set MaxSearchResults 건수 nt최대 검색 건수를 설정");
				System.out.println("set DescriptionMaxLength 문자수nt방의 소개·비고의 최대 문자수를 설정");
				System.out.println("notify 메세지 nt전원에게 메세지를 고지");
				System.out.println("roomsnt 보관 유지하고 있는 방정보의 일람");
				System.out.println("portal listnt 접속하고 있는 포털 서버의 일람");
				System.out.println("portal acceptnt 포털 등록의 접수 개시");
				System.out.println("portal rejectnt 포털 등록의 접수 정지");
			}
		});
		handlers.put("status", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("포토: " + port);
				System.out.println("유저수: " + engine.getCurrentUsers() + " / " + engine.getMaxUsers());
				System.out.println("등록 방수: " + engine.getRoomEntryCount());
				System.out.println("최대 검색 건수: " + engine.getMaxSearchResults());
				System.out.println("방의 소개·비고의 최대 문자수: " + engine.getDescriptionMaxLength());
				System.out.println("로그인 메세지 파일 : " + loginMessageFile);
				System.out.println("포털 등록: " + (engine.isAcceptingPortal() ?  "접수중" : "정지중"));
			}
		});
		handlers.put("set", new ICommandHandler() {
			@Override
			public void process(String argument) {
				String[] tokens = argument.split(" ");
				if (tokens.length != 2)
					return;

				String key = tokens[0];
				String value = tokens[1];
				if (IniConstants.MAX_USERS.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						if (max < 0)
							return;
						engine.setMaxUsers(max);
						System.out.println("최대 유저수를 " + max + " 로 설정했습니다");
					} catch (NumberFormatException e) {
					}
				} else if (IniConstants.MAX_SEARCH_RESULTS.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						if (max < 1)
							return;
						engine.setMaxSearchResults(max);
						System.out.println("최대 검색 건수를 " + max + " 로 설정했습니다");
					} catch (NumberFormatException e) {
					}
				} else if (IniConstants.DESCRIPTION_MAX_LENGTH.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						if (max < 1)
							return;
						engine.setDescriptionMaxLength(max);
						System.out.println("방의 소개·비고의 최대 문자수를 " + max + " 로 설정했습니다");
					} catch (NumberFormatException e) {
					}
				}
			}
		});
		handlers.put("rooms", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("[전부가게 등록의 일람]");
				System.out.println(engine.allRoomsToString());
			}
		});
		handlers.put("portal", new ICommandHandler() {
			@Override
			public void process(String argument) {
				if ("list".equalsIgnoreCase(argument)) {
					System.out.println("[접속하고 있는 포털 서버의 일람]");
					System.out.println(engine.allPortalsToString());
				} else if ("accept".equalsIgnoreCase(argument)) {
					engine.setAcceptingPortal(true);
					System.out.println("포털 접속의 접수를 개시했습니다");
				} else if ("reject".equalsIgnoreCase(argument)) {
					engine.setAcceptingPortal(false);
					System.out.println("포털 접속의 접수를 정지했습니다");
				}
			}
		});
		handlers.put("notify", new ICommandHandler() {
			@Override
			public void process(String message) {
				if (Utility.isEmpty(message))
					return;

				engine.notifyAllClients(message);
				System.out.println("메세지를 고지했던 : " + message);
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
	}
}
