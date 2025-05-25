# Second Assignment

# Secure Chat Application with AI Integration

This is a secure, multi-threaded chat application featuring both human-to-human and AI-assisted conversations. 
Built with Java Virtual Threads, it provides robust authentication, persistent conversations, and customizable 
AI chat rooms. All communications are secured using SSL/TLS encryption.

## Instructions

This project was made using Java SE version 23 (23.0.2).

### Compilation

Before trying to run the project follow this commands in the terminal:

``` 
cd assign2/src
javac *.java
```
After running these commands you will be able to run the project.

### Server

``` 
$ java Server <port>
```

- Where port must be a valid port number, for example: 10

### Client

```
$ java Client <port> <address>
```

- Where port must match the port used to run the Server
- And address must be a valid address, for example: localhost, 0


## Authentication

After succesfully connecting to the Server, the client will be shown three options:

- Authenticate
- Reconnect
- Exit

In the first case, the user can login if he already has created an account or can register if he has not yet created an account.

In the second case, the user is asked for his token that is generated after a successfull authentication, this token is stored in a tokens.txt file and associated to the user with this format `TOKEN:USERNAME`.

All messages between the Client and the Server envolve this format `TOKEN:MESSAGE` except for the first messages since the Client has yet not authenticated.

## Concurrency

To prevent race conditions, minimize thread overheads and avoid slow clients we use locks and thread synchronization:

- ReentrantLocks: Each shared data structure is protected by a dedicated lock (e.g., authUsersLock, chatRoomsLock, userRoomLock, etc.)
- Virtual Threads: The application uses Java's virtual threads for concurrent connections without blocking OS resources
- Condition Variables: Used for timeout management and thread coordination
- Thread-safe operations: Critical sections are protected with proper lock acquisition/release

## Persistence

The chat application maintains state across server restarts:

- Database Storage: All persistent data is stored in plain text files:
    - database.txt: Stores users, rooms, room memberships, and room message history
    - chats.txt: Stores chat room configurations including AI prompts
    - tokens.txt: Stores client-side authentication tokens
    - auth.txt: Stores every user credentials

- Automatic Recovery: When the server restarts, it loads:
    - Active users and their authentication tokens
    - Chat room memberships and configurations
    - Message history for all rooms

- Session Management: Users can reconnect to their previous sessions after disconnection

## Chat Rooms

The application supports two types of chat rooms:

- Regular Chat Rooms: Allow users to communicate with each other
- AI Chat Rooms: Include an AI assistant that responds to user messages
    - Custom prompts can be configured per room
    - Conversations with AI are persisted like regular messages

## Security

- SSL/TLS Encryption: All communication is encrypted using SSL
- Token-based Authentication: Each client uses a unique token for session management
- Password Protection: User credentials are securely stored
- Session Expiration: Inactive sessions expire after a configurable timeout
- Reconnection Protocol: Secure mechanism for session resumption

## Error Handling

The system includes robust error handling:

- Client Timeouts: Automatic disconnection of inactive clients
- Server Recovery: Ability to restart without losing state
- Graceful Disconnection: Proper cleanup of resources when clients disconnect
- AI Service Failures: Fallback mechanisms when AI service is unavailable

## Implementation Details

- Protocol: Simple text-based protocol with message format TOKEN:MESSAGE
- Multithreading: Each client connection runs in its own virtual thread
- Scalability: The server can handle multiple concurrent connections efficiently
- State Management: Clear separation between volatile and persistent state