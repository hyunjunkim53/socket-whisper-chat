package whisperchat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * [메인 채팅 클라이언트]
 * 과제 요구사항:
 * 1. GUI 구현 (채팅창, 입력창, 귓속말 대상 선택창)
 * 2. 쓰레드 구현 (서버 메시지 수신용 Reader Thread)
 * 3. Whisper 프로토콜 지원 (GUI를 통해 대상 지정)
 */
public class WhisperChatClient extends JFrame {

    // 통신 도구 (LoginGUI로부터 물려받음)
    private Socket socket;
    private Scanner in;
    private PrintWriter out;
    private String myId;

    // GUI 컴포넌트
    private JTextArea messageArea;  // 채팅 내용 보여주는 곳
    private JTextField targetField; // [과제 요구사항] 귓속말 대상 입력 (GUI Control)
    private JTextField inputField;  // 메시지 입력
    private JButton sendButton;     // 전송 버튼

    /**
     * 생성자: LoginGUI에서 로그인 성공 후 호출됨
     * 이미 연결된 소켓(socket, in, out)을 전달받아 대화를 이어감.
     */
    public WhisperChatClient(Socket socket, Scanner in, PrintWriter out, String myId) {
        super("WhisperChat - " + myId); // 창 제목에 내 ID 표시
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.myId = myId;

        buildGUI();
        
        // 창 설정
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 400);
        setLocationRelativeTo(null); // 화면 중앙
        setVisible(true);

        // [중요] 서버 메시지를 듣는 "수신 전용 스레드" 시작
        startReaderThread();
    }

    private void buildGUI() {
        setLayout(new BorderLayout());

        // 1. 채팅 내용 영역 (수정 불가, 스크롤 가능)
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        messageArea.setLineWrap(true); // 자동 줄바꿈
        messageArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        add(new JScrollPane(messageArea), BorderLayout.CENTER);

        // 2. 하단 입력 패널 (귓속말 대상 + 메시지 + 버튼)
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // [과제 요구사항] 귓속말 대상을 정하는 GUI 컨트롤
        JPanel targetPanel = new JPanel(new BorderLayout());
        targetPanel.add(new JLabel("To: "), BorderLayout.WEST);
        targetField = new JTextField(8); // 8글자 정도 크기
        targetField.setToolTipText("비워두면 전체 채팅, ID를 적으면 귓속말");
        targetPanel.add(targetField, BorderLayout.CENTER);

        bottomPanel.add(targetPanel, BorderLayout.WEST);

        // 메시지 입력 필드
        inputField = new JTextField();
        bottomPanel.add(inputField, BorderLayout.CENTER);

        // 전송 버튼
        sendButton = new JButton("Send");
        bottomPanel.add(sendButton, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // 이벤트 리스너 등록
        ActionListener sendAction = e -> sendMessage();
        inputField.addActionListener(sendAction); // 엔터키 처리
        sendButton.addActionListener(sendAction); // 클릭 처리
    }

    /**
     * [핵심 로직] 메시지 전송
     * targetField에 값이 있으면 "귓속말", 없으면 "전체말"로 판단
     */
    private void sendMessage() {
        String msg = inputField.getText();
        String target = targetField.getText().trim();

        if (msg.isEmpty()) return;

        if (target.isEmpty()) {
            // 1. 전체 채팅 (그냥 메시지 전송)
            out.println(msg);
        } else {
            // 2. 귓속말 (프로토콜: WHISPER <target> <msg>)
            out.println("WHISPER " + target + " " + msg);
        }
        
        inputField.setText(""); // 입력창 비우기
        inputField.requestFocus(); // 포커스 유지
    }

    /**
     * [백그라운드 스레드]
     * GUI가 멈추지 않도록 별도 스레드에서 서버 메시지를 계속 읽음
     */
    private void startReaderThread() {
        Thread reader = new Thread(() -> {
            try {
                while (in.hasNextLine()) {
                    String line = in.nextLine();
                    
                    // GUI 업데이트는 반드시 Event Dispatch Thread에서 수행
                    SwingUtilities.invokeLater(() -> processServerMessage(line));
                }
            } catch (Exception e) {
                // 서버 연결 끊김
            } finally {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "서버와 연결이 끊어졌습니다.");
                    System.exit(0);
                });
            }
        });
        reader.start();
    }

    /**
     * 서버가 보낸 프로토콜을 해석해서 채팅창에 보여줌
     */
    private void processServerMessage(String line) {
        // 프로토콜에 따른 메시지 처리
        if (line.startsWith("MESSAGE ")) {
            // 일반 채팅: MESSAGE User: Hello
            messageArea.append(line.substring(8) + "\n");
            
        } else if (line.startsWith("SYSTEM ")) {
            // 시스템 알림: SYSTEM xxx joined
            messageArea.append("[알림] " + line.substring(7) + "\n");
            
        } else if (line.startsWith("PRIVATE_FROM ")) {
            // 귓속말 수신: PRIVATE_FROM sender: msg
            String content = line.substring(13);
            messageArea.append("(귓속말) " + content + "\n");
            
        } else if (line.startsWith("PRIVATE_SENT ")) {
            // 내가 보낸 귓속말 확인: PRIVATE_SENT To target: msg
            String content = line.substring(13);
            messageArea.append("(보냄) " + content + "\n");
            
        } else if (line.startsWith("ERROR ")) {
            // 에러 메시지
            messageArea.append("[오류] " + line.substring(6) + "\n");
            
        } else {
            // 그 외 메시지 (혹시 모를 디버깅용)
            messageArea.append(line + "\n");
        }
        
        // 스크롤을 항상 맨 아래로 유지
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
    }
}
