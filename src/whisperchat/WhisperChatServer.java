package whisperchat;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * [채팅 서버 메인 클래스]
 * 클라이언트 접속을 받아서 ClientHandler에게 맡김
 * 현재 접속 중인 클라이언트 목록을 관리하고, 
 * broadcast / 귓속말(sendWhisper) 기능을 제공
 * 회원 정보는 MemberManager를 통해 처리
 */
public class WhisperChatServer {

	// 서버 포트 번호
	private static final int PORT = 59001;

	// 접속 중인 사용자 목록 (userId -> 출력 스트림)
	private final Map<String, PrintWriter> onlineClients = new HashMap<>();

	private final MemberManager memberManager;

	public WhisperChatServer() {
		this.memberManager = new MemberManager();
	}

	/*
	 * 서버 시작 ServerSocket으로 PORT에서 접속 대기 클라이언트가 접속할 때마다 ClientHandler를 만들어 스레드풀에 맡김
	 */
	public void start() {
		System.out.println("[WhisperChatServer] 서버가 " + PORT + " 포트에서 시작됩니다...");
		ExecutorService pool = Executors.newFixedThreadPool(20);

		try (ServerSocket listener = new ServerSocket(PORT)) {
			while (true) {
				Socket socket = listener.accept();
				ClientHandler handler = new ClientHandler(socket, memberManager, this);
				pool.execute(handler);
			}
		} catch (Exception e) {
			System.err.println("[WhisperChatServer] 서버 실행 중 오류: " + e.getMessage());
			pool.shutdown();
		}
	}

	/*
	 * 새 클라이언트 추가 userId와 그 사용자의 PrintWriter를 onlineClients에 등록 synchronized: 여러
	 * 스레드가 동시에 접속/퇴장할 수 있으므로 동기화
	 */
	public synchronized void addClient(String userId, PrintWriter out) {
		onlineClients.put(userId, out);
		System.out.println("[Server] " + userId + " 접속. (현재 " + onlineClients.size() + "명)");
	}

	// 사용자가 나가면 onlineClients에서 제거
	public synchronized void removeClient(String userId) {
		onlineClients.remove(userId);
		System.out.println("[Server] " + userId + " 퇴장. (현재 " + onlineClients.size() + "명)");
	}

	// 해당 ID가 현재 접속 중인지 여부 확인 (중복 로그인 방지용)
	public synchronized boolean isUserOnline(String userId) {
		return onlineClients.containsKey(userId);
	}

	/*
	 * broadcast type: MESSAGE / SYSTEM 등 메시지 종류 message: 실제 내용 여기에서 <MYP2> 헤더를 한 번만
	 * 붙여서, 모든 클라이언트에게 뿌려줌
	 */
	public synchronized void broadcast(String type, String message) {
		String line = "<MYP2> " + type + " " + message;
		for (PrintWriter writer : onlineClients.values()) {
			writer.println(line);
		}
	}

	/*
	 * 귓속말 전송 fromId: 보낸 사람 toId: 받을 사람 message: 내용 대상이 접속 중이면 PRIVATE_FROM 메시지를 한 번
	 * 보내고 true 반환 대상이 없으면 false 반환
	 */
	public synchronized boolean sendWhisper(String fromId, String toId, String message) {
		PrintWriter targetWriter = onlineClients.get(toId);

		if (targetWriter != null) {
			targetWriter.println("<MYP2> PRIVATE_FROM " + fromId + ": " + message);
			return true;
		} else {
			return false;
		}
	}

	public static void main(String[] args) {
		WhisperChatServer server = new WhisperChatServer();
		server.start();
	}
}