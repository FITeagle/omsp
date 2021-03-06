package org.fiteagle.omsp;

import info.openmultinet.ontology.vocabulary.Omn;
import info.openmultinet.ontology.vocabulary.Omn_domain_pc;
import info.openmultinet.ontology.vocabulary.Omn_federation;
import info.openmultinet.ontology.vocabulary.Omn_lifecycle;
import info.openmultinet.ontology.vocabulary.Omn_monitoring;
import info.openmultinet.ontology.vocabulary.Omn_monitoring_genericconcepts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
import org.fiteagle.api.tripletStoreAccessor.QueryExecuter;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Node_Variable;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.ontology.ObjectProperty;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.sparql.core.BasicPattern;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.Template;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

import org.fiteagle.core.tripletStoreAccessor.TripletStoreAccessor;
import org.fiteagle.core.tripletStoreAccessor.TripletStoreAccessor.ResourceRepositoryException;

public class ClientHandler implements Runnable {
	
	private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.toString());
	
	//enum STATE {BINARY_DATA, TEXT_DATA, HEADER, PROTOCOL_ERROR, BINARY_SKIP} ;
	//STATE state, content ;
	Socket socket ;
	OMSPInterface omspi ;
	
	Model model = ModelFactory.createDefaultModel() ; 
	List<Map<String, String>> triples = new ArrayList<Map<String, String>>();
	String domain, starttime, senderid, appname, server_ts ;
	String domain_uri, sender_uri, service_uri = null ;
	int seqNo = 0 ;
	
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
		BufferedReader input ;
		try{				
			input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String s = null ;
			boolean EndOfHeader = false ;
			//socket.setSoTimeout(35000) ;
			
			while ((s = input.readLine())!= null){
				System.out.println("Incoming stream...processing...") ;
				if(!s.isEmpty() && !EndOfHeader){
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
					if(!process_stream()){
						LOGGER.log(Level.SEVERE, "Could not process stream.");
					}
					triples.clear() ;
				}
				System.out.println("waiting for next stream") ;
			}
			input.close() ;
			socket.close() ;
			
        }catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not read the incoming stream.");
            return ;
        }finally{
    		Thread.currentThread().interrupt();
        }
		return ;
	}

	private boolean process_header(String line){
		try{
			if(line.contains("domain")){
				domain = line.split(":")[1].replaceAll("\\s","") ;
			}
			else if(line.contains("start-time") || line.contains("start_time")){
				starttime = line.split(":")[1].replaceAll("\\s","") ;
			}
			else if(line.contains("sender-id") || line.contains("sender_id")){
				senderid = line.split(":")[1].replaceAll("\\s","") ;
			}
			else if(line.contains("app-name") || line.contains("app_name")){
				appname = line.split(":")[1].replaceAll("\\s","") ;
			}
			else if(line.contains("schema") && line.contains("1")){
				String[] list = line.split(":",2)[1].split(" ") ;
				if(list.length != 6) return false ;			
				for (int i=3;i<list.length;i++){
					if(!list[i].split(":")[1].matches("string")) return false ;
				}
			}
		}catch(PatternSyntaxException e){
			return false ;
		}
		
		return true ;
	}

	public boolean process_text(String line){
		String[] values ;
		
		values = line.split("\\s+") ;
		Map<String,String> triple = new HashMap<String,String>() ;
		triple.put("client_timestamp", values[0]) ;
		triple.put("subject", values[values.length-3]) ;
		triple.put("predicate", values[values.length-2]) ;
		triple.put("object", values[values.length-1]) ;
		triples.add(triple) ;
		if(!addToRDFModel(triple.get("subject"),triple.get("predicate"),triple.get("object"))) return false ;
		
		return true ;
	}
	
	public boolean process_stream(){
		if(sender_uri != null){
			add_sender(sender_uri, false) ;
		}else{
			sender_uri = sender_exist(senderid) ;
			if(sender_uri == null) sender_uri = prefix + "sender/" + domain + "_" + senderid ;
			add_sender(sender_uri, true) ;
		}
		if(domain_uri != null){
			add_exp_domain(domain_uri, false) ;
		}else{
			domain_uri = domain_exist(domain) ;
			if(domain_uri == null) domain_uri = prefix + "domain/" + domain ;
			add_exp_domain(domain_uri, true) ;
		}

		add_timestamp() ;
		add_seqNo() ;
		LOGGER.log(Level.INFO, "Stream processed. Creating INFORM message...");
		omspi.createInformMsg(model) ;
		return true ;
	}
	
	private void process_binary(List<String> msg){
		//later
	}
	
	private void add_exp_domain(String domain_uri, boolean new_domain){
		for (int i=0;i<triples.size();i++){
			String subject = triples.get(i).get("subject") ;
			String predicate = triples.get(i).get("predicate") ;
			String object = triples.get(i).get("object") ;
			if(predicate.matches("rdf:type") && object.contains("Measurement") && !object.contains("MeasurementData")){
				addToRDFModel(subject, Omn_monitoring.sentFrom, domain_uri) ;
				add_starttime(domain_uri) ;
				if(new_domain){
					addToRDFModel(domain_uri, RDF.type, Omn_monitoring_genericconcepts.MonitoringDomain) ;
					addToRDFModel(domain_uri, RDFS.label, domain) ;
				}
			}
		}
	}
	
	private void add_sender(String sender_uri, boolean new_sender){
		for (int i=0;i<triples.size();i++){
			String subject = triples.get(i).get("subject") ;
			String predicate = triples.get(i).get("predicate") ;
			String object = triples.get(i).get("object") ;
			if(predicate.matches("rdf:type") && object.contains("Measurement") && !object.contains("MeasurementData")){
				addToRDFModel(subject, Omn_monitoring.sentFrom, sender_uri) ;
				if(new_sender){
					addToRDFModel(sender_uri, RDF.type, Omn_federation.Infrastructure) ;
					addToRDFModel(sender_uri, RDFS.label, senderid) ;
				}
			}
		}
	}
	
	private void add_timestamp(){
		for (int i=0;i<triples.size();i++){
			String subject = triples.get(i).get("subject") ;
			String predicate = triples.get(i).get("predicate") ;
			String object = triples.get(i).get("object") ;
			if(predicate.matches("rdf:type") && object.contains("Measurement") && !object.contains("MeasurementData")){
				addToRDFModel(subject, 
						omn_monitoring + "elapsedTimeAtClientSinceExperimentStartedInSeconds", triples.get(i).get("client_timestamp")) ;		
				double now_in_ms = System.currentTimeMillis() ;
				double server_timestamp = now_in_ms/1000.0 - Long.parseLong(starttime) ;
				server_ts = String.valueOf(server_timestamp) ;
				addToRDFModel(subject, 
						omn_monitoring + "elapsedTimeAtServerSinceExperimentStartedInSeconds", server_ts) ;		
			}
		}
	}
	
	private void add_starttime(String domain_uri){
		if(service_uri == null){
			service_uri = service_exist(domain_uri, sender_uri) ;
			if(service_uri == null) service_uri = prefix + "monitoring_service/" + appname + "_" + domain + "_" + senderid ;
			addToRDFModel(domain_uri, Omn.hasService, service_uri) ;
			addToRDFModel(service_uri,RDF.type, Omn_monitoring.MonitoringService) ;
			addToRDFModel(service_uri,Omn.isServiceOf, sender_uri) ;
			addToRDFModel(service_uri, Omn_lifecycle.startTime, starttime) ;
		}	
	}
	
	private void add_seqNo(){
		for (int i=0;i<triples.size();i++){
			String subject = triples.get(i).get("subject") ;
			String predicate = triples.get(i).get("predicate") ;
			String object = triples.get(i).get("object") ;
			if(predicate.matches("rdf:type") && object.contains("Measurement") && !object.contains("MeasurementData")){
				addToRDFModel(subject, Omn.sequenceNumber, Integer.toString(seqNo)) ;	
				seqNo++ ;
			}
		}
	}
	
	private boolean EndOfStream(String line){
		if(line.split("\\s+").length > 3) return false ; else return true ;
	}
	
	/*private void query_database(String label_sender, String label_domain){
		String existingValue = "?measurement <http://open-multinet.info/ontology/omn-monitoring#sentFrom> ?sender . " + 
				"?sender <http://www.w3.org/2000/01/rdf-schema#label> ?label_sender . " + 
				"?sender <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://open-multinet.info/ontology/omn-federation#Infrastructure> . " +
				"?measurement <http://open-multinet.info/ontology/omn-monitoring#sentFrom> ?domain . " +
				"?domain <http://www.w3.org/2000/01/rdf-schema#label> ?label_domain . " + 
				"?domain <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://open-multinet.info/ontology/omn-monitoring-genericconcepts#MonitoringDomain> . " +
				"?domain <http://open-multinet.info/ontology/omn#hasService> ?service . " +
				"?service <http://open-multinet.info/ontology/omn-lifecycle#StartTime> ?value . " ;
		
		String filter = "filter(regex(?label_sender,\"" + label_sender + "\")) . " + 
				"filter(regex(?label_domain,\"" + label_domain + "\")) . "	;
		
		String queryString = "SELECT ?sender ?domain ?service ?value " + "WHERE { "+existingValue+ " " + filter + " }";
		try {
			ResultSet rs = QueryExecuter.executeSparqlSelectQuery(queryString);
			if(rs.hasNext()){
				QuerySolution row = rs.next();
				domain_uri = row.get("domain").toString() ;		
				sender_uri = row.get("sender").toString() ;
				service_uri = row.get("service").toString() ;
				starttime = row.getLiteral("value").getString() ;
				System.out.println("after querying..") ;
				System.out.println(domain_uri + sender_uri + service_uri + starttime) ;
			}
			
		} catch (org.fiteagle.api.tripletStoreAccessor.TripletStoreAccessor.ResourceRepositoryException e) {
			LOGGER.log(Level.SEVERE, "Could not query triple store.");
		}
	}*/
		
	
	private String domain_exist(String label){
		String existingValue = "?domain <http://www.w3.org/2000/01/rdf-schema#label> ?label . " +
				"?domain <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://open-multinet.info/ontology/omn-monitoring-genericconcepts#MonitoringDomain> . ";
		String filter = "filter(regex(?label,\"" + label + "\")) ." ;
		
	    String queryString = "SELECT ?domain " + "WHERE { "+existingValue+ " " + filter + " }";
		try {
			ResultSet rs = QueryExecuter.executeSparqlSelectQuery(queryString);
			if(rs.hasNext()){
				QuerySolution row = rs.next();
				RDFNode value = row.get("domain");	
				return value.toString() ;
			}else return null ; 
			
		} catch (org.fiteagle.api.tripletStoreAccessor.TripletStoreAccessor.ResourceRepositoryException e) {
			LOGGER.log(Level.SEVERE, "Could not query triple store.");
		}
		
		return null ;
	}
	
	private String sender_exist(String label){
		String existingValue = "?sender <http://www.w3.org/2000/01/rdf-schema#label> ?label ." +
				"?sender <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://open-multinet.info/ontology/omn-federation#Infrastructure> . ";
		String filter = "filter(regex(?label,\"" + label + "\")) ." ;
		
	    String queryString = "SELECT ?sender " + "WHERE { "+existingValue+ " " + filter + " }";
		try {
			ResultSet rs = QueryExecuter.executeSparqlSelectQuery(queryString);
			if(rs.hasNext()){
				QuerySolution row = rs.next();
				RDFNode value = row.get("sender");	
				return value.toString() ;
			}else return null ; 
			
		} catch (org.fiteagle.api.tripletStoreAccessor.TripletStoreAccessor.ResourceRepositoryException e) {
			LOGGER.log(Level.SEVERE, "Could not query triple store.");
		}
		return null ;
	}
		
	/*private String starttime_exist(String sender){
		String existingValue = "?measurement <http://open-multinet.info/ontology/omn-monitoring#sentBy> ?sender . " + 
					"?sender <http://www.w3.org/2000/01/rdf-schema#label> ?label ." + 
					"?measurement <http://open-multinet.info/ontology/omn-monitoring#sentFrom> ?domain . " +
					"?domain <http://open-multinet.info/ontology/omn#hasService> ?service . " +
					"?service <http://open-multinet.info/ontology/omn-lifecycle#StartTime> ?value . " ;
		String filter = "filter(regex(?label,\"" + label + "\")) ." ;
		
	    String queryString = "SELECT ?sender " + "WHERE { "+existingValue+ " " + filter + " }";
		try {
			ResultSet rs = QueryExecuter.executeSparqlSelectQuery(queryString);
			if(rs.hasNext()){
				QuerySolution row = rs.next();
				RDFNode value = row.get("sender");		
				return value.toString() ;
			}else return null ; 
			
		} catch (org.fiteagle.api.tripletStoreAccessor.TripletStoreAccessor.ResourceRepositoryException e) {
			LOGGER.log(Level.SEVERE, "Could not query triple store.");
		}
		return null ;
	}*/
	
	private String service_exist(String domain_uri,String sender_uri){
		String existingValue = "<" +domain_uri+ "> <http://open-multinet.info/ontology/omn#hasService> ?service . " +
				"?service <http://open-multinet.info/ontology/omn#isServiceOf> <" + sender_uri + "> . " ;
		
	    String queryString = "SELECT ?service " + "WHERE { "+existingValue+ " }";
		try {
			ResultSet rs = QueryExecuter.executeSparqlSelectQuery(queryString);
			if(rs.hasNext()){
				QuerySolution row = rs.next();
				RDFNode value = row.get("service");		
				return value.toString() ;
			}else return null ; 
			
		} catch (org.fiteagle.api.tripletStoreAccessor.TripletStoreAccessor.ResourceRepositoryException e) {
			LOGGER.log(Level.SEVERE, "Could not query triple store.");
		}
		return null ;
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

	private boolean addToRDFModel(String subject, ObjectProperty predicate, String object){
		try{
			com.hp.hpl.jena.rdf.model.Resource sub = model.createResource(subject);
			sub.addProperty(predicate, object);
			
		}catch(Exception e){
			return false ;
		}
		return true ;	
	}
	
	private boolean addToRDFModel(String subject, Property predicate, OntClass object){
		try{
			com.hp.hpl.jena.rdf.model.Resource sub = model.createResource(subject);
			sub.addProperty(predicate, object);
			
		}catch(Exception e){
			return false ;
		}
		return true ;	
	}
	
	private boolean addToRDFModel(String subject, Property predicate, String object){
		try{
			com.hp.hpl.jena.rdf.model.Resource sub = model.createResource(subject);
			sub.addProperty(predicate, object);
			
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