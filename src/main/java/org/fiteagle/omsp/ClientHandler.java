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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.PatternSyntaxException;

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

import org.fiteagle.core.tripletStoreAccessor.TripletStoreAccessor;
import org.fiteagle.core.tripletStoreAccessor.TripletStoreAccessor.ResourceRepositoryException;

public class ClientHandler implements Runnable {
	
	private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.toString());
	
	//enum STATE {BINARY_DATA, TEXT_DATA, HEADER, PROTOCOL_ERROR, BINARY_SKIP} ;
	//STATE state, content ;
	Socket socket ;
	OMSPInterface omspi ;
	
	Model model = ModelFactory.createDefaultModel() ; 
	List<String> data, header = new ArrayList<String>();
	List<Map<String, String>> triples = new ArrayList<Map<String, String>>();
	String domain, starttime, senderid, appname ;
	
	String prefix = "http://localhost/" ;
	String omn_monitoring_genericconcepts = "http://open-multinet.info/ontology/omn-monitoring-genericconcepts#" ;
	String omn_monitoring = "http://open-multinet.info/ontology/omn-monitoring#" ;
	String rdf_type = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" ;
	
	public ClientHandler(Socket socket){
		this.socket = socket ;
		//this.state = STATE.HEADER ;
		//this.content = STATE.TEXT_DATA ;
	}

	@Override
    public void run() {
		try{				
			BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String s = null ;
			boolean EndOfHeader = false ;
			
			while ((s = input.readLine())!= null){
				if(!s.isEmpty() && !EndOfHeader && !EndOfStream(s)){
					if(!process_header(s)){
						LOGGER.log(Level.SEVERE, "Could not process stream header.");
						return ;
					}
				}else if(!s.isEmpty() && EndOfHeader && !EndOfStream(s)){
					if(!process_text(s)){
						LOGGER.log(Level.SEVERE, "Could not process stream data.");
						return ;
					}
				}else if(s.isEmpty()){
					EndOfHeader = true ;
				}else if(!s.isEmpty() && EndOfHeader && EndOfStream(s)){
					process_stream() ;
				}
			}			
			return ;
		}catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not read the incoming stream.");
            return ;
        }
	}

	private boolean process_header(String line){
		System.out.println("ClientHandler: Processing header...") ;
		System.out.println(line) ;
		try{
			if(line.contains("domain")){
				System.out.println("checking domain") ;
				domain = line.split(":")[1].replaceAll("\\s","") ;
				System.out.println("domain added") ;
			}
			else if(line.contains("start-time") || line.contains("start_time")){
				System.out.println("checking starttime") ;
				starttime = line.split(":")[1].replaceAll("\\s","") ;
				System.out.println("starttime added") ;
			}
			else if(line.contains("sender-id") || line.contains("sender_id")){
				System.out.println("checking senderid") ;
				senderid = line.split(":")[1].replaceAll("\\s","") ;
				System.out.println("senderid added") ;
			}
			else if(line.contains("app-name") || line.contains("app_name")){
				System.out.println("checking appname") ;
				appname = line.split(":")[1].replaceAll("\\s","") ;
				System.out.println("appname added") ;
			}
			else if(line.contains("schema") && line.contains("1")){
				System.out.println("checking schema") ;
				String[] list = line.split(":",1)[1].split(" ") ;
				System.out.println(list[0] + list[1] + list[2]) ;
				if(list.length != 5) return false ;			
				for (String item : list){
					if(!item.split(":")[1].matches("string")) return false ;
				}
				System.out.println("schema verified") ;
			}
		}catch(PatternSyntaxException e){
			LOGGER.log(Level.SEVERE, "Could not process stream header.");
			return false ;
		}
		
		return true ;
	}

	public boolean process_text(String line){
		System.out.println("Processing data...") ;
		String[] values ;
		
		values = line.split("\\s+") ;
		Map<String,String> triple = new HashMap<String,String>() ;
		triple.put("client_timestamp", values[0]) ;
		triple.put("subject", values[values.length-3]) ;
		triple.put("predicate", values[values.length-2]) ;
		triple.put("object", values[values.length-1]) ;
		triples.add(triple) ;
		System.out.println(triple.get("subject") + " " + triple.get("predicate") + " " + triple.get("object")) ;
		if(!addToRDFModel(triple.get("subject"),triple.get("predicate"),triple.get("object"))) return false ;
		
		return true ;
	}
	
	//TripletStoreAccessor updateModel(model);

	public boolean process_stream(){
		// look in triple store if domain exists
		// look in triple store if sender id exists
		// look in triple store if start time exists
		// look in triple store if metric exists
		
		// else if nothing exists
		//String domain_uri = prefix + UUID.randomUUID().toString() ;
		//String sender_uri = prefix + UUID.randomUUID().toString() ;
		//add_data(domain_uri, sender_uri) ;
		omspi.createInformMsg(model) ;
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
	
	private void add_data(String domain_uri, String sender_uri){
		for (int i=0;i<triples.size();i++){
			if(triples.get(i).get("predicate").matches("rdf:type") && triples.get(i).get("object").contains("Measurement")){
				// add domain
				addToRDFModel(triples.get(i).get("subject"), omn_monitoring + "sentFrom", domain_uri) ;
				// add sender
				addToRDFModel(triples.get(i).get("subject"), omn_monitoring + "sentBy", sender_uri) ;
				// add client timestamp
				addToRDFModel(triples.get(i).get("subject"), omn_monitoring + "elapsedTimeAtClientSinceExperimentStarted", triples.get(i).get("client_timestamp")) ;			
				
			}
		}
	}
	
	private void add_sender(String sender_uri, boolean sender_exists){
		for (int i=0;i<triples.size();i++){
			if(triples.get(i).get("predicate").matches("rdf:type") && triples.get(i).get("object").contains("Measurement")){
				addToRDFModel(triples.get(i).get("subject"), omn_monitoring + "sentFrom", sender_uri) ;
				if(!sender_exists) addToRDFModel(sender_uri, rdf_type, omn_monitoring_genericconcepts + "MonitoringDomain") ;
				addToRDFModel(triples.get(i).get("subject"), 
						omn_monitoring + "elapsedTimeAtClientSinceExperimentStarted", data.get(i).split("\\s+")[0]) ;			
			}
		}
	}
	
	private boolean EndOfStream(String line){
		if(line.split("\\s+").length > 3) return false ; else return true ;
	}

	public OMSPInterface getOmspI() {
		return omspi;
	}

	public void setOmspI(OMSPInterface omspi) {
		this.omspi = omspi;
	}
	

	




}