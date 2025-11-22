package whisperchat;

import javax.swing.JFrame;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Properties;
import java.util.Scanner;

/**
 * [클라이언트 시작점]
 * 과제 요구사항:
 * 1. GUI 구현 (로그인 화면)
 * 2. 설정 파일(serverinfo.dat) 읽기
 * 3. 로그인 성공 시 채팅창으로 소켓 연결 전달
 */
public class LoginGUI extends JFrame {

    private JTextField idField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;

    // 서버 연결 정보 (파일에서 읽어올 변수)
    // 파일이 없으면 기본값(localhost:59001)을 사용
    private String serverAddress = "127.0.0.1";
    private int serverPort = 59001;

    public LoginGUI() {
        super("WhisperChat Login"); // 창 제목

        // 1. 설정 파일 로드 (과제 요구사항)
        loadServerInfo();

        // 2. 화면 구성
        buildGUI();

        // 3. 버튼 이벤트 연결
        setEvents();

        // 창 기본 설정
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(320, 160);
        setLocationRelativeTo(null); // 화면 가운데 배치
        setResizable(false); // 창 크기 조절 불가
        setVisible(true);
    }

    private void loadServerInfo() {
        // HW1 과제에서 했던 것과 동일하게 Properties 사용
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("serverinfo.dat")) {
            props.load(fis);
            serverAddress = props.getProperty("host", "127.0.0.1");
            String portStr = props.getProperty("port", "59001");
            serverPort = Integer.parseInt(portStr);
            System.out.println("[LoginGUI] 서버 설정 로드 완료: " + serverAddress + ":" + serverPort);
        } catch (Exception e) {
            System.out.println("[LoginGUI] 설정 파일 없음. 기본값 사용 (" + serverAddress + ":" + serverPort + ")");
        }
    }

    private void buildGUI() {
        setLayout(new BorderLayout(10, 10));

        // 중앙: 입력 필드 패널
        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15)); // 여백

        inputPanel.add(new JLabel("ID:"));
        idField = new JTextField();
        inputPanel.add(idField);

        inputPanel.add(new JLabel("Password:"));
        passwordField = new JPasswordField(); // 비밀번호용 필드
        inputPanel.add(passwordField);

        add(inputPanel, BorderLayout.CENTER);

        // 하단: 버튼 패널
        JPanel buttonPanel = new JPanel(new FlowLayout());
        loginButton = new JButton("Login");
        registerButton = new JButton("Sign Up"); // 회원가입 버튼

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setEvents() {
        // 로그인 버튼 클릭
        loginButton.addActionListener(e -> tryLogin());

        // 회원가입 버튼 클릭 -> RegisterGUI 열기
        registerButton.addActionListener(e -> {
            // 현재 창(this)을 부모로 하여 회원가입 창을 띄움
            new RegisterGUI(this, serverAddress, serverPort);
        });

        // 입력창에서 엔터키 눌러도 로그인 되도록 편의 기능 추가
        ActionListener enterAction = e -> tryLogin();
        idField.addActionListener(enterAction);
        passwordField.addActionListener(enterAction);
    }

    /**
     * [핵심 로직] 로그인 시도
     */
    private void tryLogin() {
        String id = idField.getText().trim();
        String pw = new String(passwordField.getPassword()).trim();

        if (id.isEmpty() || pw.isEmpty()) {
            JOptionPane.showMessageDialog(this, "ID와 비밀번호를 입력하세요.");
            return;
        }

        try {
            // 1. 서버와 연결 (소켓 생성)
            Socket socket = new Socket(serverAddress, serverPort);
            Scanner in = new Scanner(socket.getInputStream());
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // 2. 로그인 프로토콜 전송: LOGIN <id> <pw>
            out.println("LOGIN " + id + " " + pw);

            // 3. 서버 응답 대기
            if (in.hasNextLine()) {
                String response = in.nextLine();

                if (response.startsWith("LOGIN_SUCCESS")) {
                    // 로그인 성공!
                    // 현재 로그인 창은 닫음
                    dispose();
                    
                    // 채팅창(WhisperChatClient)을 열면서 소켓을 넘겨줌 (연결 유지)
                    new WhisperChatClient(socket, in, out, id);
                    
                } else {
                    // 로그인 실패 (예: LOGIN_FAIL Password_Mismatch)
                    String msg = response.replace("LOGIN_FAIL ", "");
                    JOptionPane.showMessageDialog(this, "로그인 실패: " + msg);
                    socket.close(); // 실패했으니 연결 끊기
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "서버 연결 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Swing 스레드 안전성 보장
        SwingUtilities.invokeLater(() -> new LoginGUI());
    }
}