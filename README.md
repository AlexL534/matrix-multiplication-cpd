# Chat Service

This project was developed for the **CPD (Parallel and Distributed Computing)** course in the **LEIC** program. It implements a **distributed chat service**, showcasing key concepts from distributed systems, such as:

- **Multithreading** to manage multiple client connections concurrently  
- **Locks** to prevent race conditions  
- **Timers** to clean up resources from unresponsive clients  
- **Reconnection mechanism** to handle broken TCP connections gracefully  
- **Secure communication channels** to protect sensitive data  

An additional **AI chat room** feature is available, which requires an AI agent running locally in a Docker container.

**Final Grade**: 18.3/20

---

## How to Run

Make sure you have **Java** installed.

### Start the Server

```bash
java Server <port>
```
### Start a Client

```bash
java Client <port> <address>
```
