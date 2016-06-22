import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

public class ChatServerThread extends Thread{

	Socket socket;
	BufferedReader in;
	PrintWriter out;
	Map<String, ChatServerThread> mapThreads;
	String userOfThread;

	String request;
	
	public ChatServerThread(Socket socket, Map<String, ChatServerThread> mapThreads){
		this.socket = socket;
		this.mapThreads = mapThreads;
	}
	
	public void run(){
		try {
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
			
			
			while ((request = in.readLine()) != null){
				//json {"data":"og","from":"og","to":"s","type":"login"}
				System.out.println("Dobio server: "+request);
				JSONObject jsonObject = new JSONObject(request);
				String to	 = jsonObject.getString("to");
				String from	 = jsonObject.getString("from");
				String type	 = jsonObject.getString("type");
				String data	 = jsonObject.getString("data");

				if(type.equals("login")){
					//edit, authentication needed
					if(mapThreads.get(from) != null){
						out.println("login failed");
						continue;
					}
					mapThreads.put(from, this);
					userOfThread = from;
					showAllClients();
					String clients = getAllClients();
					System.out.println("Svi kljenti na serveru: " + clients);
					out.println(clients);
					notifyAllThreadsAboutUserChange();
				} else if (type.equals("chat")){
					if(mapThreads.get(to) != null)
						mapThreads.get(to).sendMessage(to, from, type, data);
					else
						mapThreads.get(from).sendMessage(from, to, "server", "Korisnik \""+to+"\" se odjavio!");
				}else{
					System.out.println("ServerThread nepoznat type poruke");
				}
				
			}//end while		
			
			
		} catch (IOException | JSONException e) {	
			System.out.println("IOException: java.net.SocketException: Connection reset");
		} finally {
			try {
				mapThreads.remove(userOfThread);
				notifyAllThreadsAboutUserChange();
				System.out.println(getAllClients());
				in.close();
				out.close();
				socket.close();
			} catch (IOException e) {
				System.out.println("Finally catch");
				e.printStackTrace();
			}
			
		}
	}

	
	public void showAllClients(){
		System.out.println("All clients:");
		for(Map.Entry<String, ChatServerThread> entry : mapThreads.entrySet()){
			System.out.println(entry.getKey());
		}
	}
	
	public String getAllClients(){
		String clients = "";
		for(Map.Entry<String, ChatServerThread> entry : mapThreads.entrySet()){
			clients += entry.getKey()+";";
		}
		return clients;
	}
	
	public void sendMessage(String to, String from, String type, String data){
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("to", to);
			jsonObj.put("from", from);
			jsonObj.put("type", type);
			jsonObj.put("data", data);
			//System.out.println("print raw json: " + jsonObj);
			out.println(jsonObj);

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void notifyAllThreadsAboutUserChange(){
		String clients = getAllClients();
		for(Map.Entry<String, ChatServerThread> entry : mapThreads.entrySet()){
			//json {"data":"og;ir;dr;","from":"s","to":"og","type":"updateUsers"}
			
			String to = entry.getKey();
			String from = "s";
			String type = "updateUsers";
			String data = clients;
			
			entry.getValue().sendMessage(to, from, type, data);
		}
	}

}
