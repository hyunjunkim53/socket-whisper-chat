package whisperchat;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * [서버 워커 스레드] 클라이언트 1명과 1:1로 연결되어 통신을 담당하는 스레드입니다. * * 핵심 로직: 1. [1단계] 인증 루프:
 * 로그인이 성공할 때까지 채팅을 못하게 가둡니다. 2. [2단계] 채팅 루프: 로그인 성공 후, 메시지(방송)와 귓속말을 처리합니다. 3.
 * [종료] 연결이 끊기면 자원을 정리하고 퇴장을 알립니다.
 */
public class ClientHandler implements Runnable {

	private Socket socket;
	private MemberManager memberManager; // 회원 업무 처리용 (가입, 로그인 검사)
	private WhisperChatServer server; // 채팅 업무 처리용 (방송, 귓속말, 명단관리)

	private String userId = null; // 로그인 성공한 사용자의 ID
	private PrintWriter out;
	private Scanner in;

	// 생성자: 일하는 데 필요한 도구들(소켓, 관리자, 서버본체)을 받아옵니다.
	public ClientHandler(Socket socket, MemberManager manager, WhisperChatServer server) {
		this.socket = socket;
		this.memberManager = manager;
		this.server = server;
	}

	@Override
	public void run() {
		try {
			// 스트림 연결 (대화 통로 개설)
			in = new Scanner(socket.getInputStream());
			out = new PrintWriter(socket.getOutputStream(), true);

			// ========================================================
			// Phase 1. 인증 대기 (로그인 전까지는 여기서 못 나감)
			// ========================================================
			while (true) {
				if (!in.hasNextLine())
					return; // 클라이언트가 강제 종료함

				String line = in.nextLine();
				// 명령어 파싱: "COMMAND arg1 arg2..."
				String[] parts = line.split(" ", 2);
				String command = parts[0];
				String body = (parts.length > 1) ? parts[1] : "";

				if ("LOGIN".equals(command)) {
					// 로그인 성공하면 true 반환 -> 루프 탈출!
					if (doLogin(body))
						break;

				} else if ("REGISTER".equals(command)) {
					doRegister(body);

				} else if ("CHECK_ID".equals(command)) {
					doCheckId(body);

				} else {
					out.println("ERROR 먼저 로그인을 해주세요.");
				}
			}

			// ========================================================
			// Phase 2. 채팅 시작 (로그인 성공 후)
			// ========================================================

			// 1. 서버(본부) 명단에 내 명함(ID, 출력스트림) 등록
			server.addClient(userId, out);

			// 2. 입장 알림 방송
			server.broadcast("SYSTEM", userId + " 님이 입장하셨습니다.");

			// 3. 메시지 수신 루프
			while (in.hasNextLine()) {
				String line = in.nextLine();

				if (line.startsWith("/quit"))
					break; // 종료 명령

				// [과제 핵심: Whisper] /w 아이디 메시지 or WHISPER 아이디 메시지
				if (line.startsWith("WHISPER ")) {
					doWhisper(line);
				} else {
					// 일반 채팅: 모두에게 방송
					server.broadcast("MESSAGE", userId + ": " + line);
				}
			}

		} catch (Exception e) {
			System.out.println("[ClientHandler] 연결 종료 (" + socket.getInetAddress() + ")");
		} finally {
			// ========================================================
			// Phase 3. 뒷정리 (연결 끊김)
			// ========================================================
			if (userId != null) {
				server.removeClient(userId); // 명단에서 삭제
				server.broadcast("SYSTEM", userId + " 님이 퇴장하셨습니다.");
			}
			try {
				socket.close();
			} catch (IOException e) {
			}
		}
	}

	// --- [내부 로직] 인증 처리 ---

	private boolean doLogin(String body) {
		// body: "id pw"
		String[] args = body.split(" ");
		if (args.length < 2)
			return false;

		String id = args[0];
		String pw = args[1];

		// 1. MemberManager에게 검사 맡기기
		boolean isValid = memberManager.login(id, pw);

		if (isValid) {
			// 2. 중복 접속 방지 (이미 접속 중인 ID면 거절)
			if (server.isUserOnline(id)) {
				out.println("LOGIN_FAIL 이미 접속 중인 아이디입니다.");
				return false;
			}

			this.userId = id; // 신분 확정
			out.println("LOGIN_SUCCESS");
			return true;
		} else {
			out.println("LOGIN_FAIL 아이디 또는 비밀번호가 틀립니다.");
			return false;
		}
	}

	private void doRegister(String body) {
		// body: "id pw name email"
		String[] args = body.split(" ");
		if (args.length < 4) {
			out.println("REGISTER_FAIL 입력 형식이 잘못되었습니다.");
			return;
		}
		// MemberManager에게 가입 요청
		boolean success = memberManager.register(args[0], args[1], args[2], args[3]);
		if (success)
			out.println("REGISTER_SUCCESS");
		else
			out.println("REGISTER_FAIL 이미 존재하는 아이디입니다.");
	}

	private void doCheckId(String id) {
		if (memberManager.isUserExists(id))
			out.println("ID_TAKEN");
		else
			out.println("ID_OK");
	}

	// --- [내부 로직] 귓속말 처리 ---

	private void doWhisper(String line) {
		// 프로토콜: WHISPER targetId message...
		// "WHISPER " (8글자) 잘라내고 시작
		String[] parts = line.substring(8).split(" ", 2);

		if (parts.length < 2) {
			out.println("ERROR 귓속말 형식이 틀렸습니다. (WHISPER ID MESSAGE)");
			return;
		}

		String targetId = parts[0];
		String msg = parts[1];

		// 서버 본체에 귓속말 배달 요청
		boolean sent = server.sendWhisper(userId, targetId, msg);

		if (sent) {
			// 보낸 나에게도 확인 메시지 (내가 뭘 보냈는지 알아야 하니까)
			out.println("PRIVATE_SENT To [" + targetId + "]: " + msg);
		} else {
			out.println("ERROR [" + targetId + "] 님을 찾을 수 없습니다.");
		}
	}
}