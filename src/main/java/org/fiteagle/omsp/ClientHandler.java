package org.fiteagle.omsp;

import info.openmultinet.ontology.vocabulary.Omn;
import info.openmultinet.ontology.vocabulary.Omn_domain_pc;
import info.openmultinet.ontology.vocabulary.Omn_lifecycle;
import info.openmultinet.ontology.vocabulary.Omn_monitoring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.Topic;

import org.fiteagle.api.core.IMessageBus;
import org.fiteagle.api.core.MessageUtil;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.vocabulary.RDF;


public class ClientHandler implements Runnable {
	
	enum STATE {BINARY_DATA, TEXT_DATA, HEADER, PROTOCOL_ERROR, BINARY_SKIP} ;
	STATE state, content ;
	Socket socket ;
	List<Model> triples = new ArrayList<Model>() ;
	Model model = ModelFactory.createDefaultModel() ; 
	
	OMSPInterface omspi ;
	
	public ClientHandler(Socket socket){
		this.socket = socket ;
		//this.state = STATE.HEADER ;
		//this.content = STATE.TEXT_DATA ;
	}

	@Override
    public void run() {
		try{
			List<String> msg = new ArrayList<String>();
			List<String> data = new ArrayList<String>();
			List<String> header = new ArrayList<String>();
			
			BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String s = null ;
			System.out.println("Printing the message received...");
			while ((s = input.readLine())!= null){
				System.out.println("Print per line: " + s) ;
				msg.add(s) ;
			}
			
			for (int i=0;i<msg.size();i++){
				if (msg.get(i).isEmpty()){
					header = msg.subList(0, i) ;
					data = msg.subList(i+1, msg.size()) ;
					break ;
				}
			}
						
			//process_header(header) ;
			if(process_text(data)){
				omspi.createInformMsg(model) ;
			}
			
			System.out.println("ClientHandler: Thread finished...");
			return ;
		}catch (IOException e) {
            System.err.println("Could not read the incoming stream.");
            System.exit(-1);
        }
	}

	private void process_header(List<String> msg){
		System.out.println("Processing header...") ;
		// later
	}

	public boolean process_text(List<String> msg){
		System.out.println("ClientHandler: Processing data...") ;
		String[] values ;
		String subject, predicate, object ;
		
		for (int i=0;i<msg.size();i++){
			values = msg.get(i).split("\\s+") ;
			subject = values[values.length-3] ;
			predicate = values[values.length-2] ;
			object = values[values.length-1] ;
			System.out.println(subject + " " + predicate + " " + object) ;
			if(!addToRDFModel(subject,predicate,object)) return false ;
		}
		
		//createInformMsg() ;
		return true ;
	}

	private void process_binary(List<String> msg){
		//later
	}
		
	private boolean addToRDFModel(String subject, String predicate, String object){
		try{
			com.hp.hpl.jena.rdf.model.Resource sub = model.createResource(subject);
			sub.addProperty(model.createProperty(predicate), object);
			
		}catch(Exception e){
			return false ;
		}
		return true ;	
	}

	public OMSPInterface getOmspI() {
		return omspi;
	}

	public void setOmspI(OMSPInterface omspi) {
		this.omspi = omspi;
	}
	






}