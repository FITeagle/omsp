package org.fiteagle.omsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;


public class ClientHandler implements Runnable {
	
	enum STATE {BINARY_DATA, TEXT_DATA, HEADER} ;
	STATE state ;
	Socket socket ;
	
	public ClientHandler(Socket socket){
		this.socket = socket ;
	}
	
	@Override
    public void run() {
		try{
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String s = null ;
			System.out.println("Printing the message received...");
			while ((s = in.readLine())!= null){
				System.out.println(s);
			}
		}catch (IOException e) {
            System.err.println("Could not read the incoming stream.");
            System.exit(-1);
        }
	}
}