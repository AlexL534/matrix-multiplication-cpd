# Second Assignment

# Secure Chat Application with AI Integration

This is a secure, multi-threaded chat application featuring both human-to-human and AI-assisted conversations. 
Built with Java Virtual Threads, it provides robust authentication, persistent conversations, and customizable 
AI chat rooms. All communications are secured using SSL/TLS encryption.

## Installation Guide
To start the Ollama VM, run the following command:

```bash
sudo docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama14 ollama/ollama
```

## Instructions

This project was made using Java SE version 23 (23.0.2).

### Compilation

Before running the project, execute the following commands in the terminal:

``` 
cd assign2/src
javac *.java
```
After running these commands you will be able to run the project.

### Server

``` 
$ java Server <port>
```

- Replace `<port>` with a valid port number, for example: `10`

### Client

```
$ java Client <port> <address>
```

- `<port>` must match the port used to run the Server
- `<address>` must be a valid address, for example: localhost, 0


## Authentication

After succesfully connecting to the Server, the client is presented with three options:


*   **Authenticate**
Log in with an existing account or, if the username does not exist, a new account will be automatically created.

*   **Reconnect**
The user is prompted to enter their token, which was generated upon a previous successful authentication. Tokens are stored in a `tokens.txt` file and associated to the user with the format `TOKEN:USERNAME`.

*   **Exit**
The client disconnects from the server.

All messages exchanged between the Client and the Server follow the format `TOKEN:MESSAGE`, except for initial messages before authentication.

## Concurrency

To prevent race conditions, minimize thread overheads and avoid slow clients, the application uses:

- **ReentrantLocks:** Each shared data structure is protected by a dedicated lock (e.g., authUsersLock, chatRoomsLock, userRoomLock, etc.)
- **Virtual Threads:** The application uses Java's virtual threads for concurrent connections without blocking OS resources
- **Condition Variables:** Used for timeout management and thread coordination
- **Thread-safe operations:** Critical sections are protected with proper lock acquisition/release

## Persistence

The chat application maintains state across server restarts:

* **Database Storage:** Persistent data is stored in plain text files:

  * `database.txt`: Users, rooms, memberships, and message history
  * `chats.txt`: Chat room configurations including AI prompts
  * `tokens.txt`: Client authentication tokens
  * `auth.txt`: User credentials

* **Automatic Recovery:** On startup, the server loads:

  * Active users and their tokens
  * Chat room memberships and configurations
  * Message history for all rooms

* **Session Management:** Users can reconnect to previous sessions after disconnection.

## Chat Rooms

The application supports two types of chat rooms:

* **Regular Chat Rooms:** Users communicate with each other.
* **AI Chat Rooms:** Include an AI assistant that responds to user messages.

  * Custom AI prompts configurable per room
  * AI conversations are persisted like regular messages.

## Security

* **SSL/TLS Encryption:** All communications are encrypted using SSL
* **Token-based Authentication:** Each client uses a unique token for session management
* **Password Protection:** User credentials are securely stored
* **Session Expiration:** Inactive sessions expire after a configurable timeout
* **Reconnection Protocol:** Secure mechanism for session resumption

## Error Handling

The system includes robust error handling:

- **Client Timeouts:** Automatic disconnection of inactive clients
- **Server Recovery:** Server can restart without losing state
- **Graceful Disconnection:** Proper cleanup of resources when clients disconnect
- **AI Service Failures:** Fallback mechanisms when AI service is unavailable

## Implementation Details

- **Protocol:** Simple text-based protocol with message format `TOKEN:MESSAGE`
- **Multithreading:** Each client connection runs in its own virtual thread
- **Scalability:** The server can handle multiple concurrent connections efficiently
- **State Management:** Clear separation between volatile and persistent state