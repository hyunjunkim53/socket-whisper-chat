package whisperchat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/*
 * [회원 관리와 보안 담당 클래스]
 * 다음과 같은 역할을 수행
 * 1. 회원 정보 파일로 관리
 * 2. urserID 중복 체크
 * 3. 비밀번호 해시 + 솔트 저장
 * 4. 로그인 시 저장된 해시와 입력 비밀 번호 비교
 */
public class MemberManager {

	// [회원 관리] 사용자들 정보 파일로 관리 (server측)
	private static final String CLIENT_INFO_FILE = "users.dat"; // 회원 정보 파일명
	private static final String DELIMITER = "::"; // 데이터를 구분할 구분자 (id::hashPw::salt::name::email)

	// [회원가입 처리]
	// synchronized를 사용해서 여러 클라이언트가 동시에 회원가입해도
	// users.dat에 쓰는 작업은 한 번에 한 스레드만 수행되게 함 -> ID 중복 / 파일 깨짐 방지
	public synchronized boolean register(String id, String pw, String name, String email) {

		// [고유성 보장] 이미 같은 ID가 존재하면 가입 불가
		if (isUserExists(id)) {
			return false;
		}

		try {
			// [암호화] 비밀번호를 해시(단방향) + 임의 솔트로 변환 (복구 불가)
			byte[] salt = generateSalt();
			String saltStr = Base64.getEncoder().encodeToString(salt);
			String hashPw = hashPassword(pw, salt);

			// [회원 관리] 파일에 한 줄 저장 (이어 쓰기: 기존 데이터 뒤에 추가)
			// [회원 가입 필드] userId, password, name, email
			// 포맷: id::hashPw::salt::name::email
			// FileWriter의 두 번째 인자 true -> 기존 내용 유지하고 뒤에 이어 쓰기
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(CLIENT_INFO_FILE, true))) {
				String line = String.join(DELIMITER, id, hashPw, saltStr, name, email);
				bw.write(line); // 회원 등록
				bw.newLine();
			}

			System.out.println("[MemberManager] 신규 회원 등록: " + id);
			return true;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	// [로그인 인증 처리] 사용자가 입력한 비밀번호를 저장된 Salt와 합쳐서 다시 해시를 만들고, 저장된 해시와 똑같은지 비교
	public synchronized boolean login(String id, String inputPw) {
		// [로그인] 사용자들 정보 저장 관리 파일에서 조회
		Map<String, String> userInfo = findUser(id);

		if (userInfo == null) {
			return false; // ID 없는 경우
		}

		try {
			// 저장돼있던 해시값과 솔트값 꺼내오기
			String storedHash = userInfo.get("hash");
			String storedSaltStr = userInfo.get("salt");

			// salt 문자열 -> salt 바이트로 복원
			byte[] salt = Base64.getDecoder().decode(storedSaltStr);

			// 현재 입력받은 비밀번호도 똑같이 해시로 변환
			String inputHash = hashPassword(inputPw, salt);

			// 두 해시값 비교를 통해 로그인 성공 및 실패 결정
			return storedHash.equals(inputHash);

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	// [정보 수신] 로그인 성공 시 이름 반환
	// [로그인] Login시 자신의 정보를 서버로부터 얻어옴
	public synchronized String getUserName(String id) {
		Map<String, String> info = findUser(id);
		if (info != null) {
			return info.get("name");
		}
		return id; // 문제가 생기면 최소한 ID라도 반환
	}

	// [중복 체크] 해당 ID가 이미 가입되어 있으면 true, 아니면 false
	public synchronized boolean isUserExists(String id) {
		return findUser(id) != null;
	}

	// users.dat 파일에서 targetId와 같은 ID를 가진 사용자를 찾는 메서드
	private Map<String, String> findUser(String targetId) {
		File file = new File(CLIENT_INFO_FILE);
		if (!file.exists())
			return null;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(DELIMITER);
				// 포맷: id::hash::salt::name::email
				if (parts.length >= 5 && parts[0].equals(targetId)) {
					Map<String, String> map = new HashMap<>();
					map.put("id", parts[0]);
					map.put("hash", parts[1]);
					map.put("salt", parts[2]);
					map.put("name", parts[3]);
					return map; // 가입된 사용자인 경우: 해당 사용자 정보 반환
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null; // 가입되지 않은 사용자인 경우: null 반환
	}

	// 해시(단방향) 생성 (SHA-256 알고리즘 사용)
	private String hashPassword(String pw, byte[] salt) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(salt);
		byte[] hash = md.digest(pw.getBytes(StandardCharsets.UTF_8));
		return Base64.getEncoder().encodeToString(hash);
	}

	// 임의 솔트 생성 (보안을 위해 매번 랜덤하게 생성)
	private byte[] generateSalt() throws NoSuchAlgorithmException {
		SecureRandom sr = new SecureRandom();
		byte[] salt = new byte[16];
		sr.nextBytes(salt);
		return salt;
	}
}
