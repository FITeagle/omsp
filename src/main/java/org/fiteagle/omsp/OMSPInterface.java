package org.fiteagle.omsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import javax.enterprise.concurrent.ManagedThreadFactory;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.fiteagle.omsp.ClientHandler ;

public class OMSPInterface {

	public static void main(String[] args){
		int portNr = 3030 ;
		try{
		    ServerSocket serverSocket = new ServerSocket(portNr);
		    System.out.println("Listening at port " + portNr);

		    Socket clientSocket = serverSocket.accept();
		    System.out.println("Connected with a client.");

		    ClientHandler ch = new ClientHandler(clientSocket) ;
		    ManagedThreadFactory threadFactory = (ManagedThreadFactory) new InitialContext().lookup("java:jboss/ee/concurrency/factory/default");
		    Thread  clientHandlerThread = threadFactory.newThread(ch);
      		clientHandlerThread.start();

    		System.out.println("Finished. Listening for another incoming client...");

		}catch (IOException e) {
            System.err.println("Could not listen on port " + portNr);
            System.exit(-1);
        } catch (NamingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
