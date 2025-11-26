package whisperchat;

import javax.swing.JFrame;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Properties;
import java.util.Scanner;

/*
 * [로그인 창 GUI]
 * 설정 파일(serverinfo.dat)에서 서버 host/port를 읽어옴
 * ID/PW를 입력받아 서버에 로그인 요청
 * 그인 성공 시 같은 소켓으로 메인 채팅창(WhisperChatClient)로 전환
 */
public class LoginGUI extends JFrame {

	private JTextField idField;
	private JPasswordField passwordField;
	private JButton loginButton;
	private JButton registerButton;

	// serverinfo.dat 에서 읽어올 서버 주소/포트 (기본값은 localhost:59001)
	// 파일이 없으면 아래 기본값 사용
	private String serverAddress = "127.0.0.1";
	private int serverPort = 59001;

	public LoginGUI() {
		super("WhisperChat Login");

		// 설정 파일 로드
		loadServerInfo();

		// 로그인 화면 구성
		buildGUI();

		// 버튼/엔터키 이벤트 연결
		setEvents();

		// 창 기본 설정
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(320, 160);
		setLocationRelativeTo(null);
		setResizable(false);
		setVisible(true);
	}

	// [ConfigFile] serverinfo.dat에서 host/port 읽어오기
	private void loadServerInfo() {
		Properties props = new Properties();
		try (FileInputStream fis = new FileInputStream("serverinfo.dat")) {
			props.load(fis);
			serverAddress = props.getProperty("host", "127.0.0.1");
			String portStr = props.getProperty("port", "59001");
			serverPort = Integer.parseInt(portStr.trim());
			System.out.println("[LoginGUI] 서버 설정 로드: " + serverAddress + ":" + serverPort);
		} catch (Exception e) {
			// serverinfo.dat이 없거나 읽기 실패 시 기본값 사용
			System.out.println("[LoginGUI] serverinfo.dat 없음 -> 기본값 사용 (" + serverAddress + ":" + serverPort + ")");
		}
	}

	// [Login GUI] 로그인 폼과 버튼 배치
	// 중앙: ID / Password 입력 필드
	// 하단: Sign Up / Login 버튼
	private void buildGUI() {
		setLayout(new BorderLayout(10, 10));

		// 중앙: 입력 필드 패널
		JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
		inputPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15));

		inputPanel.add(new JLabel("ID:"));
		idField = new JTextField();
		inputPanel.add(idField);

		inputPanel.add(new JLabel("Password:"));
		passwordField = new JPasswordField();
		inputPanel.add(passwordField);

		add(inputPanel, BorderLayout.CENTER);

		// 하단: 버튼 패널 (Sign Up/Login)
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		registerButton = new JButton("Sign Up");
		loginButton = new JButton("Login");

		buttonPanel.add(registerButton);
		buttonPanel.add(loginButton);

		add(buttonPanel, BorderLayout.SOUTH);
	}

	// [이벤트 설정]
	// Login 버튼 클릭/Enter 키 입력 시 tryLogin() 호출
	// Sign Up 버튼 클릭 시 RegisterGUI 열기
	private void setEvents() {
		// 로그인 버튼 클릭
		loginButton.addActionListener(e -> tryLogin());

		// 회원가입 버튼 클릭 -> RegisterGUI 열기
		registerButton.addActionListener(e -> {
			new RegisterGUI(this, serverAddress, serverPort);
		});

		// 입력창에서 엔터키 눌러도 로그인 시도
		ActionListener enterAction = e -> tryLogin();
		idField.addActionListener(enterAction);
		passwordField.addActionListener(enterAction);
	}

	/*
	 * 로그인 시도: 서버와 소켓 연결 <MYP2> LOGIN <id> <pw> 프로토콜 전송 응답에 따라 WhisperChatClient로
	 * 넘어가거나 에러 출력
	 */
	private void tryLogin() {
		String id = idField.getText().trim();
		String pw = new String(passwordField.getPassword()).trim();

		if (id.isEmpty() || pw.isEmpty()) {
			JOptionPane.showMessageDialog(this, "ID와 비밀번호를 입력하세요.");
			return;
		}

		try {
			// 서버와 연결 (소켓 생성)
			Socket socket = new Socket(serverAddress, serverPort);
			Scanner in = new Scanner(socket.getInputStream());
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

			// 로그인 프로토콜 전송: <MYP2> LOGIN <id> <pw>
			out.println("<MYP2> LOGIN " + id + " " + pw);

			// 서버 응답 대기
			if (in.hasNextLine()) {
				String response = in.nextLine();
				// 프로토콜 헤더(<MYP2>)가 붙어 있으면 제거
				if (response.startsWith("<MYP2> ")) {
					response = response.substring(7);
				}

				if (response.startsWith("LOGIN_SUCCESS")) {
					// 로그인 성공 -> 채팅창으로 전환
					dispose(); // 로그인 창 닫기

					// 같은 소켓/스트림을 채팅창에 넘겨서 연결 유지
					new WhisperChatClient(socket, in, out, id);

				} else {
					// 로그인 실패
					String msg = response.replace("LOGIN_FAIL ", "");
					JOptionPane.showMessageDialog(this, "로그인 실패: " + msg);
					socket.close(); // 실패 시 연결 종료
				}
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "서버 연결 오류: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(LoginGUI::new);
	}
}
