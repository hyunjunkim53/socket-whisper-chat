# Socket Whisper Chat

This is a small Java socket chat program for my Computer Networks homework.  
The goal was to design a simple text protocol (MYP2), handle login/sign up on the server, and support normal chat and whisper messages between multiple clients.

## Features

- Login and sign up with ID / password / name / email  
- Password hashing with salt (stored in `users.dat`)
- ID duplicate check before registration
- Text protocol with header `<MYP2>` and commands like `LOGIN`, `REGISTER`, `WHISPER`, `/quit`
- Normal chat (broadcast) and private whisper messages
- Swing GUI for Login, Sign Up, and main chat window

## How to Run

1. Import the project `SocketHW2` into Eclipse as an **Existing Eclipse Project**.
2. Run `WhisperChatServer` as a Java Application to start the server.
3. Run `LoginGUI` for each client.
4. Use **Sign Up** to create accounts, then log in and test:
   - normal chat between multiple windows
   - whisper messages using the **Whisper** toggle and target ID

`serverinfo.dat` contains the server IP and port (default: `127.0.0.1:59001`).  
`users.dat` may be empty at first; new users are added when they register.
