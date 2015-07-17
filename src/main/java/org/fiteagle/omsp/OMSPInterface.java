package org.fiteagle.omsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedThreadFactory;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.Topic;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.fiteagle.api.core.IMessageBus;
import org.fiteagle.api.core.MessageUtil;
import org.fiteagle.omsp.ClientHandler ;

import com.hp.hpl.jena.rdf.model.Model;

@Singleton
@Startup
public class OMSPInterface {
	
	@Inject
	private JMSContext context;
	@Resource(mappedName = IMessageBus.TOPIC_CORE_NAME)
	private Topic topic;
	
	ClientHandler clienthandler ;
	
	@PostConstruct
	public void initialize(){
		int portNr = 3030 ;
		try{
			ServerSocket serverSocket = new ServerSocket(portNr);
			System.out.println("Listening at port " + portNr);
			
			while(true){			
				Socket clientSocket = serverSocket.accept();
				System.out.println("Connected with a client.");

				this.clienthandler = new ClientHandler(clientSocket) ;
				clienthandler.setOmspI(this) ;
		    	ManagedThreadFactory threadFactory = (ManagedThreadFactory) new InitialContext().lookup("java:jboss/ee/concurrency/factory/default");
		    	Thread  clientHandlerThread = threadFactory.newThread(clienthandler);
      			clientHandlerThread.start();
      			
      			System.out.println("Thread started..");
				System.out.println("Listening for another incoming client...");
			}

		}catch (IOException e) {
            System.err.println("Could not listen on port " + portNr);
            System.exit(-1);
        }catch (NamingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void createInformMsg(Model model){	
		try{
			System.out.println("OMSPInterface: Creating inform message...");
			if(!model.isEmpty()){
				Message request = MessageUtil.createRDFMessage(model, IMessageBus.TYPE_INFORM, IMessageBus.TARGET_ORCHESTRATOR, IMessageBus.SERIALIZATION_TURTLE, null, context);
				context.createProducer().send(topic, request);
				System.out.println("OMSPInterface: Sending inform message... ");
			}
		}catch(Exception e){
			System.err.println("OMSPInterface: Could not create inform message.");
			e.printStackTrace();
		}
	}

	public ClientHandler getClienthandler() {
		return clienthandler;
	}

	public void setClienthandler(ClientHandler clienthandler) {
		this.clienthandler = clienthandler;
	}
	
}
