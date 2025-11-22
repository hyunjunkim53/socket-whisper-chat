package whisperchat;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * [회원가입 화면]
 * 과제 요구사항:
 * 1. 필수 필드 (ID, PW, Name, Email) 입력
 * 2. ID 중복 체크 (CHECK_ID)
 * 3. 회원가입 요청 (REGISTER)
 */
public class RegisterGUI extends JDialog {

    private JTextField idField, nameField, emailField;
    private JPasswordField pwField;
    private JButton checkIdBtn, registerBtn, cancelBtn;

    private String serverHost;
    private int serverPort;
    private boolean isIdChecked = false; // 중복 확인 했는지 체크하는 플래그

    public RegisterGUI(Frame parent, String host, int port) {
        super(parent, "Sign Up", true); // true = Modal (부모창 제어 불가)
        this.serverHost = host;
        this.serverPort = port;

        buildGUI();
        
        setSize(300, 250);
        setLocationRelativeTo(parent);
        setResizable(false);
        setVisible(true);
    }

    private void buildGUI() {
        setLayout(new BorderLayout(10, 10));

        // 입력 폼 패널 (GridBagLayout으로 정교하게 배치)
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 4, 4, 4);

        // ID 행 (입력창 + 중복확인 버튼)
        c.gridx = 0; c.gridy = 0; c.weightx = 0.2;
        formPanel.add(new JLabel("ID:"), c);
        
        c.gridx = 1; c.weightx = 0.5;
        idField = new JTextField();
        formPanel.add(idField, c);
        
        c.gridx = 2; c.weightx = 0.3;
        checkIdBtn = new JButton("Check");
        checkIdBtn.setMargin(new Insets(0,0,0,0)); // 버튼 작게
        checkIdBtn.addActionListener(e -> doCheckId());
        formPanel.add(checkIdBtn, c);

        // Password 행
        c.gridx = 0; c.gridy = 1;
        formPanel.add(new JLabel("PW:"), c);
        c.gridx = 1; c.gridwidth = 2; // 2칸 차지
        pwField = new JPasswordField();
        formPanel.add(pwField, c);

        // Name 행
        c.gridx = 0; c.gridy = 2; c.gridwidth = 1;
        formPanel.add(new JLabel("Name:"), c);
        c.gridx = 1; c.gridwidth = 2;
        nameField = new JTextField();
        formPanel.add(nameField, c);

        // Email 행
        c.gridx = 0; c.gridy = 3; c.gridwidth = 1;
        formPanel.add(new JLabel("Email:"), c);
        c.gridx = 1; c.gridwidth = 2;
        emailField = new JTextField();
        formPanel.add(emailField, c);

        add(formPanel, BorderLayout.CENTER);

        // 버튼 패널
        JPanel btnPanel = new JPanel();
        registerBtn = new JButton("Register");
        cancelBtn = new JButton("Cancel");
        
        registerBtn.addActionListener(e -> doRegister());
        cancelBtn.addActionListener(e -> dispose()); // 닫기

        btnPanel.add(registerBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }

    // [기능 1] ID 중복 확인
    private void doCheckId() {
        String id = idField.getText().trim();
        if(id.isEmpty()) return;

        // 잠깐 접속해서 물어보고 끊음
        try (Socket socket = new Socket(serverHost, serverPort);
             Scanner in = new Scanner(socket.getInputStream());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("CHECK_ID " + id);
            
            if(in.hasNextLine()) {
                String response = in.nextLine();
                if("ID_OK".equals(response)) {
                    JOptionPane.showMessageDialog(this, "사용 가능한 ID입니다.");
                    isIdChecked = true;
                    idField.setEditable(false); // ID 수정 못하게 막음
                    checkIdBtn.setEnabled(false);
                } else {
                    JOptionPane.showMessageDialog(this, "이미 사용 중인 ID입니다.");
                    isIdChecked = false;
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "연결 오류: " + e.getMessage());
        }
    }

    // [기능 2] 회원가입 요청
    private void doRegister() {
        if(!isIdChecked) {
            JOptionPane.showMessageDialog(this, "ID 중복 확인을 해주세요.");
            return;
        }
        
        String id = idField.getText().trim();
        String pw = new String(pwField.getPassword()).trim();
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();

        if(pw.isEmpty() || name.isEmpty() || email.isEmpty()) {
            JOptionPane.showMessageDialog(this, "모든 내용을 입력해주세요.");
            return;
        }

        try (Socket socket = new Socket(serverHost, serverPort);
             Scanner in = new Scanner(socket.getInputStream());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // 프로토콜: REGISTER id pw name email
            out.println("REGISTER " + id + " " + pw + " " + name + " " + email);

            if(in.hasNextLine()) {
                String res = in.nextLine();
                if("REGISTER_SUCCESS".equals(res)) {
                    JOptionPane.showMessageDialog(this, "가입 완료: 로그인 해주세요.");
                    dispose(); // 가입 창 닫기
                } else {
                    JOptionPane.showMessageDialog(this, "가입 실패: " + res);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}