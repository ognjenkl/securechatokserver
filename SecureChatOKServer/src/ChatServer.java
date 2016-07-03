import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.invoke.LambdaConversionException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
//import java.util.concurrent.CopyOnWriteArrayList;



public class ChatServer {
	
	int serverPort = 1234;
	ServerSocket sSocket;
	Socket socket;
	boolean listening = true;
	//List<ChatServerThread> listThreads = new CopyOnWriteArrayList<ChatServerThread>();
	Map<String, ChatServerThread> mapThreads = new ConcurrentHashMap<String, ChatServerThread>();
	
	Properties prop = null;
	FileInputStream fis = null;
	String propIp = "";
	int propPort = 0;
	
	public ChatServer(){
		try {
			prop = new Properties();
			fis = new FileInputStream(new File("resources/config.properties"));
			prop.load(fis);
			propIp = prop.getProperty("ip");
			propPort = Integer.parseInt(prop.getProperty("port"));
			
			fis.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void start(){
		try {
			System.out.println("Server started at port " + serverPort);
			sSocket = new ServerSocket(propPort);
			startGui();
			while(listening){
				
				socket = sSocket.accept();
				
				ChatServerThread cst = new ChatServerThread(socket, mapThreads);
				cst.start();

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
	
	public void startGui(){
		JFrame frame = new JFrame("Server");
		JPanel panel = new JPanel();
		JLabel label = new JLabel("Server pokrenut.");
		JButton stopButton = new JButton("Stop");
		
		frame.setSize(200, 200);
		frame.setLocation(50, 50);
		frame.setResizable(false);
		
		panel.setLayout(null);
		frame.add(panel);
		
		label.setBounds(50, 50, 200, 20);
		panel.add(label);
		stopButton.setBounds(50, 80, 100, 30);
		panel.add(stopButton);
		
		stopButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				System.out.println("Server stopped.");
				frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
			}
		});
		
		
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		
		
	}

}
