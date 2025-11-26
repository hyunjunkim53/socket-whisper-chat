package whisperchat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/*
 * 채팅 클라이언트 메인 창
 * 서버와 소켓으로 연결된 후 채팅/귓속말 메시지를 송수신하는 GUI 클래스
 * 송신 시: 모든 메시지에 <MYP2> 헤더를 붙여 서버로 전송
 * 수신 시: <MYP2> 헤더를 제거한 뒤, 화면에는 내용만 출력
 */
public class WhisperChatClient extends JFrame {

	private Socket socket;
	private Scanner in;
	private PrintWriter out;
	private String myId;

	// GUI 컴포넌트
	private JTextArea messageArea;
	private JTextField targetField;
	private JTextField inputField;
	private JButton sendButton;
	private JToggleButton whisperButton;
	private JButton logoutButton;

	public WhisperChatClient(Socket socket, Scanner in, PrintWriter out, String myId) {
		super("WhisperChat");
		this.socket = socket;
		this.in = in;
		this.out = out;
		this.myId = myId;

		// 채팅창 구성
		buildGUI();

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(500, 450);
		setLocationRelativeTo(null);
		setVisible(true);

		// 서버로부터 오는 메시지를 별도 스레드에서 수신
		startReaderThread();
	}

	// [GUI 구성]
	// 상단: 접속 중인 사용자 정보 + Logout 버튼
	// 중앙: 채팅 메시지 출력 영역
	// 하단: 일반 채팅 입력 + Whisper 모드 전환/대상 ID 입력
	private void buildGUI() {
		setLayout(new BorderLayout());

		// 상단 패널 (User 정보, Logout 버튼)
		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		topPanel.setBackground(new Color(240, 240, 240));

		JLabel infoLabel = new JLabel("User: " + myId);
		infoLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
		topPanel.add(infoLabel, BorderLayout.WEST);

		logoutButton = new JButton("Logout");
		logoutButton.setMargin(new Insets(2, 10, 2, 10));
		topPanel.add(logoutButton, BorderLayout.EAST);

		add(topPanel, BorderLayout.NORTH);

		// 중앙: 채팅 내용 영역
		messageArea = new JTextArea();
		messageArea.setEditable(false);
		messageArea.setLineWrap(true);
		messageArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
		add(new JScrollPane(messageArea), BorderLayout.CENTER);

		// 하단 패널 (Whisper 설정 + 메시지 입력)
		JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		JPanel targetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

		whisperButton = new JToggleButton("Whisper");
		whisperButton.setBackground(Color.LIGHT_GRAY);
		whisperButton.setMargin(new Insets(2, 10, 2, 10));
		targetPanel.add(whisperButton);

		targetPanel.add(new JLabel("To:"));

		targetField = new JTextField(8);
		targetField.setEnabled(false); // Whisper 모드가 아닐 때는 비활성화
		targetField.setBackground(Color.LIGHT_GRAY);
		targetPanel.add(targetField);

		bottomPanel.add(targetPanel, BorderLayout.WEST);

		inputField = new JTextField();
		bottomPanel.add(inputField, BorderLayout.CENTER);

		sendButton = new JButton("Send");
		bottomPanel.add(sendButton, BorderLayout.EAST);

		add(bottomPanel, BorderLayout.SOUTH);

		// [Logout 버튼] 서버에 /quit 전송 후 프로그램 종료
		logoutButton.addActionListener(e -> {
			out.println("<MYP2> /quit"); // 종료 프로토콜 전송
			try {
				socket.close();
			} catch (Exception ex) {
			}
			System.exit(0);
		});

		// Whisper 모드 토글: 귓속말 모드 ON/OFF
		whisperButton.addActionListener(e -> {
			if (whisperButton.isSelected()) {
				whisperButton.setBackground(new Color(255, 215, 0));
				whisperButton.setText("Whisper ON");
				targetField.setEnabled(true);
				targetField.setBackground(Color.WHITE);
				targetField.requestFocus();
			} else {
				whisperButton.setBackground(Color.LIGHT_GRAY);
				whisperButton.setText("Whisper");
				targetField.setEnabled(false);
				targetField.setBackground(Color.LIGHT_GRAY);
				targetField.setText("");
			}
		});

		ActionListener sendAction = e -> sendMessage();
		inputField.addActionListener(sendAction);
		sendButton.addActionListener(sendAction);
	}

