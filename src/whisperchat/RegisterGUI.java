package whisperchat;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/*
 * [회원가입 창 GUI]
 * 로그인 창에서 Sign Up 버튼을 눌렀을 때 띄우는 창
 * ID/PW/Name/Email을 입력받아서 서버에 회원가입 요청을 보냄
 * 서버와 통신하여 ID 중복 체크, 회원가입 처리
 */
public class RegisterGUI extends JDialog {

	private JTextField idField, nameField, emailField;
	private JPasswordField pwField;
	private JButton checkIdBtn, registerBtn, cancelBtn;

	private String serverHost;
	private int serverPort;
	private boolean isIdChecked = false; // ID 중복 체크를 통과했는지 여부

	public RegisterGUI(Frame parent, String host, int port) {
		// 로그인 창 위에 띄워지는 회원가입 창을 만듦
		// true -> 회원가입 창을 닫기 전까지 로그인 창은 비활성화
		super(parent, "Sign Up", true);
		this.serverHost = host;
		this.serverPort = port;

		initComponents();

		setSize(380, 220);
		setLocationRelativeTo(parent);
		setResizable(false);
		setVisible(true);
	}

	// 회원가입 화면 구성
	private void initComponents() {
		setLayout(new BorderLayout(10, 10));

		JPanel formPanel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);
		c.fill = GridBagConstraints.HORIZONTAL;

		// ID 행
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 1;
		c.weightx = 0.2;
		formPanel.add(new JLabel("ID:"), c);
		c.gridx = 1;
		c.weightx = 0.5;
		idField = new JTextField();
		formPanel.add(idField, c);
		c.gridx = 2;
		c.weightx = 0.3;
		checkIdBtn = new JButton("Check");
		checkIdBtn.setMargin(new Insets(0, 0, 0, 0));
		checkIdBtn.addActionListener(e -> doCheckId());
		formPanel.add(checkIdBtn, c);

		// Password 행
		c.gridx = 0;
		c.gridy = 1;
		formPanel.add(new JLabel("PW:"), c);
		c.gridx = 1;
		c.gridwidth = 2;
		pwField = new JPasswordField();
		formPanel.add(pwField, c);

		// Name 행
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 1;
		formPanel.add(new JLabel("Name:"), c);
		c.gridx = 1;
		c.gridwidth = 2;
		nameField = new JTextField();
		formPanel.add(nameField, c);

		// Email 행
		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 1;
		formPanel.add(new JLabel("Email:"), c);
		c.gridx = 1;
		c.gridwidth = 2;
		emailField = new JTextField();
		formPanel.add(emailField, c);

		add(formPanel, BorderLayout.CENTER);

		// 아래쪽 버튼 영역
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		registerBtn = new JButton("Register");
		cancelBtn = new JButton("Cancel");

		registerBtn.addActionListener(e -> doRegister());
		cancelBtn.addActionListener(e -> dispose());

		buttonPanel.add(registerBtn);
		buttonPanel.add(cancelBtn);

		add(buttonPanel, BorderLayout.SOUTH);
	}

	// [ID 중복 확인] 응답이 ID_OK이면 사용 가능, 그 외에는 이미 사용 중인 ID
	private void doCheckId() {
		String id = idField.getText().trim();
		if (id.isEmpty())
			return;

		// users.dat에서 "::"를 구분자로 쓰기 때문에, ID에 "::"가 들어가면 데이터 파일이 꼬일 수 있음
		// -> 파일 구분자 사용 금지
		if (id.contains("::")) {
			JOptionPane.showMessageDialog(this, "ID에 '::'는 포함될 수 없습니다.");
			return;
		}

		try (Socket socket = new Socket(serverHost, serverPort);
				Scanner in = new Scanner(socket.getInputStream());
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

			out.println("<MYP2> CHECK_ID " + id);

			if (in.hasNextLine()) {
				String response = in.nextLine();
				// 서버에서 오는 응답에도 항상 <MYP2> 헤더가 붙어 있으므로 먼저 제거
				if (response.startsWith("<MYP2> "))
					response = response.substring(7);

				if ("ID_OK".equals(response)) {
					JOptionPane.showMessageDialog(this, "사용 가능한 ID입니다.");
					isIdChecked = true;
					// 중복 체크 통과 후에는 ID를 바꾸지 못하게 잠그고, Check 버튼도 다시 못 누르게 막음
					idField.setEditable(false);
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

	// [회원가입 요청]
	private void doRegister() {
		// ID 중복 체크를 안 했으면 가입 불가
		if (!isIdChecked) {
			JOptionPane.showMessageDialog(this, "ID 중복 확인을 해주세요.");
			return;
		}

		String id = idField.getText().trim();
		String pw = new String(pwField.getPassword()).trim();
		String name = nameField.getText().trim();
		String email = emailField.getText().trim();

		if (pw.isEmpty() || name.isEmpty() || email.isEmpty()) {
			JOptionPane.showMessageDialog(this, "모든 내용을 입력해주세요.");
			return;
		}

		// 파일 구분자 사용 금지
		if (pw.contains("::") || name.contains("::") || email.contains("::")) {
			JOptionPane.showMessageDialog(this, "입력 정보에 '::' 문자는 사용할 수 없습니다.");
			return;
		}

		// 회원 가입 -> 임시로 소켓 열었다 닫는 구조
		try (Socket socket = new Socket(serverHost, serverPort);
				Scanner in = new Scanner(socket.getInputStream());
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

			out.println("<MYP2> REGISTER " + id + " " + pw + " " + name + " " + email);

			if (in.hasNextLine()) {
				String res = in.nextLine();
				if (res.startsWith("<MYP2> "))
					res = res.substring(7);

				if ("REGISTER_SUCCESS".equals(res)) {
					JOptionPane.showMessageDialog(this, "가입 완료: 로그인 해주세요.");
					dispose();
				} else {
					JOptionPane.showMessageDialog(this, "가입 실패: " + res);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}