import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.CryptoPrimitive;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;

import secureLib.CryptoImpl;
import secureUtil.MessageType;

public class ChatServerThread extends Thread{

	Socket socket;
	BufferedReader in;
	PrintWriter out;
	Map<String, ChatServerThread> mapThreads;
	String userOfThread;
	
	/**
	 * Secret key of user (client) of thread.
	 */
	byte[] symmetricKey = null;
	String opModeSymmetric = "";
	String hashFunction = "";
	
	Properties prop = null;
	FileInputStream fis = null;
	String propSymmetricOpModePaddingAes = "";
	String propSymmetricOpModePadding3Des = "";
	String propAsymmetricOpModePaddingRsa = "";
	String propServerKeyPath = "";
	
	public ChatServerThread(Socket socket, Map<String, ChatServerThread> mapThreads){
		try {
			this.socket = socket;
			this.mapThreads = mapThreads;
		
			prop = new Properties();
			fis = new FileInputStream(new File("resources/config.properties"));
			prop.load(fis);
			propSymmetricOpModePaddingAes = prop.getProperty("symmetricOpModePaddingAes");
			propSymmetricOpModePadding3Des = prop.getProperty("symmetricOpModePadding3Des");
			propAsymmetricOpModePaddingRsa = prop.getProperty("asymmetricOpModePaddingRsa");
			propServerKeyPath = prop.getProperty("serverKeyPath");
			
			fis.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void run(){
		try {
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
			
			symmetricKey = null;
			opModeSymmetric = "";
			hashFunction = "";
			byte[] requestDecoded = null;
			byte[] requestDecrypted = null;
			String requestString = "";
			String opModeAsymmetric = "";
			String request = "";
			
			opModeAsymmetric = propAsymmetricOpModePaddingRsa;
			
			File filePrivateKey = new File(propServerKeyPath);
			KeyPair privateKeyPairServer = CryptoImpl.getKeyPair(filePrivateKey);
			
			while ((request = in.readLine()) != null){
				if(symmetricKey == null){
					requestDecoded = Base64.getDecoder().decode(request.getBytes(StandardCharsets.UTF_8));
					requestDecrypted = CryptoImpl.asymmetricEncryptDecrypt(opModeAsymmetric, privateKeyPairServer.getPrivate(), requestDecoded, false);
					requestString = new String(requestDecrypted, StandardCharsets.UTF_8);
					
					//{"alg":"DESede/ECB/PKCS7Padding","key":"MTF2Kf7Zq4+rbrCK5pjTYpTva26rMtXl"}
					//System.out.println("requestString: " + requestString);
					JSONObject jsonRequest = new JSONObject(requestString);
					opModeSymmetric = jsonRequest.getString(MessageType.ALGORITHM);
					symmetricKey = Base64.getDecoder().decode(jsonRequest.getString(MessageType.KEY).getBytes(StandardCharsets.UTF_8));
					hashFunction = jsonRequest.getString(MessageType.HASH);
					
					
					//server signs response
					String predefinedOKTag = MessageType.OK;
					JSONObject jsonResponseOK = new JSONObject();
				//digital signature
					byte[] digest = CryptoImpl.hash(hashFunction, predefinedOKTag.getBytes(StandardCharsets.UTF_8));
					byte[] digitalSignature = CryptoImpl.asymmetricEncryptDecrypt(opModeAsymmetric, privateKeyPairServer.getPrivate(), digest, true);
					String digitalSignatureEncodedString = new String(Base64.getEncoder().encode(digitalSignature), StandardCharsets.UTF_8);
				//end digital signature
				//cipher
					jsonResponseOK.put(MessageType.DIGSIG, digitalSignatureEncodedString);
					jsonResponseOK.put(MessageType.DATA, predefinedOKTag);
					byte[] jsonResponseOKEncoded = Base64.getEncoder().encode(jsonResponseOK.toString().getBytes(StandardCharsets.UTF_8));
					byte[] responseCrypto = CryptoImpl.symmetricEncryptDecrypt(opModeSymmetric, symmetricKey, jsonResponseOKEncoded, true);
					String responseEncodedString =  new String(Base64.getEncoder().encode(responseCrypto), StandardCharsets.UTF_8);
				//end cipher
					
					//send response
					out.println(responseEncodedString);
					
				}
				else{
					//symmetric decription
					requestDecoded = Base64.getDecoder().decode(request.getBytes(StandardCharsets.UTF_8));
					requestDecrypted = CryptoImpl.symmetricEncryptDecrypt(opModeSymmetric, symmetricKey, requestDecoded, false);						
					requestString = new String(requestDecrypted, StandardCharsets.UTF_8);
					//String requestStringDecodedString = new String(Base64.getDecoder().decode(requestString.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
					System.out.println("server request decrypted: " + requestString);
					//json {"data":"og","from":"og","to":"s","type":"login"}
					JSONObject jsonRequest = new JSONObject(requestString);
					String to	 = jsonRequest.getString("to");
					String from	 = jsonRequest.getString("from");
					String type	 = jsonRequest.getString("type");
					String data	 = jsonRequest.getString("data");

					if(type.equals(MessageType.LOGIN)){
						//edit, authentication needed
						if(mapThreads.get(from) != null){
							out.println("ChatServerThread: login failed");
							continue;
						}

						// authentication and varify digital signature
						File pubKeyFile = new File("pki/" + from + "2048.pub");
						if(pubKeyFile.exists()){
							PublicKey publicKeyClient = CryptoImpl.getPublicKey(pubKeyFile);
							JSONObject jsonUsernameAndPassword = new JSONObject(data);
							if (
									authenticate(
											jsonUsernameAndPassword.getString(MessageType.USERNAME), 
											jsonUsernameAndPassword.getString(MessageType.PASSWORD))
									
									&&
									
									CryptoImpl.verifyDigitalSignatureAgainstPlainText(
										jsonUsernameAndPassword.toString(), 
										jsonRequest.getString(MessageType.DIGSIG), 
										publicKeyClient, 
										opModeAsymmetric, 
										privateKeyPairServer, 
										opModeSymmetric, 
										symmetricKey, 
										hashFunction)){
							
								mapThreads.put(from, this);
								userOfThread = from;
								showAllClients();
								//String clients = getAllClients();
								String clientsWithPubKeys = getAllClientsWithPublicKeys();
								//System.out.println("Svi kljenti na serveru: " + clients);
								System.out.println("Svi kljenti na serveru: " + clientsWithPubKeys);
								
								sendMessage(userOfThread, MessageType.SERVER, MessageType.LOGIN, clientsWithPubKeys);
	
								notifyAllThreadsAboutUserChange();
							} else {
								System.out.println("Authentikacija ili verifikacija potpisa nuspjesna");
								out.println("Login failed");
								continue;
							}
								
						}

						
					} else if (type.equals(MessageType.CHAT)){
						if(mapThreads.get(to) != null)
							mapThreads.get(to).sendMessage(to, from, type, data);
						else
							mapThreads.get(from).sendMessage(from, to, MessageType.SERVER, "Korisnik \""+to+"\" se odjavio!");
					} else if (type.equals(MessageType.PUBLICKEY) ){
						File pubKeyFile = new File("pki/" + data + "2048.pub");
						if (pubKeyFile.exists()){
							String pubKeyString = CryptoImpl.getPublicKeyAsBase64EncodedString(pubKeyFile);
							sendMessage(from, to, MessageType.PUBLICKEY, new String(pubKeyString));
						} else
							System.out.println("Nema javnog kljuca na trazenoj putanji");
						
					} else if (type.equals(MessageType.CHATKEY)) {
						mapThreads.get(to).sendMessage(to, from, type, data);
					} else
						System.out.println("ServerThread: nepoznat type poruke");
					
					request = null;
				}
				
			} //end while

					
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("IOException: java.net.SocketException: Connection reset");
		} finally {
			try {
				mapThreads.remove(userOfThread);
				notifyAllThreadsAboutUserChange();
				System.out.println(getAllClients());
				in.close();
				out.close();
				socket.close();
				//symmetricKey = null;
				//opModeSymmetric = "";
			} catch (IOException e) {
				System.out.println("Finally catch");
				e.printStackTrace();
			}
			
		}
	}

	
	public void showAllClients(){
		//System.out.println("All clients:");
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
	
	public String getAllClientsWithPublicKeys(){
		//String clientsWithPubKeys = "";
		JSONObject jsonObj = new JSONObject();
		String pubKeyString = "";
		File fileKey = null;
		try {
			jsonObj.put("clients", getAllClients());
			
			for(Map.Entry<String, ChatServerThread> entry : mapThreads.entrySet()){
				fileKey = new File("pki/" + entry.getKey() + "2048.pub");
				if(fileKey.exists()){
					pubKeyString = CryptoImpl.getPublicKeyAsBase64EncodedString(fileKey);
					//clientsWithPubKeys += entry.getKey()+";";
					jsonObj.put(entry.getKey(), pubKeyString);
				} else
					continue;
			}
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("json all clients and public keys: " + jsonObj.toString());
		return jsonObj.toString();
	}
	
	public void sendMessage(String to, String from, String type, String data){
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("to", to);
			jsonObj.put("from", from);
			jsonObj.put("type", type);
			jsonObj.put("data", data);
			
			System.out.println("Server prije enkripcije i slanja: " + jsonObj.toString());
			byte[] cipher = CryptoImpl.symmetricEncryptDecrypt(opModeSymmetric, symmetricKey, jsonObj.toString().getBytes(StandardCharsets.UTF_8), true);
			byte[] cipherEncoded = Base64.getEncoder().encode(cipher);
			String cipherString = new String(cipherEncoded, StandardCharsets.UTF_8);
			
			out.println(cipherString);

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void notifyAllThreadsAboutUserChange(){
		//String clients = getAllClients();
		String clientsWithPubKeys = getAllClientsWithPublicKeys();
		for(Map.Entry<String, ChatServerThread> entry : mapThreads.entrySet()){
			//json {"data":"og;ir;dr;","from":"s","to":"og","type":"updateUsers"}
			
			String to = entry.getKey();
			String from = MessageType.SERVER;
			String type = MessageType.UPDATE;
			//String data = clients;
			String data = clientsWithPubKeys;
			
			
			
			entry.getValue().sendMessage(to, from, type, data);
		}
	}
	
	public boolean authenticate(String username, String password){
		boolean status = false;
		File userFile = new File("pki/" + username + "2048.pub");
		if (userFile.exists() && !getAllClients().contains(username+";")){
			status = true;
		}
		return status;
	}

}
