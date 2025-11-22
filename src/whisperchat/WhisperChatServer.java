package whisperchat;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [서버 메인 클래스]
 * 과제 요구사항:
 * 1. 멀티스레드 소켓 프로그래밍 (교수님 제공 ChatServer.java 기반)
 * 2. 여러 클라이언트 동시 처리 (ThreadPool 사용)
 * 3. 귓속말 기능 구현 (접속자 명단 관리)
 */
public class WhisperChatServer {

    private static final int PORT = 59001; // 교수님 코드와 동일한 포트
    
    // [과제 핵심: Whisper]
    // 귓속말을 위해 "누가" 접속해 있는지 알아야 함.
    // (Key: userId, Value: 그 사람에게 말 걸 수 있는 통로(PrintWriter))
    // * ClientHandler들이 동시 접근하므로 'synchronized'로 보호해야 함 *
    private final Map<String, PrintWriter> onlineClients = new HashMap<>();

    // 회원가입, 로그인 등을 처리할 관리자 (서버에 1명만 존재)
    private final MemberManager memberManager;

    public WhisperChatServer() {
        this.memberManager = new MemberManager(); // 회원 관리자 1명 생성
    }

    public void start() {
        System.out.println("[WhisperChatServer] 서버가 " + PORT + " 포트에서 시작됩니다...");
        
        // 멀티스레드 처리를 위한 스레드 풀 생성 (교수님 코드와 동일)
        ExecutorService pool = Executors.newFixedThreadPool(20);

        try (ServerSocket listener = new ServerSocket(PORT)) {
            while (true) {
                // 1. 클라이언트 연결 대기 (교수님 코드와 동일)
                Socket socket = listener.accept();
                
                // 2. 연결이 되면, 새 ClientHandler(일꾼)를 생성하여 스레드 풀에 맡김
                //    (중요!) 일꾼에게 필요한 도구(소켓, 회원관리자, 서버본체)를 넘겨줌
                ClientHandler handler = new ClientHandler(socket, memberManager, this);
                pool.execute(handler);
            }
        } catch (Exception e) {
            System.err.println("[WhisperChatServer] 서버 실행 중 오류: " + e.getMessage());
            pool.shutdown();
        }
    }

    // --- (이하 ClientHandler들이 호출하는) 서버 공용 메서드 ---
    // * 여러 스레드가 동시에 호출하므로 synchronized (줄 세우기) 필수 *

    /**
     * [공용 기능 1] 새 클라이언트를 접속자 명단에 추가
     */
    public synchronized void addClient(String userId, PrintWriter out) {
        onlineClients.put(userId, out);
        System.out.println("[Server] " + userId + " 님이 명단에 추가됨. (현재 " + onlineClients.size() + "명)");
    }

    /**
     * [공용 기능 2] 클라이언트를 접속자 명단에서 제거
     */
    public synchronized void removeClient(String userId) {
        onlineClients.remove(userId);
        System.out.println("[Server] " + userId + " 님이 명단에서 제거됨. (현재 " + onlineClients.size() + "명)");
    }

    /**
     * [공용 기능 3] 이미 접속 중인 ID인지 확인 (로그인 시 중복 방지)
     */
    public synchronized boolean isUserOnline(String userId) {
        return onlineClients.containsKey(userId);
    }

    /**
     * [공용 기능 4] 전체 방송 (MESSAGE 또는 SYSTEM)
     */
    public synchronized void broadcast(String type, String message) {
        String line = type + " " + message;
        // 명단에 있는 모든 사람(PrintWriter)에게 메시지 전송
        for (PrintWriter writer : onlineClients.values()) {
            writer.println(line);
        }
    }

    /**
     * [과제 핵심: Whisper] 귓속말 전송
     * - A(fromId)가 B(toId)에게 메시지를 보냄
     */
    public synchronized boolean sendWhisper(String fromId, String toId, String message) {
        // 명단에서 B(toId)를 찾음
        PrintWriter targetWriter = onlineClients.get(toId);
        
        if (targetWriter != null) {
            // B를 찾았음! -> B에게만 메시지 전송
            // 프로토콜: PRIVATE_FROM [보낸사람ID] [메시지]
            targetWriter.println("PRIVATE_FROM " + fromId + ": " + message);
            return true; // 전송 성공
        } else {
            return false; // 전송 실패 (B가 오프라인이거나 없음)
        }
    }

    // --- 서버 프로그램 실행 ---
    public static void main(String[] args) {
        WhisperChatServer server = new WhisperChatServer();
        server.start();
    }
}
