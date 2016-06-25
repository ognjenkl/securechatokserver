import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.CopyOnWriteArrayList;

import secureLib.CryptoImpl;


public class ChatServer {
	
	int serverPort = 1234;
	ServerSocket sSocket;
	Socket socket;
	boolean listening = true;
	//List<ChatServerThread> listThreads = new CopyOnWriteArrayList<ChatServerThread>();
	Map<String, ChatServerThread> mapThreads = new ConcurrentHashMap<String, ChatServerThread>();
	
	public void start(){
		try {
			System.out.println("Server started at port "+serverPort);
			sSocket = new ServerSocket(serverPort);
			while(listening){
				
				socket = sSocket.accept();
				
				ChatServerThread cst = new ChatServerThread(socket, mapThreads);
				cst.start();
				//listThreads.add(cst);
				//showAllClients();
			}
			
			sSocket.close();
		
		} catch (IOException e) {
			try {
				socket.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		
		ChatServer cs = new ChatServer();
		cs.start();

	}

}
