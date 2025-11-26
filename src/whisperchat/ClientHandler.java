package whisperchat;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

// 한 클라이언트를 담당하는 작업 클래스
// Runnable을 구현하므로, 스레드풀에서 execute() 하면
// 이 객체의 run() 메서드가 별도 스레드에서 실행
public class ClientHandler implements Runnable {

	// 해당 스레드가 담당하는 클라이언트와 연결할 소켓
	private Socket socket;
	// 회원가입, 로그인, ID 중복 체크를 맡는 회원 관리 객체
	private MemberManager memberManager;
	// 채팅방 관리를 맡는 메인 서버 객체
	private WhisperChatServer server;

	// 클라이언트와 소통하는 입출력 스트림
	private Scanner in;
	private PrintWriter out;

	// 로그인 성공 후 소켓에 매달릴 사용자 ID 저장
	private String userId;

	public ClientHandler(Socket socket, MemberManager memberManager, WhisperChatServer server) {
		this.socket = socket;
		this.memberManager = memberManager;
		this.server = server;
	}

	// 스레드가 시작되면 가장 먼저 실행되는 메서드
	@Override
	public void run() {
		try {
			in = new Scanner(socket.getInputStream());
			out = new PrintWriter(socket.getOutputStream(), true);

			// 로그인/회원가입 -> 로그인 성공까지 도는 루프
			while (true) {
				if (!in.hasNextLine())
					return;
				String line = in.nextLine();

				// 들어오는 모든 메시지에서 헤더 제거
				if (line.startsWith("<MYP2> ")) {
					line = line.substring(7);
				}

				String[] parts = line.split(" ", 2);
				String command = parts[0];
				String body = (parts.length > 1) ? parts[1] : "";

				// 로그인 성공 시 -> while 문 탈출 -> 채팅 모드 진입
				if ("LOGIN".equals(command)) {
					if (doLogin(body))
						break;
				} else if ("REGISTER".equals(command)) {
					doRegister(body);
				} else if ("CHECK_ID".equals(command)) {
					doCheckId(body);
				} else {
					out.println("<MYP2> ERROR 먼저 로그인을 해주세요.");
				}
			}

			// 채팅 메시지 처리 (로그인 이후)
			server.addClient(userId, out);
			// 전체 사용자에게 해당 사용자가 입장했다고 broadcast
			server.broadcast("SYSTEM", userId + " 님이 입장하셨습니다.");

			while (in.hasNextLine()) {
				String line = in.nextLine();

				// 클라이언트가 보낸 <MYP2> 헤더 제거
				if (line.startsWith("<MYP2> ")) {
					line = line.substring(7);
				}

				// /quit 명령이 들어오면 채팅 루프를 빠져나가고 연결 종료 준비
				if (line.startsWith("/quit"))
					break;

				// WHISPER 대상 메시지 형태면 귓속말 처리
				if (line.startsWith("WHISPER ")) {
					doWhisper(line);
				} else {
					// 그 외에는 일반 채팅 메시지로 간주하여 전체 사용자에게 broadcast
					server.broadcast("MESSAGE", userId + ": " + line);
				}
			}

		} catch (Exception e) {
			System.out.println("[ClientHandler] 연결 종료 (" + socket.getInetAddress() + ")");
		} finally {
			if (userId != null) {
				server.removeClient(userId);
				server.broadcast("SYSTEM", userId + " 님이 퇴장하셨습니다.");
			}
			try {
				socket.close();
			} catch (IOException e) {
			}
		}
	}

	// [LOGIN 처리] MemberManager에 로그인 요청 후, 비밀번호 일치 여부, 이미 접속중인 ID인지 확인
	private boolean doLogin(String body) {
		String[] args = body.split(" ");
		if (args.length < 2)
			return false;
		String id = args[0];
		String pw = args[1];

		// MemberManager에 인증 요청 (비밀번호 hash + salt 검증)
		boolean isValid = memberManager.login(id, pw);

		if (isValid) {
			// 이미 같은 아이디가 로그인 중이면 중복 접속 방지
			if (server.isUserOnline(id)) {
				out.println("<MYP2> LOGIN_FAIL 이미 접속 중인 아이디입니다.");
				return false;
			}
			this.userId = id;
			String name = memberManager.getUserName(id);
			// 프로토콜: LOGIN_SUCCESS <이름>
			out.println("<MYP2> LOGIN_SUCCESS " + name);
			return true;
		} else {
			// 비밀번호 또는 ID 불일치
			out.println("<MYP2> LOGIN_FAIL 아이디 또는 비밀번호가 틀립니다.");
			return false;
		}
	}

	// [REGISTER 처리] MemberManager.register()를 호출하여
	// 중복 ID 여부 확인 + users.dat에 신규 회원 정보 저장
	private void doRegister(String body) {
		String[] args = body.split(" ");
		if (args.length < 4) {
			out.println("<MYP2> REGISTER_FAIL 입력 형식이 잘못되었습니다.");
			return;
		}
		// args[0]=id, args[1]=pw, args[2]=name, args[3]=email
		boolean success = memberManager.register(args[0], args[1], args[2], args[3]);
		if (success)
			out.println("<MYP2> REGISTER_SUCCESS");
		else
			out.println("<MYP2> REGISTER_FAIL 이미 존재하는 아이디입니다.");
	}

	// [CHECK_ID 처리] 클라이언트에서 보내온 ID가 이미 가입되어 있는지 확인
	private void doCheckId(String id) {
		if (memberManager.isUserExists(id))
			out.println("<MYP2> ID_TAKEN");
		else
			out.println("<MYP2> ID_OK");
	}

	// [WHISPER 처리]
	private void doWhisper(String line) {
		// line은 헤더가 제거된 상태 (WHISPER target msg)
		String[] parts = line.substring(8).split(" ", 2);
		if (parts.length < 2) {
			out.println("<MYP2> ERROR 귓속말 형식이 틀렸습니다.");
			return;
		}

		String targetId = parts[0];
		String msg = parts[1];

		// 서버에 귓속말 전송 요청
		boolean sent = server.sendWhisper(userId, targetId, msg);

		if (sent) {
			out.println("<MYP2> PRIVATE_SENT " + targetId + ": " + msg);
		} else {
			out.println("<MYP2> ERROR " + targetId + " 님을 찾을 수 없습니다.");
		}
	}
}