package org.fiteagle.omsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

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
	
	private static final Logger LOGGER = Logger.getLogger(OMSPInterface.class.toString());
	ClientHandler clienthandler ;
	
	@PostConstruct
	public void initialize(){
		int portNr = 3030 ;
		try{
			ServerSocket serverSocket = new ServerSocket(portNr);
			LOGGER.log(Level.INFO, "Listening at port " + portNr);
			
			while(true){			
				Socket clientSocket = serverSocket.accept();
				LOGGER.log(Level.INFO, "Connected with a client.");

				this.clienthandler = new ClientHandler(clientSocket) ;
				clienthandler.setOmspI(this) ;
		    	ManagedThreadFactory threadFactory = (ManagedThreadFactory) new InitialContext().lookup("java:jboss/ee/concurrency/factory/default");
		    	Thread  clientHandlerThread = threadFactory.newThread(clienthandler);
      			clientHandlerThread.start();
      			
      			LOGGER.log(Level.INFO, "Thread started..");
			}

		}catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Could not listen on port " + portNr);
            System.exit(-1);
        }catch (NamingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void createInformMsg(Model model){	
		try{
			if(!model.isEmpty()){
				final Message request = MessageUtil.createRDFMessage(model, IMessageBus.TYPE_INFORM, IMessageBus.TARGET_ORCHESTRATOR, IMessageBus.SERIALIZATION_TURTLE, null, context);
				context.createProducer().send(topic, request);
				LOGGER.log(Level.INFO, "INFORM message has been sent.");
			}
		}catch(Exception e){
			LOGGER.log(Level.SEVERE, "Could not create INFORM message.");
		}
	}

	public ClientHandler getClienthandler() {
		return clienthandler;
	}

	public void setClienthandler(ClientHandler clienthandler) {
		this.clienthandler = clienthandler;
	}
	
}