	/*
	 * [메시지 전송] 일반 메시지: <MYP2> + msg 귓속말: <MYP2> WHISPER 대상ID 메시지
	 */
	private void sendMessage() {
		String msg = inputField.getText().trim();
		if (msg.isEmpty())
			return;

		if (whisperButton.isSelected()) {
			String target = targetField.getText().trim();
			if (target.isEmpty()) {
				JOptionPane.showMessageDialog(this, "Enter Recipient ID");
				return;
			}
			// 귓속말 프로토콜: <MYP2> WHISPER 대상ID 메시지
			out.println("<MYP2> WHISPER " + target + " " + msg);
		} else {
			// 일반 메시지: <MYP2> + 실제 텍스트만 전송
			out.println("<MYP2> " + msg);
		}

		inputField.setText("");
		inputField.requestFocus();
	}

	// [수신 스레드 시작] 서버로부터 한 줄씩 메시지를 읽음
	private void startReaderThread() {
		Thread reader = new Thread(() -> {
			try {
				while (in.hasNextLine()) {
					String line = in.nextLine();

					// 수신 직후 프로토콜 헤더 제거 (UI에는 프로토콜 문자열이 보이지 않게 처리)
					if (line.startsWith("<MYP2> ")) {
						line = line.substring(7);
					}

					// final 변수로 복사 (람다식 사용 위해)
					String finalLine = line;
					SwingUtilities.invokeLater(() -> processServerMessage(finalLine));
				}
			} catch (Exception e) {
			} finally {
				SwingUtilities.invokeLater(() -> {
					JOptionPane.showMessageDialog(this, "Disconnected from Server");
					System.exit(0);
				});
			}
		});
		reader.start();
	}

	/*
	 * [서버 메시지 처리] MESSAGE / SYSTEM / PRIVATE_FROM / PRIVATE_SENT / ERROR 타입에 따라
	 * 채팅창에 다른 형식으로 출력
	 */
	private void processServerMessage(String line) {
		// 일반 채팅: MESSAGE 이후 내용을 [전체] 태그로 출력
		if (line.startsWith("MESSAGE ")) {
			messageArea.append("[전체] " + line.substring(8) + "\n");

			// 시스템 알림 메시지
		} else if (line.startsWith("SYSTEM ")) {
			messageArea.append("[알림] " + line.substring(7) + "\n");

			// 서버에서 보낸 귓속말 수신: PRIVATE_FROM 보낸사람:메시지
		} else if (line.startsWith("PRIVATE_FROM ")) {
			String content = line.substring(13);
			String[] parts = content.split(":", 2);
			if (parts.length >= 2) {
				messageArea.append("[귓속말] From " + parts[0] + ": " + parts[1] + "\n");
			} else {
				messageArea.append("[귓속말] " + content + "\n");
			}

			// 내가 보낸 귓속말에 대한 확인 메시지: PRIVATE_SENT 대상ID:메시지
		} else if (line.startsWith("PRIVATE_SENT ")) {
			String content = line.substring(13);
			String[] parts = content.split(":", 2);
			if (parts.length >= 2) {
				messageArea.append("[귓속말] To " + parts[0] + ": " + parts[1] + "\n");
			} else {
				messageArea.append("[귓속말] " + content + "\n");
			}
			// 서버에서 내려준 에러 메시지
		} else if (line.startsWith("ERROR ")) {
			messageArea.append("[오류] " + line.substring(6) + "\n");
			// 그 외 형식은 그대로 출력
		} else {
			messageArea.append(line + "\n");
		}

		messageArea.setCaretPosition(messageArea.getDocument().getLength());
	}
}